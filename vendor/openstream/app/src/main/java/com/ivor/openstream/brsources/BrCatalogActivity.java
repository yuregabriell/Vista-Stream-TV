package com.ivor.openstream.brsources;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Catalog discovery UI shared by VS and Harvester, with explicit source opt-in. */
public final class BrCatalogActivity extends Activity {
  private final int BG=0xff101319, CARD=0xff222733, TEXT=0xffFFFFFF, MUTED=0xffBFC4CE, ACCENT=0xff7255D8;
  private BrCatalogEngine engine;
  private EditText search;
  private TextView status;
  private LinearLayout cards;
  private Spinner filter;
  private final ExecutorService executor=Executors.newFixedThreadPool(3);
  private final ArrayList<BrCatalogEngine.Entry> found=new ArrayList<>();
  private boolean running=false;
  private int generation=0;
  private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable surface(int color){
    GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(14));
    return drawable;
  }
  private TextView text(String s,int size,int color){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
  }
  private Button button(String s){
    Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);
    b.setBackground(surface(ACCENT));return b;
  }
  private boolean vista(){
    return getPackageName().startsWith("com.vistastream.tv");
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);engine=new BrCatalogEngine(this);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);root.setPadding(dp(14),dp(12),dp(14),dp(8));
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
    Button back=button("←");header.addView(back,new LinearLayout.LayoutParams(dp(50),dp(46)));
    back.setOnClickListener(v->finish());
    TextView title=text("Catálogo BR • Fontes",21,TEXT);
    title.setTypeface(null,1);title.setPadding(dp(12),0,0,0);
    header.addView(title,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
    TextView introduction=text("Pesquisa em fontes ativadas. O áudio PT-BR depende dos ficheiros encontrados; players protegidos não são extraídos.",12,MUTED);
    introduction.setPadding(0,dp(5),0,dp(12));root.addView(introduction);

    TextView sourceLabel=text("FONTES ATIVAS",12,MUTED);root.addView(sourceLabel);
    for(Map.Entry<String,String> e:BrCatalogEngine.PROVIDERS.entrySet()){
      CheckBox check=new CheckBox(this);check.setText(e.getValue());check.setTextColor(TEXT);
      check.setTextSize(14);check.setChecked(engine.enabled(e.getKey()));root.addView(check);
      check.setOnCheckedChangeListener((v,enabled)->engine.setEnabled(e.getKey(),enabled));
    }
    LinearLayout searchRow=new LinearLayout(this);
    search=new EditText(this);search.setSingleLine(true);search.setTextColor(TEXT);
    search.setHintTextColor(MUTED);search.setHint("Filme, série, anime ou palavra-chave");
    search.setText("Brasil");search.setTextSize(14);
    search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
    searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(54),1));
    Button submit=button("Pesquisar");searchRow.addView(submit,new LinearLayout.LayoutParams(dp(115),dp(52)));
    root.addView(searchRow);
    submit.setOnClickListener(v->runSearch());
    search.setOnEditorActionListener((v,action,event)->{
      if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_DONE){
        runSearch();return true;
      }return false;
    });

    filter=new Spinner(this);String[] options={"Tudo","Filmes","Séries","Animes"};
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
    filter.setAdapter(adapter);root.addView(filter);
    filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
      public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){render();}
    });
    status=text("Fontes opt-in: AnimeFire e PobreFlix começam desativadas. Ativa apenas fontes a que tens direito de acesso.",12,MUTED);
    status.setPadding(0,dp(8),0,dp(8));root.addView(status);
    ScrollView scroll=new ScrollView(this);cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(cards);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(root);
    runSearch();
  }

  private void runSearch(){
    if(running)return;
    final int request=++generation;
    found.clear();render();
    ArrayList<String> enabled=new ArrayList<>();
    for(String key:BrCatalogEngine.PROVIDERS.keySet())if(engine.enabled(key))enabled.add(key);
    if(enabled.isEmpty()){status.setText("Ativa pelo menos uma fonte.");return;}
    running=true;status.setText("A pesquisar em "+enabled.size()+" fonte(s)...");
    final String query=search.getText().toString().trim();
    final java.util.concurrent.atomic.AtomicInteger remaining=new java.util.concurrent.atomic.AtomicInteger(enabled.size());
    final ArrayList<String> errors=new ArrayList<>();
    for(String provider:enabled){
      executor.execute(()->{
        ArrayList<BrCatalogEngine.Entry> entries=new ArrayList<>();
        try{entries=engine.search(provider,query);}
        catch(Exception ex){synchronized(errors){errors.add(BrCatalogEngine.PROVIDERS.get(provider)+": "+ex.getClass().getSimpleName());}}
        final ArrayList<BrCatalogEngine.Entry> results=entries;
        runOnUiThread(()->{
          if(isFinishing()||request!=generation)return;
          HashSet<String> seen=new HashSet<>();
          for(BrCatalogEngine.Entry item:found)seen.add(item.provider+"|"+item.url);
          for(BrCatalogEngine.Entry item:results)if(seen.add(item.provider+"|"+item.url))found.add(item);
          int count=remaining.decrementAndGet();
          if(count==0){
            running=false;
            status.setText(found.size()+" títulos encontrados."+
              (errors.isEmpty()?"":" Algumas fontes falharam: "+android.text.TextUtils.join("; ",errors))+
              " Resultados não garantem streams reproduzíveis ou direitos de utilização.");
          }else status.setText(found.size()+" títulos encontrados • "+count+" fonte(s) pendentes...");
          render();
        });
      });
    }
  }
  private void render(){
    if(cards==null)return;
    cards.removeAllViews();int shown=0;
    String chosen=filter==null?"Tudo":String.valueOf(filter.getSelectedItem());
    for(BrCatalogEngine.Entry entry:found){
      if(chosen.equals("Filmes") && !(entry.type.contains("Filme")||entry.type.contains("vídeo")))continue;
      if(chosen.equals("Séries")&&!entry.type.contains("Série"))continue;
      if(chosen.equals("Animes")&&!entry.type.contains("Anime"))continue;
      LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(surface(CARD));
      LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
      cp.setMargins(0,0,0,dp(11));cards.addView(card,cp);
      TextView title=text(entry.title,17,TEXT);title.setTypeface(null,1);card.addView(title);
      card.addView(text(BrCatalogEngine.PROVIDERS.get(entry.provider)+" • "+entry.type+
        (entry.hint.isEmpty()?"":" • "+entry.hint),12,MUTED));
      Button details=button("Ver fontes e episódios");
      LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(46));ap.topMargin=dp(8);
      card.addView(details,ap);details.setOnClickListener(v->inspect(entry));
      shown++;if(shown>=90)break;
    }
    if(shown==0)cards.addView(text("Sem resultados nesta categoria. Experimenta outra pesquisa ou fonte.",14,MUTED));
  }
  private void inspect(BrCatalogEngine.Entry item){
    status.setText("A carregar detalhes de "+item.title+"...");
    executor.execute(()->{
      BrCatalogEngine.Detail detail=null;String error=null;
      try{detail=engine.details(item);}catch(Exception e){error=e.getMessage();}
      final BrCatalogEngine.Detail result=detail;
      final String failure=error;
      runOnUiThread(()->{
        if(isFinishing())return;
        status.setText(failure==null?"Detalhes consultados.":"Erro na consulta: "+failure);
        if(result==null){
          new AlertDialog.Builder(this).setTitle("Fonte indisponível")
            .setMessage("Não foi possível ler esta página.").setPositiveButton("OK",null).show();
        }else showDetails(item,result);
      });
    });
  }
  private void showDetails(BrCatalogEngine.Entry item,BrCatalogEngine.Detail detail){
    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(16),dp(8),dp(16),dp(8));
    TextView notice=text(detail.notice,13,Color.DKGRAY);body.addView(notice);
    if(detail.streams.isEmpty())body.addView(text("Nenhum link direto disponível para o player.",14,Color.DKGRAY));
    for(BrCatalogEngine.Stream stream:detail.streams){
      Button play=button("▶ "+stream.label);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));p.topMargin=dp(7);
      body.addView(play,p);play.setOnClickListener(v->play(item,stream));
    }
    if(!detail.episodes.isEmpty()){
      TextView episodes=text("EPISÓDIOS ("+detail.episodes.size()+")",14,Color.DKGRAY);
      episodes.setPadding(0,dp(16),0,dp(6));body.addView(episodes);
    }
    int n=0;
    for(BrCatalogEngine.Entry episode:detail.episodes){
      if(n++>=40){body.addView(text("Lista limitada aos primeiros 40 episódios.",12,Color.DKGRAY));break;}
      Button open=button(episode.title);body.addView(open,new LinearLayout.LayoutParams(-1,dp(47)));
      open.setOnClickListener(v->inspect(episode));
    }
    ScrollView scroll=new ScrollView(this);scroll.addView(body);
    new AlertDialog.Builder(this).setTitle(item.title)
      .setView(scroll).setPositiveButton("Fechar",null)
      .setNeutralButton("Ver página de origem",(d,w)->{
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.url)));}catch(Exception ignored){}
      }).show();
  }
  private void play(BrCatalogEngine.Entry item,BrCatalogEngine.Stream stream){
    if(vista()){
      Intent i=new Intent("com.vistastream.tv.action.PLAY");
      i.setClassName(getPackageName(),"com.ivor.openstream.harvester.HarvesterPlaybackActivity");
      i.putExtra("com.vistastream.tv.extra.URL",stream.url);
      i.putExtra("com.vistastream.tv.extra.TITLE",item.title);
      i.putExtra("com.vistastream.tv.extra.MIME_TYPE",stream.mime);
      i.putExtra("com.vistastream.tv.extra.PAGE_URL",item.url);
      try{startActivity(i);}catch(Exception ex){status.setText("Não foi possível iniciar o player do VS: "+ex.getMessage());}
    }else {
      HarvesterVistaBridge.open(this,stream.url,item.title,
        stream.mime.contains("mpegurl")?"HLS":stream.mime.contains("dash")?"DASH":
          stream.mime.contains("webm")?"WEBM":"MP4",item.url);
    }
  }
  @Override protected void onDestroy(){generation++;executor.shutdownNow();super.onDestroy();}
}