/* VistaStream Web 0.2 — visual parity + user-installed Stremio add-ons */
import {StremioHub,normalizeManifest} from "./stremio.js";
const CINE="https://v3-cinemeta.strem.io", ANI="https://graphql.anilist.co", TV="https://api.tvmaze.com";
const KEY="vista-web-local-v1",$=id=>document.getElementById(id);
const emptyStore=()=>({favorites:[],history:[],progress:{},sources:[],localTitles:[],addons:[]});
function readStore(){try{const v=JSON.parse(localStorage.getItem(KEY)||"{}");return {...emptyStore(),...v};}catch{return emptyStore();}}
const S={page:"home",segment:"all",filter:"all",sourcesTab:"browse",addonRows:[],addonsLoading:false,addonRequest:0,movies:[],shows:[],anime:[],catalog:[],results:[],search:"",searchToken:0,selected:null,season:1,episode:1,playback:null,store:readStore(),loading:true};
function save(){try{localStorage.setItem(KEY,JSON.stringify(S.store));}catch{notify("Sem espaço para guardar. Exporta um backup.");}}
const hub=new StremioHub(()=>S.store,save);
function el(tag,cls,txt){const n=document.createElement(tag);if(cls)n.className=cls;if(txt!=null)n.textContent=String(txt);return n;}
function append(p,...nodes){nodes.filter(Boolean).forEach(n=>p.append(n));return p;}
function btn(label,cls,action){const b=el("button",cls,label);b.type="button";b.addEventListener("click",action);return b;}
function notify(msg){const t=$("toast");t.textContent=msg;t.hidden=false;clearTimeout(notify.timer);notify.timer=setTimeout(()=>t.hidden=true,3800);}
function strip(s){return String(s||"").slice(0,5000).replace(/<[^>]*>/g," ").replace(/&(?:nbsp|amp|quot|lt|gt);/gi," ").replace(/\s+/g," ").trim().slice(0,1300);}
function slug(s){return String(s||"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase().replace(/[^a-z0-9]+/g," ").trim();}
function imageUrl(s){try{const u=new URL(s);return u.protocol==="https:"?u.href:"";}catch{return"";}}
function sourceOk(s){try{const u=new URL(String(s));return u.protocol==="https:"&&!u.username&&!u.password&&s.length<=4096&&!Array.from(u.searchParams.keys()).some(x=>/^(token|auth|sig|signature|session|key|expires|expiry|policy|ip_bind)$/i.test(x))&&/\.(mp4|m4v|mov|webm|m3u8|mpd|mkv)$/i.test(u.pathname);}catch{return false;}}
async function json(url,opts){const c=new AbortController(),timer=setTimeout(()=>c.abort(),12000);try{const r=await fetch(url,{...opts,signal:c.signal,credentials:"omit"});if(!r.ok)throw Error("HTTP "+r.status);return await r.json();}finally{clearTimeout(timer);}}
function cine(m,type){const ref=m.imdb_id||m.id;if(!ref||!m.name)return null;return {id:type+":"+ref,ref,type,name:m.name,year:Number(String(m.releaseInfo||m.year||"").match(/\d{4}/)?.[0])||0,poster:imageUrl(m.poster),background:imageUrl(m.background||m.poster),description:strip(m.description),rating:parseFloat(m.imdbRating)||0,genres:m.genre||[],provider:"Cinemeta"};}
function anime(m){const name=m.title?.english||m.title?.romaji;if(!name)return null;return {id:"anime:"+m.id,ref:m.id,type:"anime",name,year:m.seasonYear||0,poster:imageUrl(m.coverImage?.extraLarge),background:imageUrl(m.bannerImage||m.coverImage?.extraLarge),description:strip(m.description),rating:m.averageScore?m.averageScore/10:0,genres:m.genres||[],episodes:m.episodes||12,provider:"AniList"};}
function maze(m){return {id:"series:maze"+m.id,ref:m.id,type:"series",name:m.name,year:Number(String(m.premiered||"").slice(0,4))||0,poster:imageUrl(m.image?.original||m.image?.medium),background:imageUrl(m.image?.original),description:strip(m.summary),rating:m.rating?.average||0,genres:m.genres||[],provider:"TVmaze"};}
async function cineList(type,search){const s=search?"/search="+encodeURIComponent(search):"";const data=await json(CINE+"/catalog/"+type+"/top"+s+".json");return (data.metas||[]).slice(0,65).map(x=>cine(x,type)).filter(Boolean);}
const query="query List($q:String){Page(page:1,perPage:25){media(type:ANIME,search:$q,sort:TRENDING_DESC,isAdult:false){id title{english romaji} coverImage{extraLarge} bannerImage seasonYear description(asHtml:false) averageScore genres episodes}}}";
async function animeList(search){const d=await json(ANI,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({query,variables:{q:search||null}})});return(d.data?.Page?.media||[]).map(anime).filter(Boolean);}
function syncLocalTitles(){for(const src of S.store.sources){const type=src.kind==="tv"||src.kind==="series"?"series":src.kind==="anime"?"anime":"movie";if(S.catalog.some(x=>x.type===type&&slug(x.name)===slug(src.title)))continue;const id="local:"+type+":"+slug(src.title);if(S.catalog.some(x=>x.id===id))continue;S.catalog.push({id,type,name:src.title,year:src.year||0,poster:"",background:"",description:"Fonte da tua biblioteca pessoal.",genres:[],rating:0,provider:"Biblioteca pessoal"});}}
async function loadCatalog(){S.loading=true;render();const all=await Promise.allSettled([cineList("movie"),cineList("series"),animeList()]);S.movies=all[0].status==="fulfilled"?all[0].value:[];S.shows=all[1].status==="fulfilled"?all[1].value:[];S.anime=all[2].status==="fulfilled"?all[2].value:[];if(!S.shows.length){try{const d=await json(TV+"/shows?page=0");S.shows=d.filter(x=>x.image).slice(0,40).map(maze);}catch{}}S.catalog=[...S.movies,...S.shows,...S.anime];syncLocalTitles();S.loading=false;render();refreshAddonCatalog();}
function title(id){return S.catalog.find(x=>x.id===id);}
function sources(item,s=1,e=1){if(!item)return[];return S.store.sources.filter(x=>{const type=x.kind==="tv"||x.kind==="series"?"series":x.kind==="anime"?"anime":"movie";return type===item.type&&slug(x.title)===slug(item.name)&&(type==="movie"||(x.season===s&&x.episode===e));});}
function fav(item){return S.store.favorites.includes(item.id);}
function favorite(item){if(fav(item))S.store.favorites=S.store.favorites.filter(x=>x!==item.id);else S.store.favorites.unshift(item.id);save();render();if(S.selected?.id===item.id)renderDetails();}
function latest(item){return Object.entries(S.store.progress).filter(([k,x])=>x.itemId===item.id&&x.time>10).sort((a,b)=>b[1].updated-a[1].updated)[0]?.[1];}
function card(item){const b=btn("","media-card",()=>details(item));const art=el("span","poster");if(item.poster){const img=el("img");img.src=item.poster;img.alt="";img.loading="lazy";art.append(img);}append(art,el("span","top-tag",item.type==="anime"?"ANIME":item.type==="series"?"SÉRIE":"FILME"));if(item.rating)append(art,el("span","rating","★ "+Number(item.rating).toFixed(1)));const p=latest(item);if(p?.duration){const bar=el("span","progress-track"),fill=el("i");fill.style.width=Math.min(100,Math.round(p.time/p.duration*100))+"%";append(bar,fill);append(art,bar);}append(b,art,el("strong","",item.name),el("span","meta",(item.year||"")+" · "+(item.genres?.[0]||item.type)));return b;}
function rail(name,items,sub){if(!items.length)return null;const section=el("section","section"),head=el("div","section-header"),titleDiv=el("div"),row=el("div","rail");append(titleDiv,el("h2","section-title",name));if(sub)append(titleDiv,el("p","section-sub",sub));append(head,titleDiv);items.slice(0,30).forEach(x=>row.append(card(x)));return append(section,head,row);}
function heading(eyebrow,name,text){const h=el("div","page-header");append(h,el("span","eyebrow",eyebrow),el("h1","",name),el("p","",text));return h;}
function blank(text,action){const d=el("div","empty");append(d,el("div","empty-symbol","✦"),el("h3","","O VistaStream está pronto"),el("p","",text));if(action)append(d,btn("Abrir biblioteca","primary-btn accent",()=>navigate("library")));return d;}
function hero(item){const h=el("section","hero"),bg=el("div","hero-image"),copy=el("div","hero-copy");if(item.background)bg.style.backgroundImage="url("+JSON.stringify(item.background)+")";append(copy,el("span","eyebrow","✦ EM DESTAQUE NO VISTASTREAM"),el("h1","",item.name),el("div","hero-tags",(item.year||"")+" · "+item.type.toUpperCase()+" · "+(item.rating?"★ "+Number(item.rating).toFixed(1):"")),el("p","sub",item.description||"Descobre mais sobre este título e associa as tuas fontes pessoais."));const actions=el("div","hero-actions");append(actions,btn("▶ Ver detalhes","primary-btn",()=>details(item)),btn(fav(item)?"✓ Na biblioteca":"＋ Minha lista","outline-btn",()=>favorite(item)));append(copy,actions);return append(h,bg,copy);}
function heroCarousel(items){
  const section=el("section","premium-hero-section"),head=el("div","premium-hero-head","✦ EM DESTAQUE NO VISTASTREAM");
  const rail=el("div","hero-carousel"),dots=el("div","hero-dots");
  items.slice(0,8).forEach((item,index)=>{
    const card=btn("","hero-poster",()=>details(item));
    if(item.poster){const img=el("img");img.src=item.poster;img.alt="";img.loading=index<3?"eager":"lazy";card.append(img);}
    const rank=el("span","hero-rank","#"+(index+1)+" EM DESTAQUE");
    const bottom=el("div","hero-bottom");
    append(bottom,el("h2","",item.name),el("div","hero-info",(item.year||"")+"  ·  "+(item.type==="movie"?"Filme":item.type==="anime"?"Anime":"Série")+"  ·  "+(item.rating?"★ "+Number(item.rating).toFixed(1):"Em destaque")),el("span","hero-play","▶   Ver detalhes"));
    append(card,rank,bottom);rail.append(card);
    dots.append(el("span","hero-dot"+(index===0?" current":"")));
  });
  let pending=0;
  rail.addEventListener("scroll",()=>{
    cancelAnimationFrame(pending);
    pending=requestAnimationFrame(()=>{
      const first=rail.firstElementChild;
      if(!first)return;
      const center=rail.scrollLeft+rail.clientWidth/2;
      const index=Math.round((center-(first.offsetLeft-rail.offsetLeft+first.clientWidth/2))/(first.clientWidth+13));
      const current=Math.max(0,Math.min(dots.children.length-1,index));
      [...dots.children].forEach((x,i)=>x.classList.toggle("current",i===current));
    });
  },{passive:true});
  return append(section,head,rail,dots);
}
function quickExplore(){
  const card=el("section","quick-explore-card"),body=el("div");
  append(body,el("strong","","Explorar catálogo completo"),el("small","","Filmes, séries, animes e extensões instaladas"));
  return append(card,body,btn("Explorar  ↗","primary-btn",()=>navigate("discover")));
}
function home(){
  const animeMode=S.segment==="anime";
  const list=animeMode?S.anime:[...S.movies.slice(0,4),...S.shows.slice(0,4)];
  const main=list[0]||S.catalog[0];
  if(!main){append($("content"),blank("O catálogo está temporariamente indisponível. Podes continuar na biblioteca.",true));return;}
  append($("content"),heroCarousel(list.length?list:[main]),quickExplore());
  if(animeMode){
    append($("content"),rail("Em exibição e populares",S.anime,"Animes em alta no AniList"),
      rail("Descobre novos animes",[...S.anime].reverse()));
  }else{
    const cont=S.store.history.map(x=>title(x.id)).filter(x=>x&&latest(x));
    append($("content"),rail("Continuar a assistir",cont),
      rail("Filmes populares",S.movies),
      rail("Séries em destaque",S.shows),
      rail("Animes para descobrir",S.anime));
  }
  S.addonRows.filter(x=>x.items?.length).slice(0,4).forEach(x=>append($("content"),rail(x.label,x.items,"Catálogo · "+x.addon.name)));
}
async function refreshAddonCatalog(){
  const token=++S.addonRequest;
  if(!hub.list().some(x=>x.enabled)){S.addonRows=[];return;}
  S.addonsLoading=true;
  try{
    const result=await hub.catalogRows();if(S.addonRequest!==token)return;
    S.addonRows=result;
    const extra=result.flatMap(x=>x.items||[]);
    S.catalog=[...new Map([...S.catalog,...extra].map(x=>[x.id,x])).values()];
    if(["home","discover","anime","sources"].includes(S.page))render();
  }catch(e){console.warn("Vista addons:",e.message);}
  finally{if(S.addonRequest===token)S.addonsLoading=false;}
}
function discover(animesOnly=false){const wrap=$("content");append(wrap,heading(animesOnly?"✦ VISTASTREAM ANIME":"VISTASTREAM · EXPLORAR",animesOnly?"O mundo dos animes":"Descobre algo novo",animesOnly?"Explora títulos do AniList.": "Filmes, séries e animes num único catálogo."));const opts=animesOnly?[["anime","Todos os animes"]]:[["all","Todos"],["movie","Filmes"],["series","Séries"],["anime","Animes"]];const chips=el("div","chips");opts.forEach(([key,label])=>chips.append(btn(label,"chip"+(S.filter===key?" selected":""),()=>{S.filter=key;render();})));append(wrap,chips);const filtered=S.catalog.filter(x=>animesOnly?x.type==="anime":S.filter==="all"||x.type===S.filter);if(!filtered.length){append(wrap,blank("Sem resultados. Liga-te à internet e tenta novamente.",true));return;}const grid=el("div","grid-cards");filtered.forEach(x=>grid.append(card(x)));append(wrap,grid);}
async function search(q){S.search=q;const current=++S.searchToken;if(!q.trim()){S.results=[];displayResults();return;}const results=await Promise.allSettled([cineList("movie",q),cineList("series",q),animeList(q)]);if(current!==S.searchToken)return;let all=results.flatMap(x=>x.status==="fulfilled"?x.value:[]);if(!all.length){try{const d=await json(TV+"/search/shows?q="+encodeURIComponent(q));all=d.slice(0,20).map(x=>maze(x.show));}catch{}}if(current!==S.searchToken)return;S.results=all;S.catalog=[...new Map([...S.catalog,...all].map(x=>[x.id,x])).values()];displayResults();}
function displayResults(){const host=$("search-results");if(!host)return;host.replaceChildren();if(!S.search){append(host,blank("Procura pelo nome de um filme, série ou anime.",false));return;}if(!S.results.length){append(host,el("p","section-sub","A pesquisar… Se não aparecerem resultados, tenta outro título."));return;}const grid=el("div","grid-cards");S.results.forEach(x=>grid.append(card(x)));host.append(grid);}
function searchPage(){const wrap=$("content");append(wrap,heading("ENCONTRA O QUE QUERES VER","Pesquisa","Procura títulos em vários catálogos."));const container=el("label","search-input-wrap"),icon=el("span","","⌕"),inp=el("input","search-input");inp.id="search-input";inp.type="search";inp.placeholder="Filme, série ou anime…";inp.value=S.search;inp.autocomplete="off";let timeout;inp.addEventListener("input",()=>{S.search=inp.value;clearTimeout(timeout);timeout=setTimeout(()=>search(inp.value.trim().slice(0,100)),350);});append(container,icon,inp);append(wrap,container,el("div",""));wrap.lastChild.id="search-results";displayResults();}
function library(){const wrap=$("content");append(wrap,heading("A TUA COLEÇÃO","Biblioteca","Favoritos, continuar a assistir e fontes pessoais deste dispositivo."));const cont=S.store.history.map(x=>title(x.id)).filter(x=>x&&latest(x));append(wrap,rail("Continuar a assistir",cont));const fs=S.store.favorites.map(id=>title(id)).filter(Boolean);if(fs.length){const grid=el("div","grid-cards");fs.forEach(x=>grid.append(card(x)));append(wrap,el("h2","section-title","A minha lista"),grid);}else append(wrap,blank("Adiciona um título à tua lista de favoritos.",false));const b=el("section","setting-block");append(b,el("h3","","Fontes pessoais · "+S.store.sources.length),el("p","","Apenas endereços diretos e autorizados. Algumas fontes Android não são compatíveis com Safari."));for(const s of S.store.sources.slice(0,40)){const row=el("div","source-entry"),body=el("div");append(body,el("b","",s.title),el("small","",s.kind+" · "+s.season+"x"+s.episode+" · "+(s.quality||"Auto")));append(row,body,btn("Remover","",()=>{if(confirm("Remover esta fonte da biblioteca local?")){S.store.sources=S.store.sources.filter(x=>x.id!==s.id);save();render();}}));append(b,row);}const actions=el("div","action-row");append(actions,btn("Importar Vista Source","primary-btn accent",()=>$("source-file").click()),btn("Adicionar URL","outline-btn",manual));append(b,actions);wrap.append(b);}
function addonsPage(){
  const main=$("content");
  append(main,heading("VISTASTREAM · FONTES","Extensões e fontes","Instala extensões Stremio compatíveis, consulta catálogos e escolhe fontes no próprio título."));
  const nav=el("div","source-tabs");
  [["browse","Explorar"],["installed","Instaladas"],["repositories","Repositórios"]].forEach(([mode,text])=>
    nav.append(btn(text,S.sourcesTab===mode?"active":"",()=>{S.sourcesTab=mode;render();})));
  main.append(nav);
  if(S.sourcesTab==="installed"){
    const active=hub.list();
    if(!active.length){append(main,blank("Ainda não tens extensões. Podes instalar fontes Stremio pelo endereço HTTPS do manifest.json.",false),
      btn("＋ Instalar extensão","primary-btn accent",()=>{S.sourcesTab="repositories";render();}));return;}
    append(main,el("p","section-sub",active.length+" extensões guardadas neste iPhone. A extensão nunca é instalada no sistema: o site usa as suas APIs públicas."));
    for(const addon of active){
      const card=el("section","addon-card"),heading=el("div");
      append(heading,el("strong","",addon.name),el("p","",addon.description||"Extensão Stremio"));
      const badges=el("div","addon-status");
      append(badges,el("span","addon-tag",addon.enabled?"● Ativa":"Pausada"),
        el("span","addon-stat",(addon.catalogs?.length||0)+" catálogos"),el("span","addon-stat","Versão "+(addon.version||"—")));
      const actions=el("div","action-row");
      append(actions,
        btn(addon.enabled?"Desativar":"Ativar","small-btn",()=>{hub.toggle(addon.id);render();refreshAddonCatalog();}),
        btn("Remover","small-btn",()=>{if(confirm("Remover a extensão "+addon.name+" deste dispositivo?")){hub.remove(addon.id);render();refreshAddonCatalog();}}));
      append(card,heading,badges,actions);main.append(card);
    }
    append(main,btn("＋ Adicionar outra extensão","primary-btn accent",()=>{S.sourcesTab="repositories";render();}));return;
  }
  if(S.sourcesTab==="repositories"){
    const intro=el("section","setting-block");
    append(intro,el("h3","","Instalar extensão Stremio"),el("p","","Cola o endereço HTTPS de manifest.json de uma extensão em que confias. O navegador precisa de acesso CORS à API. Algumas extensões exigem configuração externa ou não reproduzem no Safari."));
    const form=el("form","source-form"),label=el("label","","Endereço da extensão (manifest.json)");
    const input=el("input");input.type="url";input.required=true;input.autocomplete="off";input.placeholder="https://addon.exemplo.com/manifest.json";
    const submit=el("button","primary-btn accent","Instalar extensão");submit.type="submit";
    append(form,label,input,submit);
    form.addEventListener("submit",async ev=>{
      ev.preventDefault();submit.disabled=true;submit.textContent="A verificar extensão…";
      try{
        const addon=await hub.install(input.value);
        notify("Extensão "+addon.name+" instalada");
        S.sourcesTab="installed";render();refreshAddonCatalog();
      }catch(err){notify("Não foi possível instalar: "+(err.message||"erro de rede/CORS"));}
      finally{submit.disabled=false;submit.textContent="Instalar extensão";}
    });
    append(intro,form);main.append(intro);
    const reference=el("section","setting-block");
    append(reference,el("h3","","Fontes integradas e recomendadas"),
      el("p","","O catálogo Cinemeta e o AniList já estão disponíveis no VistaStream Web. Se quiseres, também podes adicionar o Cinemeta como extensão Stremio para experimentar o gestor de catálogos."));
    const buttons=el("div","action-row");
    append(buttons,btn("＋ Instalar Cinemeta (metadados)","outline-btn",async()=>{
      try{const a=await hub.install("https://v3-cinemeta.strem.io/manifest.json");notify(a.name+" instalado");S.sourcesTab="installed";render();refreshAddonCatalog();}
      catch(e){notify(e.message||"Cinemeta indisponível");}
    }));
    append(reference,buttons);main.append(reference);
    const note=el("section","setting-block");
    append(note,el("h3","","Repositórios e formatos"),
      el("p","","Extensões Stremio usam o seu próprio manifesto e endpoints de catálogo, metadados e streams. Extensões Android CloudStream ou APKs não funcionam diretamente num site; exigiriam adaptadores Web próprios. Não usaremos extensões desconhecidas sem consentimento."));
    const official=el("a","small-btn","Documentação oficial do protocolo Stremio ↗");
    official.href="https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md";
    official.target="_blank";official.rel="noopener noreferrer";note.append(official);
    main.append(note);
    return;
  }
  const actions=el("section","quick-explore-card"),desc=el("div");
  append(desc,el("strong","","As tuas extensões"),el("small","",hub.list().length+" instaladas · "+hub.list().filter(x=>x.enabled).length+" ativas"));
  append(actions,desc,btn("Gerir fontes  ↗","primary-btn",()=>{S.sourcesTab="installed";render();}));
  main.append(actions);
  const catalog=el("section","section");
  append(catalog,el("h2","section-title","Catálogos das extensões"));
  if(!hub.list().length){
    append(catalog,blank("Adiciona uma extensão Stremio para encontrar catálogos e consultar fontes disponíveis nos títulos.",false),
      btn("＋ Adicionar extensão","primary-btn accent",()=>{S.sourcesTab="repositories";render();}));
  }else if(S.addonsLoading){
    append(catalog,el("div","loading-splash","A consultar os catálogos das extensões…"));
  }else{
    const active=S.addonRows.filter(x=>x.items?.length);
    if(!active.length){
      append(catalog,el("div","status-line","Nenhum catálogo acessível neste momento. Algumas extensões só oferecem streams, exigem pesquisa, ou não permitem chamadas CORS a partir do Safari."));
    }else active.forEach(x=>append(catalog,rail(x.label,x.items,"Origem: "+x.addon.name)));
    append(catalog,btn("↻ Atualizar catálogos","outline-btn",()=>refreshAddonCatalog()));
  }
  main.append(catalog);
  const personal=el("section","setting-block");
  append(personal,el("h3","","Biblioteca do VistaBrowser"),el("p","","Importa as fontes do manifest.json do Vista Source e utiliza-as no mesmo player. A importação mantém-se local no teu iPhone."));
  const pActions=el("div","action-row");
  append(pActions,btn("Importar Vista Source","primary-btn accent",()=>$("source-file").click()),
    btn("Adicionar vídeo próprio","outline-btn",manual));
  append(personal,pActions);main.append(personal);
}
function settings(){const wrap=$("content");append(wrap,heading("PREFERÊNCIAS","Definições","O VistaStream está disponível no iPhone, sem IPA ou Mac."));const blocks=[
 ["📱 Adicionar ao iPhone","No Safari, toca em Partilhar → Adicionar ao ecrã principal → Abrir como aplicação.","Web App gratuita"],
 ["▤ Vista Source","Importa um manifest.json exportado pelo VistaBrowser ou VistaStream Android. O conteúdo permanece guardado apenas neste navegador.","Importar manifest.json"],
 ["◫ Backup da biblioteca","Guarda favoritos, fontes e progresso antes de trocar de dispositivo ou limpar o Safari.","Exportar e importar backup"],
 ["◎ Vista ID e sincronização","Esta primeira versão funciona em modo local. A sincronização automática Android/TV/iPhone precisa de um serviço Vista ID autenticado; não está ainda ativa.","Modo local"],
 ["Sobre","Metadados: Cinemeta, AniList e TVmaze. O VistaStream não fornece streams não autorizados. Algumas fontes não funcionam no Safari.","Versão Web 0.1.0"]];
 blocks.forEach(([name,text,caption],i)=>{const block=el("section","setting-block");append(block,el("h3","",name),el("p","",text));const actions=el("div","action-row");if(i===1)append(actions,btn("Importar manifest.json","primary-btn accent",()=>$("source-file").click()),btn("Adicionar URL","outline-btn",manual));else if(i===2)append(actions,btn("Exportar backup","outline-btn",exportBackup),btn("Importar backup","outline-btn",()=>$("backup-file").click()));else append(actions,el("span","status-pill",caption));append(block,actions);wrap.append(block);});}
function render(){document.querySelectorAll("[data-tab]").forEach(b=>b.classList.toggle("selected",b.dataset.tab===S.page));document.querySelectorAll("[data-segment]").forEach(b=>b.classList.toggle("current",b.dataset.segment===S.segment));$("content").replaceChildren();if(S.loading){append($("content"),el("div","loading-splash","A carregar o VistaStream…"));return;}if(S.page==="home")home();else if(S.page==="discover")discover();else if(S.page==="anime")discover(true);else if(S.page==="search")searchPage();else if(S.page==="library")library();else if(S.page==="sources")addonsPage();else settings();}
function navigate(page){S.page=["home","discover","anime","search","library","sources","settings"].includes(page)?page:"home";history.replaceState(null,"","#"+S.page);render();scrollTo({top:0,behavior:"instant"});if(S.page==="search")$("search-input")?.focus();}
async function episodeList(item){if(item.type==="movie")return[];if(item._eps)return item._eps;let out=[];if(item.provider==="Cinemeta"){try{const d=await json(CINE+"/meta/series/"+encodeURIComponent(item.ref)+".json");out=(d.meta?.videos||[]).filter(x=>x.season>0&&x.episode>0).map(x=>({s:Number(x.season),e:Number(x.episode),name:x.title||"Episódio "+x.episode}));}catch{}}if(item.provider==="TVmaze"){try{const d=await json(TV+"/shows/"+item.ref+"/episodes");out=d.filter(x=>x.season>0&&x.number>0).map(x=>({s:x.season,e:x.number,name:x.name||"Episódio "+x.number}));}catch{}}if(!out.length){const matching=S.store.sources.filter(x=>slug(x.title)===slug(item.name));if(matching.length)out=matching.map(x=>({s:x.season,e:x.episode,name:"Episódio "+x.episode}));else out=Array.from({length:Math.min(item.episodes||12,60)},(_,i)=>({s:1,e:i+1,name:"Episódio "+(i+1)}));}item._eps=out;return out;}
function details(item){S.selected=item;S.season=1;S.episode=1;$("details-overlay").hidden=false;renderDetails();}
function renderDetails(){const item=S.selected;if(!item)return;const host=$("details-panel");host.replaceChildren();const cover=el("div","detail-cover");if(item.background)cover.style.backgroundImage="url("+JSON.stringify(item.background)+")";append(cover,btn("×","detail-close",closeDetails));const body=el("div","detail-body"),header=el("span","detail-info",item.type.toUpperCase()+" · "+(item.year||"")+" "+(item.rating?" · ★ "+Number(item.rating).toFixed(1):""));append(body,header,el("h2","",item.name),el("p","detail-desc",item.description||"Sem sinopse disponível."));const actions=el("div","action-row");append(actions,btn("▶ "+(sources(item,S.season,S.episode).length?"Reproduzir":"Adicionar fonte para ver"),"primary-btn accent",playSelected),btn(fav(item)?"♥ Na minha lista":"♡ Minha lista","outline-btn",()=>favorite(item)));append(body,actions);
  if(item.type!=="movie"){const eps=el("div","detail-source"),head=el("h4","","Temporadas e episódios"),sel=el("select"),box=el("div","episode-rows");sel.id="season-pick";append(eps,head,sel,box);append(body,eps);episodeList(item).then(items=>{if(S.selected?.id!==item.id)return;const seasons=[...new Set(items.map(x=>x.s))].sort((a,b)=>a-b);if(!seasons.includes(S.season))S.season=seasons[0]||1;sel.replaceChildren();seasons.forEach(s=>{const o=el("option","", "Temporada "+s);o.value=s;o.selected=s===S.season;sel.append(o);});const show=()=>{box.replaceChildren();items.filter(x=>x.s===S.season).slice(0,100).forEach(x=>{const avail=sources(item,x.s,x.e).length;append(box,btn("▶ E"+String(x.e).padStart(2,"0")+" · "+x.name+(avail?" · fonte disponível":""),"episode-row",()=>{S.episode=x.e;playSelected();}));});};sel.addEventListener("change",()=>{S.season=Number(sel.value);S.episode=1;show();});show();});}
 const list=el("div","detail-source");append(list,el("h4","","Fontes pessoais"));const found=sources(item,S.season,S.episode);found.forEach(x=>{const row=el("div","source-entry");append(row,el("b","",x.quality||"Fonte direta"),btn("▶ Reproduzir","",()=>play(x,item,S.season,S.episode)));list.append(row);});if(!found.length)append(list,el("p","","Ainda não existe uma fonte associada. O catálogo fornece metadados, não os vídeos."));append(list,btn("＋ Adicionar fonte","small-btn",()=>sourceForm(item)));append(body,list);append(host,cover,body);
}
function closeDetails(){$("details-overlay").hidden=true;S.selected=null;}
function sourceForm(item){const section=el("div","detail-source");append(section,el("h4","","Adicionar fonte para "+item.name),el("p","","Adiciona apenas uma URL HTTPS direta de conteúdo autorizado. MP4/HLS são recomendados para Safari."));const form=el("form");const input=el("input");input.type="url";input.required=true;input.placeholder="https://servidor.pt/video.m3u8";input.style.width="100%";input.style.padding="14px";input.style.borderRadius="12px";input.style.background="#202638";input.style.color="#fff";input.style.border="1px solid #444a5c";form.append(input,el("p","section-sub","Não guardamos cookies, tokens ou URLs de reprodução protegidas."));const submit=el("button","primary-btn accent","Guardar fonte");submit.type="submit";form.append(submit);form.addEventListener("submit",e=>{e.preventDefault();const count=addSources([{title:item.name,url:input.value,kind:item.type==="series"?"tv":item.type,season:S.season,episode:S.episode}]);if(count){section.remove();renderDetails();notify("Fonte adicionada");}else notify("Fonte inválida. Precisa ser HTTPS direto sem tokens.");});append(section,form);$("details-panel").querySelector(".detail-body").append(section);section.scrollIntoView({behavior:"smooth"});}
function manual(){navigate("settings");const box=el("section","setting-block"),form=el("form");append(box,el("h3","","Adicionar fonte pessoal"));function input(name,placeholder,type="text"){const field=el("div","field"),label=el("label","",placeholder),i=el("input");i.name=name;i.type=type;i.placeholder=placeholder;i.required=true;append(field,label,i);form.append(field);return i;}input("title","Nome do filme, série ou anime");const select=el("select");select.name="kind";[["movie","Filme"],["tv","Série"],["anime","Anime"]].forEach(([v,l])=>{const o=el("option","",l);o.value=v;select.append(o);});form.append(select);const sea=input("season","Temporada","number"),epi=input("episode","Episódio","number");sea.value="1";epi.value="1";input("url","URL HTTPS MP4/HLS","url");const submit=el("button","primary-btn accent","Guardar fonte");submit.type="submit";form.append(submit);form.addEventListener("submit",e=>{e.preventDefault();const d=Object.fromEntries(new FormData(form));const count=addSources([d]);if(count){notify("Fonte adicionada");navigate("library");}else notify("Endereço de vídeo inválido.");});box.append(form);$("content").append(box);box.scrollIntoView({behavior:"smooth"});}
function addSources(items){let count=0;const prior=new Set(S.store.sources.map(x=>x.url+":"+x.season+":"+x.episode));for(const x of items.slice(0,250)){if(!x||!sourceOk(x.url)||!x.title||typeof x.title!=="string")continue;const season=Number(x.season)||1,episode=Number(x.episode)||1;if(season<1||season>999||episode<1||episode>9999)continue;const signature=x.url+":"+season+":"+episode;if(prior.has(signature))continue;S.store.sources.push({id:"src-"+Date.now()+"-"+Math.random().toString(36).slice(2),url:x.url,title:x.title.slice(0,180),kind:["tv","series","movie","anime"].includes(x.kind)?x.kind:"movie",season,episode,year:Number(x.year)||0,quality:String(x.quality||"Auto").slice(0,30),language:String(x.language||"").slice(0,20)});prior.add(signature);count++;}S.store.sources=S.store.sources.slice(-500);save();syncLocalTitles();return count;}
async function importSource(file){if(!file)return;if(file.size>1024*1024){notify("Manifesto demasiado grande.");return;}try{const m=JSON.parse(await file.text());if(m.type!=="vista-source"||m.schemaVersion!==1||!Array.isArray(m.items)||m.items.length>250)throw Error("Vista Source v1 inválido");const n=addSources(m.items);notify(n+" fontes importadas");navigate("library");}catch(e){notify("Falha ao importar: "+e.message);}}
function download(text,name){const url=URL.createObjectURL(new Blob([text],{type:"application/json"}));const a=el("a");a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),4000);}
function exportBackup(){download(JSON.stringify({type:"vistastream-web-backup",version:1,store:S.store},null,2),"VistaStream-backup.json");notify("Backup preparado");}
async function importBackup(file){if(!file||file.size>2*1024*1024)return;try{const m=JSON.parse(await file.text());if(m.type!=="vistastream-web-backup"||m.version!==1||!Array.isArray(m.store?.sources))throw Error("Backup inválido");if(!confirm("Substituir a biblioteca guardada no iPhone?"))return;S.store=emptyStore();S.store.favorites=Array.isArray(m.store.favorites)?m.store.favorites.slice(0,500):[];S.store.progress=m.store.progress||{};S.store.history=Array.isArray(m.store.history)?m.store.history.slice(0,150):[];addSources(m.store.sources);save();notify("Backup restaurado");navigate("library");}catch(e){notify(e.message);}}
function playSelected(){const item=S.selected;if(!item)return;const xs=sources(item,S.season,S.episode);if(xs.length)play(xs[0],item,S.season,S.episode);else sourceForm(item);}
function savePlayback(){if(!S.playback||!Number.isFinite($("video-player").currentTime)||$("video-player").currentTime<1)return;const p=S.playback,v=$("video-player"),key=p.item.id+":"+p.s+":"+p.e;S.store.progress[key]={itemId:p.item.id,time:v.currentTime,duration:Number.isFinite(v.duration)?v.duration:0,updated:Date.now()};save();}
function play(src,item,s,e){if(!sourceOk(src.url)){notify("Esta fonte não pode ser reproduzida.");return;}closeDetails();S.playback={item,s,e};$("player-overlay").hidden=false;$("player-title").textContent=item.name;$("player-subtitle").textContent=item.type==="movie"?"Filme":"Temporada "+s+" · Episódio "+e;const v=$("video-player");v.removeAttribute("crossorigin");v.src=src.url;v.load();S.store.history=[{id:item.id,updated:Date.now()},...S.store.history.filter(x=>x.id!==item.id)].slice(0,100);save();const old=S.store.progress[item.id+":"+s+":"+e]?.time||0;v.addEventListener("loadedmetadata",()=>{if(old>5&&old<v.duration-15)v.currentTime=old;},{once:true});v.play().catch(()=>{const p=$("player-error");p.hidden=false;p.textContent="Toque em ▶ no reprodutor. Alguns formatos ou fontes não são compatíveis com Safari.";});}
function closePlayer(){savePlayback();const v=$("video-player");v.pause();v.removeAttribute("src");v.load();S.playback=null;$("player-overlay").hidden=true;render();}
function closeAll(){if(!$("player-overlay").hidden)closePlayer();else if(!$("details-overlay").hidden)closeDetails();}
document.addEventListener("click",e=>{const b=e.target.closest("[data-tab],[data-segment],[data-close]");if(b?.dataset.tab)navigate(b.dataset.tab);else if(b?.dataset.segment){S.segment=b.dataset.segment;navigate("home");}else if(b?.dataset.close==="details")closeDetails();else if(b?.dataset.close==="player")closePlayer();});
$("quick-search").addEventListener("click",()=>navigate("search"));$("quick-settings").addEventListener("click",()=>navigate("settings"));$("close-player").addEventListener("click",closePlayer);
$("source-file").addEventListener("change",e=>{importSource(e.target.files[0]);e.target.value="";});$("backup-file").addEventListener("change",e=>{importBackup(e.target.files[0]);e.target.value="";});
$("video-player").addEventListener("timeupdate",()=>{const v=$("video-player");if(!v._lastSave||Date.now()-v._lastSave>5000){v._lastSave=Date.now();savePlayback();}});
$("video-player").addEventListener("error",()=>{const p=$("player-error");p.hidden=false;p.textContent="Fonte indisponível ou formato não suportado pelo Safari. Verifica se o endereço é autorizado e acessível.";});
window.addEventListener("visibilitychange",()=>{if(document.hidden)savePlayback();});window.addEventListener("keydown",e=>{if(e.key==="Escape")closeAll();});
window.addEventListener("hashchange",()=>{const page=location.hash.slice(1);if(page!==S.page)navigate(page);});
if("serviceWorker" in navigator)window.addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));
navigate(location.hash.slice(1)||"home");loadCatalog();
