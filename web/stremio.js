/** VistaStream Web · Stremio protocol integration: HTTPS/CORS only. */
const MAX_ADDONS=18, MAX_STREAMS=40, MAX_CATALOG_ITEMS=60;
export function normalizeManifest(input){
  const u=new URL(String(input||"").trim());
  if(u.protocol!=="https:"||u.username||u.password||u.href.length>2048)throw Error("Utiliza uma URL HTTPS de manifest.json.");
  if(!/\/manifest\.json$/i.test(u.pathname))throw Error("O endereço tem de terminar em /manifest.json.");
  if(["localhost","127.0.0.1","0.0.0.0","::1"].includes(u.hostname)||/^(10|127|192\.168)\./.test(u.hostname))throw Error("Este site não consegue aceder a addons locais.");
  u.hash="";return u.href;
}
async function fetchAddon(url){
  const controller=new AbortController(),t=setTimeout(()=>controller.abort(),10000);
  try{
    const response=await fetch(url,{mode:"cors",credentials:"omit",referrerPolicy:"no-referrer",signal:controller.signal,headers:{"Accept":"application/json"}});
    if(!response.ok)throw Error("HTTP "+response.status);
    const declared=Number(response.headers.get("content-length"))||0;
    if(declared>1024*1024)throw Error("Resposta demasiado grande");
    const text=await response.text();
    if(text.length>1024*1024)throw Error("Resposta demasiado grande");
    return JSON.parse(text);
  }catch(e){if(e instanceof SyntaxError)throw Error("Resposta não contém JSON válido");throw e;}
  finally{clearTimeout(t);}
}
function supports(manifest,resource,type,id){
  return (manifest.resources||[]).some(r=>{
    const o=typeof r==="string"?{name:r}:r;
    if(!o||o.name!==resource)return false;
    if(o.types&&!o.types.includes(type))return false;
    if(o.idPrefixes?.length && !o.idPrefixes.some(prefix=>String(id).startsWith(prefix)))return false;
    return true;
  });
}
function resourceUrl(addon,kind,type,id){
  const root=addon.url.slice(0,addon.url.lastIndexOf("manifest.json"));
  const target=new URL([kind,encodeURIComponent(type),encodeURIComponent(id)].join("/")+".json",root);
  if(target.origin!==new URL(addon.url).origin)throw Error("Destino inesperado do addon");
  return target.href;
}
const cleaned=s=>String(s||"").slice(0,200);
function metaItem(m,type,addon){
  if(!m||typeof m!=="object"||!m.name||!m.id)return null;
  let u="",bg="";
  try{const x=new URL(String(m.poster||""));if(x.protocol==="https:")u=x.href;}catch{}
  try{const x=new URL(String(m.background||""));if(x.protocol==="https:")bg=x.href;}catch{}
  return {
    id:"stremio:"+addon.id+":"+m.id,ref:String(m.id),type:type==="series"?"series":type==="anime"?"anime":"movie",
    name:cleaned(m.name),year:Number(String(m.releaseInfo||m.year||"").match(/\d{4}/)?.[0])||0,
    poster:u,background:bg||u,description:cleaned(m.description||""),
    rating:Number(m.imdbRating)||0,genres:Array.isArray(m.genres)?m.genres.slice(0,8):[],
    provider:addon.name,addonId:addon.id
  };
}
function httpsPlayable(raw){
  try{
    const u=new URL(raw);
    return u.protocol==="https:"&&!u.username&&!u.password&&u.href.length<4096 &&
      /\.(mp4|m4v|mov|m3u8|webm)(?:$)/i.test(u.pathname);
  }catch{return false;}
}
function streamLabel(s,addon){
  return [cleaned(s.name)||addon.name,cleaned(s.title)].filter(Boolean).join(" · ").slice(0,190);
}
export class StremioHub{
  constructor(getStore,persist){this.getStore=getStore;this.persist=persist;}
  list(){
    const items=this.getStore().addons;
    return Array.isArray(items)?items.filter(x=>x&&typeof x==="object"&&x.url):[];
  }
  async install(raw){
    const url=normalizeManifest(raw);
    if(this.list().some(a=>a.url===url))throw Error("Esta extensão já está instalada.");
    if(this.list().length>=MAX_ADDONS)throw Error("Limite de extensões atingido.");
    const m=await fetchAddon(url);
    if(typeof m.id!=="string"||!m.id||typeof m.name!=="string"||!Array.isArray(m.resources)||!Array.isArray(m.catalogs)||!Array.isArray(m.types))
      throw Error("Manifesto Stremio incompleto.");
    if(m.resources.length>60||m.catalogs.length>100)throw Error("Manifesto demasiado grande.");
    const item={
      id:cleaned(m.id),name:cleaned(m.name),description:cleaned(m.description||"Extensão Stremio"),url,
      enabled:true,version:cleaned(m.version||""),resources:m.resources.slice(0,60),
      catalogs:m.catalogs.slice(0,100),types:m.types.slice(0,20),addedAt:Date.now()
    };
    const store=this.getStore();store.addons=[...this.list(),item];
    this.persist();return item;
  }
  remove(id){this.getStore().addons=this.list().filter(x=>x.id!==id);this.persist();}
  toggle(id){const a=this.list().find(x=>x.id===id);if(!a)return;a.enabled=!a.enabled;this.persist();}
  async catalogRows(){
    const jobs=[];
    for(const addon of this.list().filter(a=>a.enabled)){
      for(const c of addon.catalogs.slice(0,3)){
        if(!c?.id||!["movie","series","anime"].includes(c.type))continue;
        if(!supports(addon,"catalog",c.type,c.id))continue;
        const mustSearch=(c.extra||[]).some(x=>x.name==="search"&&x.isRequired);
        if(mustSearch)continue;
        const url=resourceUrl(addon,"catalog",c.type,c.id);
        jobs.push(fetchAddon(url).then(j=>({addon,label:cleaned(c.name||addon.name),
          items:(j.metas||[]).slice(0,MAX_CATALOG_ITEMS).map(x=>metaItem(x,c.type,addon)).filter(Boolean)}))
          .catch(e=>({addon,label:cleaned(c.name||addon.name),items:[],error:e.message})));
      }
    }
    return Promise.all(jobs.slice(0,20));
  }
  async streams(item,season=1,episode=1,alternateRef=""){
    const ref=String(alternateRef||item.ref||"");
    const type=item.type==="movie"?"movie":"series";
    if(!ref)return [];
    const id=type==="movie"?ref:ref+":"+season+":"+episode;
    const eligible=this.list().filter(a=>a.enabled&&(supports(a,"stream",type,id)||supports(a,"stream",type,ref)));
    const batches=await Promise.all(eligible.map(async addon=>{
      try{
        const data=await fetchAddon(resourceUrl(addon,"stream",type,id));
        return (Array.isArray(data.streams)?data.streams:[]).slice(0,MAX_STREAMS).map(s=>{
          if(!s||typeof s!=="object")return null;
          const link=String(s.url||"");
          return {addon:addon.name,addonId:addon.id,title:streamLabel(s,addon),url:httpsPlayable(link)?link:"",
            playable:httpsPlayable(link),
            reason:s.infoHash?"Torrent não suportado diretamente no Safari":
              link.startsWith("http:")?"Fonte HTTP não segura/incompatível com HTTPS":
              link?"Formato ou endereço indisponível no Safari":"Fonte sem vídeo HTTPS direto",
            external:s.externalUrl&&/^https:\/\//.test(s.externalUrl)?String(s.externalUrl):""};
        }).filter(Boolean);
      }catch(e){return [{addon:addon.name,title:addon.name,url:"",playable:false,
        reason:/fetch|network|cors|failed/i.test(e.message)?"A extensão não respondeu (rede ou CORS)":e.message}];}
    }));
    const flat=batches.flat();
    return [...new Map(flat.map(x=>[x.url||x.title+":"+x.reason,x])).values()].slice(0,75);
  }
}
