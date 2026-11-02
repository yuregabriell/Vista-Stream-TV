/* VistaStream Live TV: remote public channel metadata; never caches stream bytes. */
const COUNTRIES=[
  ["PT","🇵🇹","Portugal"],["BR","🇧🇷","Brasil"],["US","🇺🇸","Estados Unidos"],
  ["GB","🇬🇧","Reino Unido"],["ES","🇪🇸","Espanha"],["FR","🇫🇷","França"],
  ["DE","🇩🇪","Alemanha"],["IT","🇮🇹","Itália"],["JP","🇯🇵","Japão"],
  ["CA","🇨🇦","Canadá"],["MX","🇲🇽","México"],["AR","🇦🇷","Argentina"],
  ["AO","🇦🇴","Angola"],["MZ","🇲🇿","Moçambique"]
];
const countryKey="vista-live-country-v1",favoriteKey="vista-live-favorites-v1";
const RICOS="https://zoreu.github.io/ricosstremio/manifest.json";
let installedLiveAddons=[],openSources=null;
function safeGet(k){try{return localStorage.getItem(k);}catch{return null;}}
function safeSet(k,v){try{localStorage.setItem(k,v);}catch{}}
const detected=(navigator.language||"pt-PT").split("-").at(-1).toUpperCase();
const state={
  country:COUNTRIES.find(c=>c[0]===safeGet(countryKey))?.[0]||
    COUNTRIES.find(c=>c[0]===detected)?.[0]||"PT",
  channels:[],countryLoaded:"",loading:false,error:"",filter:"Todos",search:"",
  favorites:(()=>{try{return JSON.parse(safeGet(favoriteKey)||"{}");}catch{return {};}})()
};
function node(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=String(text);return e;}
function button(label,cls,fn){const b=node("button",cls,label);b.type="button";b.addEventListener("click",fn);return b;}
function attr(line,name){return line.match(new RegExp(name+'="([^"]*)"'))?.[1]?.trim()||"";}
function parseM3U(text,code){
  const out=[],seen=new Set();let meta="";
  for(const raw of text.split(/\r?\n/).slice(0,25000)){
    const line=raw.trim();
    if(line.startsWith("#EXTINF:")){meta=line;continue;}
    if(!meta||!line||line.startsWith("#"))continue;
    let valid=false;
    try{const u=new URL(line);valid=u.protocol==="https:"&&u.href.length<4096&&
      !["localhost","127.0.0.1"].includes(u.hostname)&&
      !/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(u.hostname);}catch{}
    if(valid){
      const name=(meta.split(",").at(-1)||attr(meta,"tvg-name")).trim().slice(0,120);
      const group=(attr(meta,"group-title")||"Geral").slice(0,50);
      const logo=attr(meta,"tvg-logo");
      const id=code+"|"+name.toLocaleLowerCase();
      if(name&&!seen.has(id)){seen.add(id);out.push({id,name,group,logo:logo.startsWith("https://")?logo:"",url:line});}
    }
    meta="";
    if(out.length>=1500)break;
  }
  return out;
}

