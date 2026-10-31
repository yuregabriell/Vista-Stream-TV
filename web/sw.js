/* App shell only — never cache private sources, search responses or video bytes. */
const VERSION="vistastream-web-shell-v2";
const SHELL=["./","./index.html","./styles.css","./native.css","./app.js","./stremio.js","./manifest.webmanifest","./icon.svg","./icons/icon-180.png","./icons/icon-192.png","./icons/icon-512.png"];
self.addEventListener("install",event=>{event.waitUntil(caches.open(VERSION).then(cache=>cache.addAll(SHELL)));self.skipWaiting();});
self.addEventListener("activate",event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(x=>x.startsWith("vistastream-web-shell-")&&x!==VERSION).map(x=>caches.delete(x)))));self.clients.claim();});
self.addEventListener("fetch",event=>{const u=new URL(event.request.url);if(event.request.method!=="GET"||u.origin!==self.location.origin)return;if(!SHELL.some(x=>new URL(x,self.registration.scope).pathname===u.pathname))return;event.respondWith(fetch(event.request).then(resp=>{if(resp.ok){const copy=resp.clone();caches.open(VERSION).then(cache=>cache.put(event.request,copy)).catch(()=>{});}return resp;}).catch(()=>caches.match(event.request)));});
