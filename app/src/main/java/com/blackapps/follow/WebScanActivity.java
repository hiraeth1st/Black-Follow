package com.blackapps.follow;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Visible, foreground-only traversal. Scrolls the real page; observes its responses. */
public final class WebScanActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private WebView web;private TextView status;private Button pause;
    private volatile boolean cancelled;
    private boolean ownsLock,paused,inForeground,installed,loadingProfile,ending,polling;
    private String owner,token,script,kind;
    private Store.Account account;private InstagramClient.Profile profile;
    private WebScanData followers,following,current;
    private long start,phaseStart,lastProgress;
    private int generation;
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(!Monitor.BUSY.compareAndSet(false,true)){Toast.makeText(this,"Başka bir kontrol sürüyor.",Toast.LENGTH_LONG).show();finish();return;}
        ownsLock=true;
        owner=Session.owner(this);
        if(Session.blocked(this)||Session.prefs(this).getBoolean("paused",false)||!Session.authenticated(this,owner)) {fail("Önce Instagram oturumunu kontrol et. "+Session.globalStatus(this));return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(13,16,21));
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(12,i.getSystemWindowInsetTop(),12,i.getSystemWindowInsetBottom());return i;});
        status=new TextView(this);status.setTextColor(Color.rgb(184,223,108));status.setPadding(12,12,12,12);root.addView(status);
        TextView note=new TextView(this);note.setText("WEB ÜZERİNDEN TARAMA\nListe yarım ekranlık adımlarla kaydırılır; yeni kişiler yüklenirken beklenir. Bu ekranı açık tut. Arama kutusunu kullanma.");note.setTextColor(Color.LTGRAY);note.setPadding(12,0,12,12);root.addView(note);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {
                if(!r.isForMainFrame())return false;
                Uri u=r.getUrl();boolean allowed="https".equals(u.getScheme())&&"www.instagram.com".equals(u.getHost());
                if(!allowed)return true;
                if(!WebSession.trusted(u.toString())){fail("Instagram yeniden giriş veya doğrulama istiyor. Oturum ekranını kontrol et.");return true;}
                return false;
            }
            public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                if(installed&&!loadingProfile){fail("Instagram listeyi yeni bir sayfada açtı; tarama bağlantısı kesildi. Önceki geçmiş korunuyor. [BF_WEB_NAVIGATION]");return;}
                installed=false;
            }
            public void onPageFinished(WebView v,String url) {
                if(ending||cancelled||!loadingProfile||profile==null)return;
                if(!trustedProfile(url)){fail("Hedef Instagram profili açılamadı; web taraması durdu.");return;}
                install();
            }
            public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail("Instagram sayfası yüklenemedi. İnternet bağlantısını kontrol et.");}
        });
        pause=new Button(this);pause.setText("Duraklat");pause.setOnClickListener(v->{if(ending)return;paused=!paused;lastProgress=System.currentTimeMillis();pause.setText(paused?"Devam et":"Duraklat");if(!paused)schedule();});root.addView(pause);
        Button close=new Button(this);close.setText("İptal / Geri dön");close.setOnClickListener(v->finish());root.addView(close);
        setContentView(root);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status.setText("Profil sayıları kontrol ediliyor…");
        try(InputStream in=getResources().openRawResource(R.raw.scroll_scan);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);script=out.toString("UTF-8");
        }catch(Exception e){fail("Web tarama kodu yüklenemedi.");return;}
        worker.execute(()->{
            try(Store store=new Store(this)) {
                guard();account=store.get(getIntent().getLongExtra("account",0),owner);if(account==null)throw new IOException("Hesap kaydı bulunamadı.");
                start=System.currentTimeMillis();store.profileAttempt(account);
                InstagramClient client=new InstagramClient(this,owner,start+60000);profile=client.readProfile(account);guard();
                if(profile.restricted)throw new IOException("Bu Instagram oturumunun gizli hesaba erişimi yok.");
                followers=new WebScanData(profile.followers);following=new WebScanData(profile.following);store.saveProfile(account,profile);account.remote=profile.id;account.username=profile.username;
                ui(()->begin("followers"));
            }catch(Exception e){error(e);}
        });
    }
    private void guard() throws Exception {
        if(cancelled||isFinishing()||isDestroyed())throw new IOException("Web taraması iptal edildi.");
        if(!owner.equals(Session.owner(this))||!Session.authenticated(this,owner))throw new InstagramClient.AccessError("Instagram oturumu değişti; tarama durduruldu.",true,false);
        if(Session.blocked(this))throw new InstagramClient.AccessError(Session.waitMessage(this),false,true);
    }
    private boolean trustedProfile(String url) {
        try {Uri u=Uri.parse(url);return WebSession.trusted(url)&&"www.instagram.com".equals(u.getHost())&&("/"+profile.username+"/").equalsIgnoreCase(u.getPath());}catch(Exception e){return false;}
    }
    private void begin(String which) {
        if(cancelled||ending)return;
        kind=which;current=kind.equals("followers")?followers:following;generation++;token=UUID.randomUUID().toString();installed=false;
        phaseStart=lastProgress=System.currentTimeMillis();
        if(current.expected==0) {
            try{current.accept(new JSONObject().put("cursor","").put("next","").put("more",false).put("users",new JSONArray()));phaseDone();}catch(Exception e){error(e);}return;
        }
        status.setText(label()+" açılıyor…");loadingProfile=true;web.loadUrl(Session.ORIGIN+"/"+profile.username+"/");
        final int g=generation;handler.postDelayed(()->{if(g==generation&&loadingProfile&&!ending&&!cancelled)fail("Instagram profili 45 saniyede yüklenemedi. [BF_WEB_LOAD]");},45000);
    }
    private String label(){return "followers".equals(kind)?"Takipçiler":"Takip edilenler";}
    private void install() {
        final int g=generation;loadingProfile=false;
        try {
            JSONObject config=new JSONObject().put("id",profile.id).put("username",profile.username).put("kind",kind).put("token",token);
            final Runnable timeout=()->{if(g==generation&&!installed&&!ending&&!cancelled)fail("Web gözlemcisi yanıt vermedi. [BF_WEB_INSTALL]");};handler.postDelayed(timeout,12000);
            web.evaluateJavascript(script.replace("__BF_CONFIG__",config.toString()),value->{
                handler.removeCallbacks(timeout);
                if(g!=generation||cancelled||ending)return;
                if(!"true".equals(value)){fail("Web tarama gözlemcisi başlatılamadı. [BF_WEB_INSTALL]");return;}
                installed=true;lastProgress=System.currentTimeMillis();schedule();
            });
        }catch(Exception e){error(e);}
    }
    private final Runnable tick=this::poll;
    private void schedule(){handler.removeCallbacks(tick);if(!paused&&inForeground&&!ending&&!cancelled)handler.postDelayed(tick,1000);}
    private void poll() {
        if(!installed||polling||paused||!inForeground||ending||cancelled)return;
        final int g=generation;
        try{guard();}catch(Exception e){error(e);return;}
        if(System.currentTimeMillis()-phaseStart>20*60000L){fail("Web taraması süre sınırına ulaştı; geçmiş korunuyor.");return;}
        if(!WebSession.trusted(web.getUrl())){fail("Instagram oturumu web taraması sırasında değişti.");return;}
        polling=true;
        final Runnable timeout=()->{if(g==generation&&polling&&!ending&&!cancelled)fail("Web sayfası yanıt vermedi. [BF_WEB_BRIDGE]");};handler.postDelayed(timeout,12000);
        web.evaluateJavascript("window.__bfScrollScan ? JSON.stringify(window.__bfScrollScan.step(true)) : null",value->{
            handler.removeCallbacks(timeout);polling=false;if(g!=generation||cancelled||ending)return;
            try {
                guard();Object encoded=new JSONTokener(value).nextValue();if(!(encoded instanceof String))throw new IOException("Web tarama bağlantısı kayboldu. [BF_WEB_BRIDGE]");
                JSONObject report=new JSONObject((String)encoded);if(!token.equals(report.getString("token")))throw new IOException("Eski web tarama sonucu reddedildi.");
                JSONArray packets=report.getJSONArray("packets");
                for(int i=0;i<packets.length();i++) {
                    JSONObject packet=packets.getJSONObject(i);String gate=packet.optString("gate");
                    if(!gate.isEmpty()) {
                        long retry=RetryPolicy.serverDelay(packet.optString("retry"),System.currentTimeMillis());
                        throw new InstagramClient.AccessError("Instagram web liste isteğini durdurdu. HTTP "+packet.optInt("status")+" [BF_WEB_ACCESS]",gate.equals("auth")||gate.equals("restricted"),gate.equals("rate"),retry);
                    }
                    current.accept(packet);lastProgress=System.currentTimeMillis();
                }
                String issue=report.optString("error");if(!issue.isEmpty())throw new IOException("Web taraması durdu: "+issue+". Arama kutusunu boş bırak; sayfayı elle değiştirme.");
                if(report.optInt("moved")>0)lastProgress=System.currentTimeMillis();
                String progress=label()+": "+current.people.size()+"/"+current.expected+" kişi • "+current.pages()+" sayfa";
                Monitor.progress=progress;status.setText(progress+(report.optInt("pending")>0?"\nYeni kişilerin yüklenmesi bekleniyor…":"\nYarım ekranlık adımlarla ilerleniyor…"));
                if(report.optBoolean("ended")&&current.terminal()){phaseDone();return;}
                if(System.currentTimeMillis()-lastProgress>30000)throw new IOException("Web listesi 30 saniyedir ilerlemiyor. "+progress+". "+(current.pages()==0?"Desteklenen liste yanıtı yakalanamadı; Instagram sayfa biçimi farklı olabilir.":"Tamamlandı sayılmadı; önceki geçmiş korunuyor.")+" [BF_WEB_STALLED]");
                schedule();
            }catch(Exception e){error(e);}
        });
    }
    private void phaseDone() {
        installed=false;handler.removeCallbacks(tick);
        final String finishedKind=kind;final WebScanData finished=current;
        worker.execute(()->{
            try(Store store=new Store(this)) {
                guard();store.savePreview(account,finishedKind,finished.people,finished.expected);Monitor.REVISION.incrementAndGet();
                if(finishedKind.equals("followers"))ui(()->begin("following"));else finishScan(store);
            }catch(Exception e){error(e);}
        });
    }
    private void finishScan(Store store) throws Exception {
        guard();
        if(!followers.complete()||!following.complete())throw new IOException("Web kaydırmalı tarama eksik kaldı: "+followers.people.size()+"/"+profile.followers+" takipçi, "+following.people.size()+"/"+profile.following+" takip. Alınan listeler önizleme olarak kaydedildi; geçmiş ve bildirimler değişmedi. [BF_WEB_PARTIAL]");
        ui(()->status.setText("İki liste alındı; profil toplamları son kez doğrulanıyor…"));
        InstagramClient.Profile after=new InstagramClient(this,owner,System.currentTimeMillis()+60000).readProfile(account);guard();
        if(!profile.id.equals(after.id)||after.followers!=profile.followers||after.following!=profile.following||after.restricted)throw new IOException("Profil tarama sırasında değişti; tam kayıt yapılmadı.");
        InstagramClient.Snapshot snapshot=new InstagramClient.Snapshot();snapshot.profile=after;snapshot.start=start;snapshot.end=System.currentTimeMillis();snapshot.followers=followers.people;snapshot.following=following.people;
        long before=store.lastEventId(account.id,owner);guard();
        if(!store.commit(account,snapshot,Session.interval(this),true))throw new IOException("Hesap kaydı değişti; tarama kaydedilmedi.");
        Session.dataSucceeded(this);ChangeNotifications.post(this,store,account,before);Monitor.REVISION.incrementAndGet();
        ui(()->result("Web taraması tamamlandı: "+profile.followers+" takipçi, "+profile.following+" takip. İki liste ve geçmiş kaydedildi."));
    }
    private void error(Exception e) {
        if(cancelled)return;
        if(e instanceof InstagramClient.AccessError){InstagramClient.AccessError a=(InstagramClient.AccessError)e;if(a.rate)Session.recordRate(this,a);if(a.auth)Session.pause(this,a.getMessage());}
        String message=e instanceof IOException||e instanceof IllegalArgumentException?e.getMessage():"Web yanıtı işlenemedi; önceki geçmiş korunuyor. [BF_WEB_SCHEMA]";
        ui(()->fail(message));
    }
    private void fail(String message) {
        if(ending||cancelled||isFinishing()||isDestroyed())return;
        ending=true;installed=false;handler.removeCallbacksAndMessages(null);if(web!=null)web.stopLoading();
        final WebScanData partial=current;final String partialKind=kind;
        final LinkedHashMap<String,Store.Edge> captured=partial==null?null:new LinkedHashMap<>(partial.people);
        if(account!=null)worker.execute(()->{try(Store s=new Store(this)){
            if(!cancelled&&owner.equals(Session.owner(this))&&Session.matches(owner)) {
                if(captured!=null&&!captured.isEmpty())s.savePreview(account,partialKind,captured,partial.expected);
                s.status(account.id,owner,message,true,System.currentTimeMillis()+Session.interval(this));Monitor.REVISION.incrementAndGet();
            }
        }catch(Exception ignored){}});
        result(message);
    }
    private void result(String message) {
        ending=true;handler.removeCallbacksAndMessages(null);if(web!=null)web.stopLoading();if(pause!=null)pause.setEnabled(false);
        if(status!=null)status.setText(message);
        new AlertDialog.Builder(this).setTitle("Web tarama sonucu").setMessage(message).setCancelable(false)
            .setNeutralButton("Bilgiyi kopyala",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Black Follow web taraması","Black Follow 0.4.0\n"+message));finish();})
            .setPositiveButton("Hesaba dön",(d,w)->finish()).show();
    }
    private void ui(Runnable r){runOnUiThread(()->{if(!cancelled&&!isFinishing()&&!isDestroyed())r.run();});}
    public void onResume(){super.onResume();inForeground=true;if(web!=null)web.onResume();if(installed)schedule();}
    public void onPause(){inForeground=false;handler.removeCallbacks(tick);if(installed&&!ending){paused=true;if(pause!=null)pause.setText("Devam et");}if(web!=null)web.onPause();super.onPause();}
    public void onDestroy(){
        cancelled=true;generation++;handler.removeCallbacksAndMessages(null);
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        // Release the shared lock only after outstanding guarded database work finishes.
        worker.execute(()->{if(ownsLock){ownsLock=false;Monitor.progress="";Monitor.REVISION.incrementAndGet();Monitor.BUSY.set(false);}});worker.shutdown();super.onDestroy();
    }
}