function safeManifest(s){
  try{
    const url=new URL(String(s));const host=url.hostname.toLowerCase();
    return url.protocol==="https:"&&url.username===""&&url.password===""&&
      url.href.length<2048&&url.pathname.endsWith("/manifest.json")&&
      !["localhost","127.0.0.1"].includes(host)&&
      !/^(10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host);
  }catch{return false;}
}
function resource(manifest,kind,type,id){
  const u=new URL(manifest);
  const base=u.href.slice(0,u.href.lastIndexOf("manifest.json"));
  return base+kind+"/"+encodeURIComponent(type)+"/"+encodeURIComponent(id)+".json";
}
async function addonJson(url){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),10500);
  try{
    const r=await fetch(url,{mode:"cors",credentials:"omit",signal:controller.signal,headers:{Accept:"application/json"}});
    if(!r.ok)throw Error("HTTP "+r.status);
    const text=await r.text();if(text.length>1500000)throw Error("Resposta extensa");
    return JSON.parse(text);
  }finally{clearTimeout(timer);}
}
async function loadStremioChannels(code){
  const sources=[];
  if(code==="BR")sources.push({url:RICOS,name:"Canal Ricos"});
  for(const x of installedLiveAddons.filter(x=>x?.enabled&&x.types?.includes("tv")&&safeManifest(x.url))){
    if(!sources.some(v=>v.url===x.url))sources.push({url:x.url,name:x.name||"Stremio TV"});
  }
  const out=[];
  for(const addon of sources.slice(0,8)){
    try{
      const manifest=await addonJson(addon.url),catalogs=Array.isArray(manifest.catalogs)?manifest.catalogs:[];
      const aliases=code==="BR"?["br","brazil","brasil"]:code==="PT"?["pt","portugal"]:[code.toLowerCase()];
      for(const catalog of catalogs.slice(0,70)){
        if(!["tv","channel"].includes(catalog.type)||!catalog.id)continue;
        const match=addon.url===RICOS&&code==="BR"||
          aliases.some(a=>a===String(catalog.id).toLowerCase()||a===String(catalog.name||"").toLowerCase().replace(/ tv$/,""));
        if(!match)continue;
        const data=await addonJson(resource(addon.url,"catalog",catalog.type,catalog.id));
        for(const entry of (data.metas||[]).slice(0,800)){
          if(!entry?.id||!entry?.name)continue;
          out.push({id:code+"|stremio|"+addon.url+"|"+entry.id,
            name:String(entry.name).slice(0,120),group:String(entry.genres?.[0]||"TV Brasileira").slice(0,60),
            logo:String(entry.logo||entry.poster||"").startsWith("https://")?String(entry.logo||entry.poster):"",
            url:"",source:addon.name,addon:{manifest:addon.url,type:catalog.type,ref:String(entry.id)}});
        }
      }
    }catch(e){console.warn("Live TV addon:",addon.name,e.message);}
  }
  return out;
}
async function playChannel(channel,country,play){
  if(!channel.addon){play(channel,country);return;}
  try{
    const data=await addonJson(resource(channel.addon.manifest,"stream",channel.addon.type,channel.addon.ref));
    const video=(data.streams||[]).find(x=>{
      try{const u=new URL(x.url);return u.protocol==="https:"&&!u.username&&!u.password&&u.href.length<4096;}catch{return false;}
    });
    if(!video)throw Error("O addon não devolveu uma transmissão HTTPS direta.");
    play({...channel,url:video.url},country);
  }catch(e){alert("Transmissão indisponível: "+e.message);}
}

async function load(code){
  state.loading=true;state.error="";state.channels=[];state.countryLoaded="";state.filter="Todos";
  let publicChannels=[],error=null;
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),15000);
  try{
    const response=await fetch("https://iptv-org.github.io/iptv/countries/"+code.toLowerCase()+".m3u",
      {mode:"cors",credentials:"omit",signal:controller.signal});
    if(!response.ok)throw Error("HTTP "+response.status);
    const declared=Number(response.headers.get("content-length"))||0;
    if(declared>5_000_000)throw Error("Lista demasiado grande");
    const txt=await response.text();
    if(txt.length>5_000_000)throw Error("Lista demasiado grande");
    publicChannels=parseM3U(txt,code);
  }catch{error="Não foi possível atualizar a lista pública.";}
  finally{clearTimeout(timer);}
  const extra=await loadStremioChannels(code);
  if(code!==state.country)return;
  state.channels=[...new Map([...publicChannels,...extra].map(x=>[x.id,x])).values()];
  state.countryLoaded=code;state.loading=false;
  if(!state.channels.length)state.error=error||"Nenhum canal disponível para este país.";
}

