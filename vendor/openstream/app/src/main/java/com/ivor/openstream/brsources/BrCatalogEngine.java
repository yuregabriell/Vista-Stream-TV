package com.ivor.openstream.brsources;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** BR source adapters: discover metadata, distinguish tested direct video from player pages.
 *  No CloudStream .cs3 execution, auth bypass, DRM removal, or ad interactions. */
public final class BrCatalogEngine {
  public static final class Entry {
    public final String provider, title, url, image, type, hint;
    public Entry(String p,String t,String u,String i,String type,String hint) {
      this.provider=p;this.title=t;this.url=u;this.image=i;this.type=type;this.hint=hint;
    }
  }
  public static final class Stream {
    public final String url,mime,label;
    public Stream(String url,String mime,String label) {this.url=url;this.mime=mime;this.label=label;}
  }
  public static final class Detail {
    public final ArrayList<Stream> streams = new ArrayList<>();
    public final ArrayList<Entry> episodes = new ArrayList<>();
    public String notice="";
  }
  public static final LinkedHashMap<String,String> PROVIDERS = new LinkedHashMap<>();
  static {
    PROVIDERS.put("archive","Acervo aberto — português");
    PROVIDERS.put("animefire","AnimeFire — experimental");
    PROVIDERS.put("pobreflix","PobreFlix — experimental");
  }
  private static final Pattern ID = Pattern.compile("[a-zA-Z0-9_.-]{1,150}");
  private static final Pattern VIDEO = Pattern.compile("(?i)[.](mp4|m4v|webm|m3u8|mpd)([?#]|$)");
  private final android.content.SharedPreferences prefs;
  public BrCatalogEngine(Context context) {
    prefs=context.getApplicationContext().getSharedPreferences("br_catalog_sources_v1",Context.MODE_PRIVATE);
  }
  public boolean enabled(String key) {return prefs.getBoolean(key,key.equals("archive"));}
  public void setEnabled(String key,boolean enabled) {
    if(PROVIDERS.containsKey(key))prefs.edit().putBoolean(key,enabled).apply();
  }
  private static String encode(String s) {
    try{return URLEncoder.encode(s,"UTF-8");}catch(Exception ex){return s;}
  }
  private static boolean http(String s) {
    try {Uri u=Uri.parse(s);return ("https".equalsIgnoreCase(u.getScheme())||
      "http".equalsIgnoreCase(u.getScheme()))&&!u.getHost().isEmpty();}catch(Exception e){return false;}
  }
  private static boolean video(String address) {return http(address)&&VIDEO.matcher(address).find();}
  public static String mime(String address) {
    String s=address.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
    if(s.endsWith(".m3u8"))return "application/vnd.apple.mpegurl";
    if(s.endsWith(".mpd"))return "application/dash+xml";
    if(s.endsWith(".webm"))return "video/webm";
    return "video/mp4";
  }
  private static JSONObject json(String address)throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
    c.setConnectTimeout(8500);c.setReadTimeout(8500);
    c.setRequestProperty("User-Agent","VideoStreamBR/0.3 (Android)");
    try {
      if(c.getResponseCode()<200||c.getResponseCode()>299)
        throw new IOException("HTTP "+c.getResponseCode());
      try(InputStream input=c.getInputStream()) {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        byte[] part=new byte[8192];int n;
        while((n=input.read(part))>0 && bytes.size()<2000000)bytes.write(part,0,n);
        return new JSONObject(bytes.toString("UTF-8"));
      }
    }finally{c.disconnect();}
  }
  private static Document html(String address)throws Exception {
    return Jsoup.connect(address).userAgent("Mozilla/5.0 (Android; VideoStreamBR/0.3)")
      .timeout(8500).maxBodySize(2000000).followRedirects(true).get();
  }
  public ArrayList<Entry> search(String provider,String query)throws Exception {
    if(!PROVIDERS.containsKey(provider)||!enabled(provider))return new ArrayList<>();
    String q=query==null?"":query.trim();
    if(q.length()>100)q=q.substring(0,100);
    if(provider.equals("archive"))return searchArchive(q);
    if(provider.equals("animefire")) {
      String route=q.isEmpty()?"/em-lancamento":"/pesquisar/"+q.toLowerCase(Locale.ROOT).replaceAll("\\s+","-");
      return searchHtml(provider,"https://animefire.io",route,
        "a[href*='/animes/'], a[href*='/filmes/']");
    }
    String route=q.isEmpty()?"/filmes":"/pesquisar?s="+encode(q);
    return searchHtml(provider,"https://lospobreflix.site",route,
      "a[href*='/filme/'],a[href*='/serie/'],a[href*='/anime/'],a[href*='/dorama/']");
  }
  private ArrayList<Entry> searchArchive(String query)throws Exception {
    String q="mediatype:movies AND licenseurl:* AND (language:por OR language:portuguese OR language:pt)";
    if(!query.isEmpty())q+=" AND title:("+query.replaceAll("[^\\p{L}\\p{N} ]"," ").trim()+")";
    String url="https://archive.org/advancedsearch.php?q="+encode(q)+
      "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=licenseurl&rows=40&page=1&output=json";
    JSONObject response=json(url).optJSONObject("response");
    JSONArray docs=response==null?null:response.optJSONArray("docs");
    ArrayList<Entry> result=new ArrayList<>();
    if(docs==null)return result;
    for(int i=0;i<docs.length();i++){
      JSONObject d=docs.optJSONObject(i);if(d==null)continue;
      String id=d.optString("identifier");
      if(!ID.matcher(id).matches())continue;
      result.add(new Entry("archive",d.optString("title",id),
        "https://archive.org/details/"+id,"https://archive.org/services/img/"+id,
        "Filme / vídeo","Português indicado • licença aberta"));
    }
    return result;
  }
  private ArrayList<Entry> searchHtml(String provider,String base,String route,String selector)throws Exception {
    Document doc=html(base+route);
    ArrayList<Entry> results=new ArrayList<>();
    Set<String> seen=new HashSet<>();
    String host=Uri.parse(base).getHost();
    for(Element anchor:doc.select(selector)){
      String link=anchor.absUrl("href");
      if(!http(link)||!host.equalsIgnoreCase(Uri.parse(link).getHost())||!seen.add(link))continue;
      Element card=anchor.closest("article,.card,.item,.anime-card,.anime-item,.group");
      if(card==null)card=anchor;
      Element heading=card.selectFirst("h2,h3,h4,.animeTitle,.card-title");
      Element image=card.selectFirst("img");
      if(image==null)image=anchor.selectFirst("img");
      String title=heading!=null?heading.text():"";
      if(title.isEmpty())title=anchor.attr("title");
      if(title.isEmpty()&&image!=null)title=image.attr("alt");
      if(title.isEmpty())title=anchor.text();
      if(title.trim().isEmpty())continue;
      String poster="";
      if(image!=null){poster=image.absUrl("data-src");if(poster.isEmpty())poster=image.absUrl("src");}
      String category=provider.equals("animefire")?"Anime":
        link.contains("/serie/")?"Série":link.contains("/anime/")?"Anime":"Filme";
      String hint=title.toLowerCase(Locale.ROOT).contains("dublado")?"Dublado (indicado)":"";
      results.add(new Entry(provider,title.trim(),link,poster,category,hint));
      if(results.size()>=40)break;
    }
    return results;
  }
  public Detail details(Entry entry)throws Exception {
    if(entry.provider.equals("archive"))return archiveDetails(entry);
    return htmlDetails(entry);
  }
  private Detail archiveDetails(Entry entry)throws Exception {
    Detail detail=new Detail();
    String id=entry.url.substring(entry.url.lastIndexOf('/')+1);
    if(!ID.matcher(id).matches()){detail.notice="Identificador inválido";return detail;}
    JSONObject meta=json("https://archive.org/metadata/"+encode(id));
    JSONObject m=meta.optJSONObject("metadata");
    String license=m==null?"":m.optString("licenseurl");
    if(license.isEmpty()){detail.notice="Licença aberta não confirmada";return detail;}
    JSONArray files=meta.optJSONArray("files");
    if(files!=null)for(int i=0;i<files.length();i++){
      JSONObject file=files.optJSONObject(i);if(file==null)continue;
      String name=file.optString("name");
      String[] parts=name.split("/");
      StringBuilder encoded=new StringBuilder();
      for(String part:parts){if(encoded.length()>0)encoded.append('/');encoded.append(encode(part).replace("+","%20"));}
      String url="https://archive.org/download/"+id+"/"+encoded;
      if(video(url))detail.streams.add(new Stream(url,mime(url),name));
      if(detail.streams.size()>=25)break;
    }
    detail.notice="Licença declarada pelo acervo: "+license+". Verifique condições de uso.";
    return detail;
  }
  private Detail htmlDetails(Entry entry)throws Exception {
    Detail d=new Detail();Document doc=html(entry.url);
    Set<String> seen=new HashSet<>();
    for(Element el:doc.select("video[src],video source[src],source[src]")) {
      String link=el.absUrl("src");
      if(video(link)&&seen.add(link))d.streams.add(new Stream(link,mime(link),"Vídeo direto"));
      if(d.streams.size()>=20)break;
    }
    seen.clear();
    for(Element a:doc.select("a[href*='/episodios/'],a[href*='/episodio/'],a[href*='/episode/'],a[href*='temporada']")){
      String link=a.absUrl("href");
      if(!http(link)||!seen.add(link))continue;
      String title=a.text().trim();
      if(title.isEmpty())title=a.attr("title");
      if(title.isEmpty())title="Episódio";
      d.episodes.add(new Entry(entry.provider,title,link,"","Episódio",""));
      if(d.episodes.size()>=150)break;
    }
    d.notice=d.streams.isEmpty()?
      "Nenhum vídeo HTTP direto encontrado no HTML. Players protegidos, anúncios e DRM não são contornados.":
      "Streams diretos identificados; disponibilidade e autorização ainda dependem da fonte.";
    return d;
  }
}