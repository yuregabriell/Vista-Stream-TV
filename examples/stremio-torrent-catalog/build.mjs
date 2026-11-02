#!/usr/bin/env node
/**
 * Creates a STATIC Stremio addon from an authorized media list.
 * No website scraping, torrent downloading or executable plugins.
 * Usage: node build.mjs input.json ./dist
 * Host dist/ at https://.../ and add its /manifest.json to VistaStream.
 */
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  console.error("Usage: node build.mjs <authorized-media.json> <output-directory>");
  process.exit(2);
}
const items = JSON.parse(await readFile(input, "utf8"));
if (!Array.isArray(items)) throw new Error("Input must be a JSON array");
const idPattern = /^(?:tt\d{5,}|tmdb:\d+)$/;
const videoUrl = /^https?:\/\//i;
const hashPattern = /^[0-9a-f]{40}$/i;
const addonId = "org.vistastream.authorized.catalog";
const catalogId = "vista-authorized-pt";
const streamsFor = (sources, title) => {
  if (!Array.isArray(sources)) throw new Error("streams must be an array: " + title);
  return sources.map((s) => {
    if (!s || typeof s !== "object") throw new Error("Invalid stream for " + title);
    const hasUrl = typeof s.url === "string" && videoUrl.test(s.url);
    const hasHash = typeof s.infoHash === "string" && hashPattern.test(s.infoHash);
    if (Number(hasUrl) + Number(hasHash) !== 1) throw new Error("Specify ONE http(s) url or 40-hex infoHash for " + title);
    const result = { name: String(s.name || "Fonte autorizada").slice(0, 100) };
    if (hasUrl) result.url = s.url;
    if (hasHash) result.infoHash = s.infoHash.toLowerCase();
    if (s.fileIdx !== undefined) {
      if (!Number.isInteger(s.fileIdx) || s.fileIdx < 0) throw new Error("fileIdx must be zero-based >= 0");
      if (!hasHash) throw new Error("fileIdx requires infoHash");
      result.fileIdx = s.fileIdx;
    }
    if (s.language) result.title = String(s.language).slice(0, 60);
    if (Array.isArray(s.sources) && hasHash) {
      result.sources = s.sources.filter(x => typeof x === "string" && /^(tracker:(?:https?|udp):\/\/|dht:)/i.test(x)).slice(0, 30);
    }
    return result;
  });
};
const out = new Map();
const catalogs = { movie: [], series: [] };
const ids = new Set();
async function save(relative, value) {
  out.set(relative, JSON.stringify(value, null, 2) + "\n");
}
for (const item of items) {
  if (!item || !idPattern.test(item.id || "") || !["movie", "series"].includes(item.type)) {
    throw new Error("Each entry requires IMDb 'tt...' or TMDB 'tmdb:123' id and type movie/series");
  }
  if (ids.has(item.type + ":" + item.id)) throw new Error("Duplicate ID " + item.id);
  ids.add(item.type + ":" + item.id);
  if (!item.rights || !item.permissionConfirmed) {
    throw new Error("Record needs rights description and permissionConfirmed=true: " + item.id);
  }
  const name = String(item.name || "").trim();
  if (!name || name.length > 160) throw new Error("Missing/invalid name for " + item.id);
  const preview = { id: item.id, type: item.type, name };
  if (item.poster && videoUrl.test(item.poster)) preview.poster = item.poster;
  if (item.description) preview.description = String(item.description).slice(0, 1000);
  catalogs[item.type].push(preview);
  const meta = { ...preview };
  if (item.type === "movie") {
    await save("stream/movie/" + item.id + ".json", { streams: streamsFor(item.streams || [], name) });
  } else {
    if (!Array.isArray(item.episodes) || !item.episodes.length) {
      throw new Error("Series must contain episodes: " + item.id);
    }
    const episodeIds = new Set();
    meta.videos = [];
    for (const ep of item.episodes) {
      if (!Number.isInteger(ep.season) || ep.season <= 0 || !Number.isInteger(ep.episode) || ep.episode <= 0) {
        throw new Error("Invalid S/E for " + item.id);
      }
      const episodeId = item.id + ":" + ep.season + ":" + ep.episode;
      if (episodeIds.has(episodeId)) throw new Error("Duplicate episode " + episodeId);
      episodeIds.add(episodeId);
      meta.videos.push({ id: episodeId, title: String(ep.name || "Episódio " + ep.episode), season: ep.season, episode: ep.episode });
      await save("stream/series/" + episodeId + ".json", { streams: streamsFor(ep.streams || [], episodeId) });
    }
  }
  await save("meta/" + item.type + "/" + item.id + ".json", { meta });
}
for (const type of ["movie", "series"]) {
  await save("catalog/" + type + "/" + catalogId + ".json", { metas: catalogs[type] });
}
await save("manifest.json", {
  id: addonId, version: "1.0.0", name: "Vista · Catálogo PT autorizado",
  description: "Catálogo de fontes com distribuição autorizada, preparado para o VistaStream.",
  resources: ["catalog", "meta", "stream"],
  types: ["movie", "series"],
  idPrefixes: ["tt", "tmdb:"],
  catalogs: ["movie", "series"].filter(t => catalogs[t].length).map(type => ({ type, id: catalogId, name: "Catálogo PT autorizado" }))
});
for (const [relative, payload] of out) {
  const dest = join(output, relative);
  await mkdir(join(dest, ".."), { recursive: true });
  await writeFile(dest, payload, "utf8");
}
console.log("Generated " + out.size + " JSON files for " + items.length + " titles in " + output);