function favoriteIds(){return new Set(state.favorites[state.country]||[]);}
function changeFavorite(item){
  const set=favoriteIds();
  if(set.has(item.id))set.delete(item.id);else set.add(item.id);
  state.favorites[state.country]=[...set];
  safeSet(favoriteKey,JSON.stringify(state.favorites));
}
function countryPicker(after){
  const overlay=node("div","live-modal"),sheet=node("section","live-country-sheet");
  sheet.setAttribute("role","dialog");sheet.setAttribute("aria-modal","true");sheet.setAttribute("aria-label","Selecionar país");
  const head=node("div","live-modal-head");
  head.append(node("h2","", "Escolher país"),button("✕","live-close",()=>overlay.remove()));
  const input=node("input","live-country-search");input.placeholder="Pesquisar país ou sigla";
  input.setAttribute("aria-label","Pesquisar país");
  const list=node("div","live-country-results");
  function rows(){
    list.replaceChildren();
    COUNTRIES.filter(c=>c[2].toLowerCase().includes(input.value.toLowerCase())||
      c[0].toLowerCase().includes(input.value.toLowerCase())).forEach(c=>{
      const b=button(c[1]+"  "+c[2]+(c[0]===state.country?"  ✓":""),"live-country-option",()=>{
        state.country=c[0];state.search="";safeSet(countryKey,c[0]);overlay.remove();after();
      });
      list.append(b);
    });
  }
  input.addEventListener("input",rows);rows();sheet.append(head,input,list);
  overlay.append(sheet);overlay.addEventListener("click",e=>{if(e.target===overlay)overlay.remove();});
  document.body.append(overlay);input.focus();
}
function paint(host,play){
  if(!host.isConnected)return;
  host.replaceChildren();
  const country=COUNTRIES.find(c=>c[0]===state.country)||COUNTRIES[0];
  const page=node("section","live-page");
  const title=node("div","live-page-head");
  title.append(node("span","live-eyebrow","●  VISTASTREAM LIVE"),
    node("h1","", "Televisão ao vivo"),
    node("p","", "Os teus canais favoritos, organizados por país e categoria."));
  page.append(title);
  const hero=node("section","live-hero");
  const info=node("div","live-hero-info");
  info.append(node("span","live-eyebrow","ESCOLHE ONDE QUERES ASSISTIR"),
    node("h2","",country[1]+"  "+country[2]),
    node("p","", "Seleciona outro país para ver a respetiva lista de canais."));
  hero.append(info,button("Mudar país  ⌄","live-country-trigger",()=>{
    countryPicker(()=>{void refresh(true);});
  }));
  page.append(hero);

  const toolbar=node("div","live-toolbar"),search=node("input","live-search");
  search.placeholder="⌕  Pesquisar canais";search.value=state.search;
  search.setAttribute("aria-label","Pesquisar canais");
  search.addEventListener("input",()=>{state.search=search.value;renderResults();});
  toolbar.append(search,button("Fontes TV","live-reload",()=>openSources?.()),
    button("↻","live-reload",()=>{void refresh(true);}));
  page.append(toolbar);
  const chips=node("div","live-chips");
  page.append(chips);
  const counts=node("div","live-results-heading");
  page.append(counts);
  const results=node("div","live-grid");
  page.append(results);
  page.append(node("p","live-disclaimer",
    "Canais fornecidos por listas públicas externas. A disponibilidade, a autorização e a reprodução variam por região; algumas fontes podem não funcionar no Safari."));
  host.append(page);
  function renderResults(){
    chips.replaceChildren();results.replaceChildren();counts.replaceChildren();
    const groups=["Todos","Favoritos",...new Set(state.channels.map(c=>c.group))].slice(0,27);
    for(const group of groups){
      const chip=button(group,"live-chip"+(group===state.filter?" active":""),()=>{
        state.filter=group;renderResults();
      });chips.append(chip);
    }
    const favorites=favoriteIds();
    const visible=state.channels.filter(c=>
      (state.filter==="Todos"||(state.filter==="Favoritos"&&favorites.has(c.id))||
       state.filter===c.group)&&
      c.name.toLowerCase().includes(state.search.toLowerCase()));
    counts.append(node("h2","", "Canais de "+country[2]),
      node("span","",state.loading?"A carregar…":visible.length+" canais"));
    if(state.loading){results.append(node("div","live-message","A carregar canais…"));return;}
    if(state.error){
      results.append(node("div","live-message",state.error),
        button("Tentar novamente","live-reload",()=>{void refresh(true);}));
      return;
    }
    if(!visible.length){results.append(node("div","live-message","Nenhum canal nesta seleção."));return;}
    for(const ch of visible){
      const card=node("div","live-channel");
      card.setAttribute("role","button");card.setAttribute("tabindex","0");
      card.setAttribute("aria-label","Reproduzir "+ch.name);
      card.addEventListener("click",()=>void playChannel(ch,country,play));
      card.addEventListener("keydown",e=>{
        if(e.target===card&&(e.key==="Enter"||e.key===" ")){
          e.preventDefault();void playChannel(ch,country,play);
        }
      });
      const art=node("span","live-channel-logo");
      if(ch.logo){const image=node("img");image.src=ch.logo;image.alt="";image.loading="lazy";image.referrerPolicy="no-referrer";art.append(image);}
      else art.append(node("span","live-placeholder","◉"));
      const fav=button(favorites.has(ch.id)?"♥":"♡","live-fav",e=>{
        e.stopPropagation();changeFavorite(ch);renderResults();
      });
      fav.setAttribute("aria-label",favorites.has(ch.id)?"Remover favorito":"Adicionar favorito");
      const name=node("strong","",ch.name),group=node("span","live-group",ch.group+(ch.source?" · "+ch.source:""));
      card.append(art,fav,name,group);results.append(card);
    }
  }
  renderResults();
}
let currentHost=null,currentPlay=null;
async function refresh(force=false){
  const code=state.country;
  if(force||state.countryLoaded!==code){
    const p=load(code);if(currentHost)paint(currentHost,currentPlay);
    await p;
  }
  if(currentHost&&state.country===code)paint(currentHost,currentPlay);
}
export function renderLiveTv(host,{play,addons=[],onSources}){
  currentHost=host;currentPlay=play;installedLiveAddons=addons;openSources=onSources;
  paint(host,play);
  if(state.countryLoaded!==state.country&&!state.loading)void refresh();
}
