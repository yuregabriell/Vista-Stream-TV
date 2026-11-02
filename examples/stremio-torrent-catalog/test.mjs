import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const script = new URL("./build.mjs", import.meta.url).pathname;
const hash = "0123456789abcdef0123456789abcdef01234567";
test("builds a valid static addon and preserves per-episode torrent indexes", async () => {
  const temp = await mkdtemp(join(tmpdir(), "vs-stremio-"));
  try {
    const media = [{
      id: "tt1234567", type: "series", name: "Teste PT-BR",
      rights: "Authorized fixture", permissionConfirmed: true,
      episodes: [{ season: 1, episode: 1, streams: [
        { infoHash: hash, fileIdx: 0, name: "PT-BR 1080p", language: "PT-BR" }
      ] }]
    }];
    const input = join(temp, "input.json");
    const dest = join(temp, "dist");
    await writeFile(input, JSON.stringify(media));
    const run = spawnSync(process.execPath, [script, input, dest], { encoding: "utf8" });
    assert.equal(run.status, 0, run.stderr);
    const manifest = JSON.parse(await readFile(join(dest, "manifest.json"), "utf8"));
    assert.ok(manifest.resources.includes("stream"));
    assert.ok(manifest.idPrefixes.includes("tt"));
    const catalog = JSON.parse(await readFile(join(dest, "catalog/series/vista-authorized-pt.json"), "utf8"));
    assert.equal(catalog.metas[0].id, "tt1234567");
    const stream = JSON.parse(await readFile(join(dest, "stream/series/tt1234567:1:1.json"), "utf8"));
    assert.equal(stream.streams[0].infoHash, hash);
    assert.equal(stream.streams[0].fileIdx, 0);
    assert.equal(stream.streams[0].title, "PT-BR");
  } finally { await rm(temp, { recursive: true, force: true }); }
});

test("refuses entries without rights confirmation", async () => {
  const temp = await mkdtemp(join(tmpdir(), "vs-stremio-"));
  try {
    const input = join(temp, "input.json");
    await writeFile(input, JSON.stringify([{ id: "tt1234567", type: "movie", name: "Teste", streams: [] }]));
    const run = spawnSync(process.execPath, [script, input, join(temp, "dist")], { encoding: "utf8" });
    assert.notEqual(run.status, 0);
    assert.match(run.stderr, /rights description/);
  } finally { await rm(temp, { recursive: true, force: true }); }
});
