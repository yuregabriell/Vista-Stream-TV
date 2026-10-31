import {readFileSync} from "node:fs";
import assert from "node:assert/strict";
const body=readFileSync(new URL("./stremio.js",import.meta.url),"utf8");
const {StremioHub,normalizeManifest}=await import("data:text/javascript,"+encodeURIComponent(body));
assert.equal(normalizeManifest("https://example.org/manifest.json"),"https://example.org/manifest.json");
for(const bad of ["http://example.org/manifest.json","https://localhost/manifest.json","https://example.org/not-a-manifest","https://name:pass@example.org/manifest.json"]){
  assert.throws(()=>normalizeManifest(bad));
}
const requests=[];
globalThis.fetch=async (url)=>{
  requests.push(url);
  let obj;
  if(url==="https://example.org/manifest.json")obj={
    id:"org.example.authorized",name:"Demo autorizado",version:"1.2.3",description:"Conteúdo de demonstração",
    types:["movie","series"],resources:["catalog","stream","meta"],
    catalogs:[{type:"movie",id:"demo",name:"Filmes de demonstração"}]
  };
  else if(url==="https://example.org/catalog/movie/demo.json")obj={
    metas:[{id:"tt123",name:"Filme autorizado",type:"movie",poster:"https://example.org/poster.jpg"}]
  };
  else if(url==="https://example.org/stream/movie/tt123.json")obj={
    streams:[{name:"HLS",url:"https://example.org/video.m3u8"},{name:"BitTorrent",infoHash:"deadbeef"}]
  };
  else if(url==="https://example.org/stream/series/tt123%3A2%3A3.json")obj={
    streams:[{name:"Episódio 3",url:"https://example.org/ep3.mp4"}]
  };
  else return {ok:false,status:404,headers:new Map(),text:async()=>""};
  return {ok:true,status:200,headers:new Map(),text:async()=>JSON.stringify(obj)};
};
let persist=0;
const store={addons:[]};
const hub=new StremioHub(()=>store,()=>{persist++;});
const addon=await hub.install("https://example.org/manifest.json");
assert.equal(addon.name,"Demo autorizado");
assert.equal(store.addons.length,1);
const catalogs=await hub.catalogRows();
assert.equal(catalogs.length,1);
assert.equal(catalogs[0].items[0].name,"Filme autorizado");
const movie=await hub.streams({type:"movie",ref:"tt123"},1,1);
assert.equal(movie.length,2);
assert.equal(movie[0].playable,true);
assert.equal(movie[1].playable,false);
const episode=await hub.streams({type:"series",ref:"tt123"},2,3);
assert.equal(episode[0].playable,true);
assert(requests.some(x=>x.endsWith("series/tt123%3A2%3A3.json")));
hub.toggle(addon.id);
assert.equal(hub.list()[0].enabled,false);
hub.remove(addon.id);
assert.equal(hub.list().length,0);
assert(persist>=3);
console.log("Stremio protocol tests OK: install/catalog/movie/episode/reject unsupported streams");
