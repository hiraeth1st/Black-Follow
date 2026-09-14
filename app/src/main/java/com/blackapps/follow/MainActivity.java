package com.blackapps.follow;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.webkit.CookieManager;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(13,16,21), CARD=Color.rgb(24,29,37), GREEN=Color.rgb(184,243,107), MUTED=Color.rgb(159,173,191);
    private LinearLayout content;private TextView status;private Store store;private AvatarLoader avatars;
    private String pendingExport="",exportOwner="";private boolean exporting=false;private static final int SAVE_REPORT=770;
    private long selected=0;private String tab="followers",query="";private int page=0;private boolean history=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){if(status!=null) status.setText(Monitor.BUSY.get()?(Monitor.progress.isEmpty()?"Instagram bağlantısı kontrol ediliyor…":Monitor.progress):Session.globalStatus(MainActivity.this));handler.postDelayed(this,2000);}};
    public void onCreate(Bundle b) {
        super.onCreate(b);store=new Store(this);avatars=new AvatarLoader(this);
        if(b!=null) {selected=b.getLong("selected");tab=b.getString("tab","followers");page=b.getInt("page");query=b.getString("query","");history=b.getBoolean("history");pendingExport=b.getString("pendingExport","");exportOwner=b.getString("exportOwner","");}
        ChangeNotifications.initialize(this);if(b==null)openNotification(getIntent());MonitorJob.schedule(this);render();
    }
    public void onResume(){super.onResume();ChangeNotifications.initialize(this);if(store!=null) render();handler.post(ticker);}
    public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
    public void onDestroy(){if(store!=null) store.close();if(avatars!=null)avatars.close();super.onDestroy();}
    public void onSaveInstanceState(Bundle b){b.putLong("selected",selected);b.putString("tab",tab);b.putInt("page",page);b.putString("query",query);b.putBoolean("history",history);b.putString("pendingExport",pendingExport);b.putString("exportOwner",exportOwner);super.onSaveInstanceState(b);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openNotification(intent);render();}
    private void openNotification(Intent intent){
        if(intent==null || !intent.hasExtra("notification_account"))return;
        long id=intent.getLongExtra("notification_account",0);String owner=intent.getStringExtra("notification_owner");
        if(Session.owner(this).equals(owner) && store.get(id,owner)!=null){selected=id;tab="events";page=0;query="";ChangeNotifications.cancel(this,id);}
        else {selected=0;toast("Bildirim bu Instagram oturumuna ait değil veya hesap silinmiş.");}
        intent.removeExtra("notification_account");intent.removeExtra("notification_owner");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==ChangeNotifications.PERMISSION_REQUEST){toast(ChangeNotifications.enabled(this)?"Yeni kişiler tespit edildiğinde bildirim gönderilecek.":"Bildirim izni verilmedi; kayıtlar Hareketler sekmesinde tutulmaya devam eder.");render();}}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    private void label(String s,int size,int color){content.addView(text(s,size,color));}
    private void gap(){Space s=new Space(this);content.addView(s,new LinearLayout.LayoutParams(1,dp(12)));}
    private Button button(String value,Runnable action){Button b=new Button(this);b.setText(value);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(GREEN);b.setOnClickListener(v->action.run());return b;}
    private void action(String value,Runnable run){content.addView(button(value,run),new LinearLayout.LayoutParams(-1,dp(52)));}
    private void rowButtons(String left,Runnable a,String right,Runnable b){LinearLayout row=new LinearLayout(this);row.addView(button(left,a),new LinearLayout.LayoutParams(0,dp(54),1));row.addView(button(right,b),new LinearLayout.LayoutParams(0,dp(54),1));content.addView(row);}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(12),dp(16),dp(12));c.setBackground(shape(CARD));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);content.addView(c,lp);return c;}
    private String date(long n){return n==0?"Henüz yok":new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.forLanguageTag("tr-TR")).format(new Date(n));}
    private void render(){
        if(avatars!=null)avatars.clearPage();
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(18),i.getSystemWindowInsetTop(),dp(18),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("BLACK FOLLOW",20,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,dp(60),1));
        Button menu=button("☰",this::drawer);menu.setContentDescription("Geçmiş ve ayarlar menüsü");top.addView(menu,new LinearLayout.LayoutParams(dp(58),dp(52)));root.addView(top);
        status=text(Session.globalStatus(this),12,GREEN);root.addView(status);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(0,dp(12),0,dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(selected>0) detail();else home();
    }
    private void home(){
        label(history?"Hesap geçmişi":"Takipte kal.",30,Color.WHITE);
        label("İsimler, listeler ve zaman içinde tespit edilen değişiklikler.",14,MUTED);gap();
        if(Session.owner(this).isEmpty()) {
            LinearLayout c=card();c.addView(text("Önce Instagram'a bağlan",20,Color.WHITE));c.addView(text("Şifreni Instagram'ın kendi sayfasına girersin. Bu uygulama yalnızca oturumunun erişebildiği listeleri okumayı dener.",14,MUTED));
            action("Instagram'a giriş yap",this::login);
            label("Her kullanıcı kendi Instagram hesabıyla giriş yapar. Kayıtlar kendi telefonunda tutulur; APK paylaşmak hesabını veya geçmişini paylaşmaz.\n\nInstagram'ın erişim verebildiği veriler alınabilir; giriş yapılması liste erişimini garanti etmez.",13,MUTED);return;
        }
        if(!ChangeNotifications.enabled(this)) {label("Yeni takipçi ve takiplerden haberdar olmak için bildirimleri aç.",14,MUTED);action("Bildirimleri aç",()->ChangeNotifications.request(this));}
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(MUTED);input.setHint("Instagram kullanıcı adı");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);content.addView(input);
        action("Hesap ekle • profil bilgilerini getir",()->{
            if(Monitor.BUSY.get()){toast("Mevcut kontrolün bitmesini bekle.");return;}
            String username=input.getText().toString().trim().toLowerCase(Locale.ROOT);if(username.startsWith("@")) username=username.substring(1);
            try {long id=store.add(Session.owner(this),username);selected=id;page=0;query="";tab="followers";render();check(id,true);}
            catch(Exception e){toast("Geçerli bir kullanıcı adı gir; örnek: kullanici.adi");}
        });
        rowButtons("Tümünü kontrol et",()->check(0),"Instagram oturumu",this::login);gap();
        List<Store.Account> accounts=store.accounts(Session.owner(this));Collections.reverse(accounts);
        label("KAYITLI HESAPLAR  ·  "+accounts.size(),12,MUTED);
        if(accounts.isEmpty()) label("Bir hesap eklediğinde burada kalır ve izlemeyi durdurana kadar otomatik kontrol edilir.",15,Color.WHITE);
        for(Store.Account a:accounts){
            LinearLayout c=card();c.addView(text("@"+a.username,20,Color.WHITE));
            c.addView(text((a.followers<0?"—":a.followers)+" takipçi     "+(a.following<0?"—":a.following)+" takip",16,GREEN));
            c.addView(text("Profil sayıları: "+date(a.profileAt),12,MUTED));
            c.addView(text((a.enabled?"İzleme açık":"İzleme duraklatıldı")+" • Son kayıt: "+date(a.lastSuccess),12,MUTED));
            c.addView(text(a.status,12,MUTED));c.setOnClickListener(v->{selected=a.id;page=0;query="";tab="followers";render();});
        }
        gap();label("Veriler bu telefonda saklanır. Saatler cihazın saat dilimindedir. Android pil tasarrufu otomatik kontrolleri geciktirebilir.",12,MUTED);
    }
    private void detail(){
        Store.Account a=store.get(selected,Session.owner(this));if(a==null){selected=0;home();return;}
        action("‹ Hesap geçmişine dön",()->{selected=0;history=true;render();});
        label("@"+a.username,28,Color.WHITE);if(!a.title.isEmpty()) label(a.title,14,MUTED);
        LinearLayout summary=card();summary.addView(text((a.followers<0?"—":a.followers)+" takipçi      "+(a.following<0?"—":a.following)+" takip",22,GREEN));
        summary.addView(text("Profil sayılarının zamanı: "+date(a.profileAt),12,MUTED));
        summary.addView(text("Kişi listelerinin zamanı: "+date(a.lastSuccess),12,MUTED));summary.addView(text("Son liste denemesi: "+date(a.lastAttempt),12,MUTED));summary.addView(text(a.status,13,Color.WHITE));
        if(a.profileAt==0) label("Profil henüz alınamadı. Çizgi işareti sıfır kişi anlamına gelmez.",13,MUTED);
        if(Session.blocked(this)) label("Profil ve listeler Instagram erişim engeli nedeniyle yenilenemiyor. Bekleme bitene kadar yeni veri istenmez. Önceki bilgiler varsa tarihleriyle gösterilir.",13,MUTED);
        action("Listeyi şimdi yenile",()->check(a.id));
        rowButtons("Sayıları yenile",()->check(a.id,true),"Dışa aktar (.txt)",this::exportReport);
        action(a.enabled?"İzlemeyi durdur":"İzlemeyi sürdür",()->{store.enabled(a.id,a.owner,!a.enabled);render();});
        label("Yeni kayıtlardaki saat tespit zamanıdır. Aralık, önceki taramanın başlangıcı ile yeni taramanın bitişini gösterir; kesin takip saati değildir.",12,MUTED);gap();
        LinearLayout tabs=new LinearLayout(this);
        String[] kinds={"followers","following","events"},names={"Takipçiler","Takip edilen","Hareketler"};
        for(int i=0;i<3;i++){String k=kinds[i];Button b=button(names[i],()->{tab=k;page=0;query="";render();});if(tab.equals(k)) b.setBackground(shape(CARD));tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}content.addView(tabs);
        if(a.lastSuccess==0){label("Henüz tam liste alınmadı. İlk başarılı kontrolden sonra mevcut kişiler “Zaman bilinmiyor” olarak görünecek.",15,MUTED);return;}
        if(!tab.equals("events")) {
            EditText search=new EditText(this);search.setTextColor(Color.WHITE);search.setHintTextColor(MUTED);search.setHint("Listede isim ara");search.setSingleLine(true);search.setText(query);content.addView(search);
            action("Listede ara",()->{query=search.getText().toString().trim();page=0;render();});
        } else label("“Listeden çıktı” kaydı, takipten çıkma, engelleme veya hesabın kapanması gibi farklı nedenlerle oluşabilir. İki kontrol arasındaki kısa süreli değişiklikler kaçırılabilir.",12,MUTED);
        int count=0;
        try(Cursor c=tab.equals("events")?store.events(a.id,a.owner,page*100):store.edges(a.id,a.owner,tab,query,page*100)){
            while(c.moveToNext()) {
                count++;LinearLayout item=card();
                if(tab.equals("events")){
                    boolean followers=c.getString(0).equals("followers"),added=c.getString(1).equals("added");
                    item.addView(text(HistoryText.event(c.getString(0),c.getString(1),c.getString(2),c.getInt(6),c.getLong(7),TimeZone.getDefault()),16,added?GREEN:Color.WHITE));
                    final String username=c.getString(2);item.setOnClickListener(v->openProfile(username));
                    item.addView(text("Tespit: "+date(c.getLong(5))+"\nAralık: "+date(c.getLong(4))+" → "+date(c.getLong(5)),12,MUTED));
                } else {
                    final String username=c.getString(0);
                    LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.CENTER_VERTICAL);
                    ImageView picture=new ImageView(this);picture.setImageResource(android.R.drawable.ic_menu_myplaces);picture.setBackground(shape(BG));picture.setClipToOutline(true);picture.setScaleType(ImageView.ScaleType.CENTER_CROP);picture.setContentDescription("@"+username+" profil fotoğrafı; alınamazsa yer tutucu gösterilir");
                    LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(48),dp(48));imageParams.rightMargin=dp(12);identity.addView(picture,imageParams);
                    LinearLayout personText=column();personText.addView(text("@"+username,17,Color.WHITE));if(!c.getString(1).isEmpty())personText.addView(text(c.getString(1),13,MUTED));identity.addView(personText,new LinearLayout.LayoutParams(0,-2,1));item.addView(identity);
                    avatars.load(picture,c.getString(4),a.owner);item.setOnClickListener(v->openProfile(username));item.setContentDescription("@"+username+" Instagram profilini aç");
                    item.addView(text(c.getLong(2)==0?"Zaman bilinmiyor · ilk kayıtta mevcut":"Tespit: "+date(c.getLong(2))+"\nAralık: "+date(c.getLong(3))+" → "+date(c.getLong(2)),12,c.getLong(2)==0?MUTED:GREEN));
                }
            }
        }
        if(count==0) label(tab.equals("events")?"Bu sayfada değişiklik kaydı yok.":"Bu sayfada kişi bulunamadı.",15,MUTED);
        label("Sayfa "+(page+1)+" • sayfa başına 100 kayıt. Kişiye dokunarak Instagram profilini açabilirsin.",12,MUTED);
        LinearLayout pages=new LinearLayout(this);if(page>0)pages.addView(button("‹ Önceki",()->{page--;render();}));if(count==100)pages.addView(button("Sonraki ›",()->{page++;render();}));content.addView(pages);
    }
    private void check(long id){
        check(id,false);
    }
    private void check(long id,boolean profileOnly){
        if(Monitor.BUSY.get()){toast("Kontrol zaten sürüyor.");return;}
        Context app=getApplicationContext();toast("Kontrol başlatılıyor…");
        new Thread(()->{String result=profileOnly?Monitor.profile(app,id):Monitor.run(app,id,true);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;render();new AlertDialog.Builder(this).setTitle("Kontrol sonucu").setMessage(result)
            .setNeutralButton("Bilgiyi kopyala",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Black Follow kontrol","Black Follow 0.2.2\n"+result));toast("Kontrol bilgisi kopyalandı");})
            .setPositiveButton("Tamam",null).show();});},"manual-check").start();
    }
    private void login(){if(Monitor.BUSY.get()){toast("Mevcut kontrolün bitmesini bekle.");return;}startActivity(new Intent(this,LoginActivity.class));}
    private void drawer(){
        Dialog dialog=new Dialog(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(32),dp(22),dp(24));panel.setBackgroundColor(CARD);
        panel.addView(text("BLACK FOLLOW",22,GREEN));panel.addView(text("0.2.2 • Erişim testi",13,MUTED));
        panel.addView(button("Hesap geçmişi",()->{dialog.dismiss();selected=0;history=true;render();}));
        panel.addView(button("Instagram oturumu",()->{dialog.dismiss();login();}));
        panel.addView(button("Kontrol ayarları",()->{dialog.dismiss();settings();}));
        panel.addView(button(ChangeNotifications.enabled(this)?"Bildirim ayarları":"Bildirimleri aç",()->{dialog.dismiss();ChangeNotifications.request(this);}));
        panel.addView(button("Son hata bilgisini kopyala",()->{dialog.dismiss();String detail=Session.prefs(this).getString("last_error_detail","Bu sürümde henüz istek hatası kaydedilmedi.");android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Black Follow tanı","Black Follow 0.2.2\n"+Session.globalStatus(this)+"\n"+detail));toast("Hata bilgisi kopyalandı");}));
        if(selected>0)panel.addView(button("Bu hesabı dışa aktar (.txt)",()->{dialog.dismiss();exportReport();}));
        if(selected>0) panel.addView(button("Bu hesabın kayıtlarını sil",()->{dialog.dismiss();remove();}));
        panel.addView(button("Instagram'dan çıkış",()->{dialog.dismiss();logout();}));
        panel.addView(text("İlk listeler: zaman bilinmiyor.\nSonraki değişiklikler: tespit zamanı.\n\nInstagram'a erişim sağlanamadığında eski kayıtlar korunur.",14,MUTED));
        dialog.setContentView(panel);Window w=dialog.getWindow();if(w!=null){w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.86),-1);w.setGravity(Gravity.START);w.setBackgroundDrawableResource(android.R.color.transparent);}dialog.show();if(w!=null)w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.86),-1);
    }
    private void settings(){
        String[] options={"6 saatte bir","12 saatte bir","24 saatte bir","Otomatik kontrolü kapat"};
        new AlertDialog.Builder(this).setTitle("Arka plan kontrolü").setItems(options,(d,i)->{Session.prefs(this).edit().putBoolean("automatic",i<3).putInt("hours",i==0?6:i==1?12:24).apply();MonitorJob.schedule(this);render();}).setNegativeButton("Kapat",null).show();
    }
    private void remove(){
        long id=selected;new AlertDialog.Builder(this).setTitle("Bu hesabın geçmişi silinsin mi?").setMessage("Takipçi, takip ve hareket kayıtları bu telefondan silinecek; otomatik izleme duracak.").setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{if(Monitor.BUSY.get()){toast("Kontrol bitince tekrar dene.");return;}store.delete(id,Session.owner(this));ChangeNotifications.cancel(this,id);selected=0;render();}).show();
    }
    private void logout(){
        if(Monitor.BUSY.get()){toast("Önce mevcut kontrolün bitmesini bekle.");return;}
        new AlertDialog.Builder(this).setTitle("Oturumu kapat?").setMessage("Otomatik kontroller duracak. Kayıtların saklanacak ve aynı Instagram hesabıyla yeniden giriş yaptığında görünecek.").setNegativeButton("Vazgeç",null).setPositiveButton("Çıkış",(d,w)->{
            ChangeNotifications.clear(this);Session.pause(this,"Oturum kapalı");Session.prefs(this).edit().remove("owner").remove("viewer_name").apply();
            CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();selected=0;render();});
            android.webkit.WebStorage.getInstance().deleteAllData();
        }).show();
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private void openProfile(String username){
        try {startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(ProfileLinks.profile(username))));}
        catch(Exception e){toast("Profili açacak Instagram veya tarayıcı uygulaması bulunamadı.");}
    }
    private void exportReport(){
        if(exporting||!pendingExport.isEmpty()){toast("Önce açık olan dışa aktarmayı tamamla.");return;}
        final long account=selected;final String owner=Session.owner(this);Store.Account a=store.get(account,owner);
        if(a==null){toast("Önce dışa aktarılacak hesabı aç.");return;}
        final String name="Black-Follow-"+a.username+"-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".txt";
        final Context app=getApplicationContext();exporting=true;toast("Tüm tarihçe hazırlanıyor…");
        new Thread(()->{
            java.io.File file=null;
            try {
                java.io.File dir=new java.io.File(app.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException();
                file=java.io.File.createTempFile("bf-export-",".txt",dir);
                try(Store db=new Store(app);java.io.Writer out=new java.io.OutputStreamWriter(new java.io.FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8)){db.export(account,owner,out);}
                final java.io.File ready=file;
                runOnUiThread(()->{
                    exporting=false;if(isFinishing()||isDestroyed()||!owner.equals(Session.owner(this))){ready.delete();return;}
                    pendingExport=ready.getAbsolutePath();exportOwner=owner;
                    try {Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("text/plain");save.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(save,SAVE_REPORT);}
                    catch(Exception e){ready.delete();pendingExport="";exportOwner="";toast("Dosya kaydetme ekranı açılamadı.");}
                });
            }catch(Exception e){if(file!=null)file.delete();runOnUiThread(()->{exporting=false;if(!isFinishing()&&!isDestroyed())toast("Dışa aktarma hazırlanamadı; tekrar dene.");});}
        },"history-export").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=SAVE_REPORT)return;
        String path=pendingExport,owner=exportOwner;pendingExport="";exportOwner="";
        java.io.File file=new java.io.File(path);
        try {if(path.isEmpty()||!file.getCanonicalFile().getParentFile().equals(new java.io.File(getCacheDir(),"exports").getCanonicalFile())||!file.getName().startsWith("bf-export-"))return;}catch(java.io.IOException e){return;}
        if(result!=RESULT_OK||data==null||data.getData()==null){file.delete();return;}
        if(!owner.equals(Session.owner(this))){file.delete();toast("Oturum değişti; dışa aktarma iptal edildi.");return;}
        final android.net.Uri destination=data.getData();final Context app=getApplicationContext();exporting=true;
        new Thread(()->{
            boolean success=false;
            try(java.io.InputStream in=new java.io.FileInputStream(file);java.io.OutputStream out=app.getContentResolver().openOutputStream(destination,"wt")){
                if(out==null||!owner.equals(Session.owner(app)))throw new java.io.IOException();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();success=true;
            }catch(Exception e){success=false;}finally{file.delete();}
            final boolean saved=success;runOnUiThread(()->{exporting=false;if(!isFinishing()&&!isDestroyed())toast(saved?"Tarihçe TXT olarak kaydedildi.":"Kaydetme tamamlanamadı; hedef dosyayı kontrol et.");});
        },"save-history-export").start();
    }
    @Override public void onBackPressed(){if(selected>0){selected=0;render();}else super.onBackPressed();}
}
