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
    private ScrollView scroll;private long renderedRevision;private int eventFilter=0;
    private boolean usePreview=true;
    private boolean profilePage=false;private String uiOwner="";
    private long searchSelected=0;private String searchTab="followers",searchQuery="";private int searchPage=0,searchFilter=0;
    private LinearLayout content;private TextView status;private Store store;private AvatarLoader avatars;
    private String pendingExport="",exportOwner="";private boolean exporting=false;private static final int SAVE_REPORT=770;
    private long selected=0;private String tab="followers",query="";private int page=0;private boolean history=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){
        boolean changed=renderedRevision!=Monitor.REVISION.get();
        if(selected==0 && !Monitor.BUSY.get() && changed && !(getCurrentFocus() instanceof EditText)) refreshDisplayedData();
        if(status!=null) status.setText(Monitor.BUSY.get()?(Monitor.progress.isEmpty()?"Instagram bağlantısı kontrol ediliyor…":Monitor.progress):Session.globalStatus(MainActivity.this)+(renderedRevision!=Monitor.REVISION.get()?"\nYeni kayıtlar var • görmek için dokun.":""));
        handler.postDelayed(this,2000);
    }};
    private void refreshDisplayedData() {
        int y=scroll==null?0:scroll.getScrollY();render();
        final ScrollView refreshed=scroll;refreshed.post(()->refreshed.scrollTo(0,y));
    }
    public void onCreate(Bundle b) {
        super.onCreate(b);store=new Store(this);avatars=new AvatarLoader(this);
        if(b!=null) {usePreview=b.getBoolean("usePreview",true);eventFilter=Math.max(0,Math.min(4,b.getInt("eventFilter",0)));selected=b.getLong("selected");tab=b.getString("tab","followers");page=b.getInt("page");query=b.getString("query","");history=b.getBoolean("history");pendingExport=b.getString("pendingExport","");exportOwner=b.getString("exportOwner","");}
        if(b!=null){profilePage=b.getBoolean("profilePage");uiOwner=b.getString("uiOwner","");searchSelected=b.getLong("searchSelected");searchTab=b.getString("searchTab","followers");searchQuery=b.getString("searchQuery","");searchPage=b.getInt("searchPage");searchFilter=Math.max(0,Math.min(4,b.getInt("searchFilter")));}
        else uiOwner=Session.owner(this);
        resetChangedOwner();
        ChangeNotifications.initialize(this);if(b==null)openNotification(getIntent());MonitorJob.schedule(this);render();
    }
    public void onResume(){super.onResume();resetChangedOwner();ChangeNotifications.initialize(this);if(store!=null) render();handler.post(ticker);}
    public void onPause(){handler.removeCallbacks(ticker);super.onPause();}
    public void onDestroy(){if(store!=null) store.close();if(avatars!=null)avatars.close();super.onDestroy();}
    public void onSaveInstanceState(Bundle b){b.putBoolean("usePreview",usePreview);b.putBoolean("profilePage",profilePage);b.putString("uiOwner",uiOwner);b.putLong("searchSelected",searchSelected);b.putString("searchTab",searchTab);b.putString("searchQuery",searchQuery);b.putInt("searchPage",searchPage);b.putInt("searchFilter",searchFilter);b.putInt("eventFilter",eventFilter);b.putLong("selected",selected);b.putString("tab",tab);b.putInt("page",page);b.putString("query",query);b.putBoolean("history",history);b.putString("pendingExport",pendingExport);b.putString("exportOwner",exportOwner);super.onSaveInstanceState(b);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openNotification(intent);render();}
    private void openNotification(Intent intent){
        if(intent==null || !intent.hasExtra("notification_account"))return;
        long id=intent.getLongExtra("notification_account",0);String owner=intent.getStringExtra("notification_owner");
        if(Session.owner(this).equals(owner) && store.get(id,owner)!=null){
            Store.Account a=store.get(id,owner);boolean own=owner.equals(a.remote);
            if(own&&!profilePage)saveSearchPage();profilePage=own;
            selected=id;tab="events";page=0;query="";eventFilter=own&&intent.getBooleanExtra("notification_departures",false)?4:0;ChangeNotifications.cancel(this,id);
        }
        else {selected=0;toast("Bildirim bu Instagram oturumuna ait değil veya hesap silinmiş.");}
        intent.removeExtra("notification_departures");intent.removeExtra("notification_account");intent.removeExtra("notification_owner");
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
        renderedRevision=Monitor.REVISION.get();
        if(avatars!=null)avatars.clearPage();
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),0,dp(18),0);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(18),i.getSystemWindowInsetTop(),dp(18),i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("BLACK FOLLOW",20,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,dp(60),1));
        Button menu=button("☰",this::drawer);menu.setContentDescription("Geçmiş ve ayarlar menüsü");top.addView(menu,new LinearLayout.LayoutParams(dp(58),dp(52)));root.addView(top);
        status=text(Session.globalStatus(this),12,GREEN);status.setOnClickListener(v->{if(!Monitor.BUSY.get() && renderedRevision!=Monitor.REVISION.get())refreshDisplayedData();});root.addView(status);
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(0,dp(12),0,dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(profilePage && selected==0) ownWelcome();else if(selected>0) detail();else home();
        bottomNavigation(root);
    }
    private void resetChangedOwner(){
        String owner=Session.owner(this);if(owner.equals(uiOwner))return;
        uiOwner=owner;profilePage=false;selected=0;searchSelected=0;tab=searchTab="followers";query=searchQuery="";page=searchPage=eventFilter=searchFilter=0;
    }
    private void saveSearchPage(){searchSelected=selected;searchTab=tab;searchQuery=query;searchPage=page;searchFilter=eventFilter;}
    private void showSearch(){
        if(!profilePage)return;profilePage=false;selected=searchSelected;tab=searchTab;query=searchQuery;page=searchPage;eventFilter=searchFilter;render();
    }
    private void showOwn(){
        if(profilePage)return;saveSearchPage();profilePage=true;selected=0;usePreview=true;tab="followers";query="";page=eventFilter=0;loadOwn();
    }
    private void loadOwn(){
        String owner=Session.owner(this);
        if(owner.isEmpty()){render();return;}
        Store.Account a=store.self(owner);
        if(a==null && !Monitor.BUSY.get())try{selected=store.ensureSelf(owner,Session.prefs(this).getString("viewer_name",""));a=store.get(selected,owner);}catch(Exception e){toast("Profil kaydı hazırlanamadı. Instagram oturumunu kontrol et.");}
        else if(a!=null)selected=a.id;
        render();
        if(a!=null && a.profileAttempt==0 && a.enabled && !Monitor.BUSY.get())check(a.id);
    }
    private void ownWelcome(){
        label("Profilim",30,Color.WHITE);
        label("Kendi takipçi ve takip listelerin; yeni gelenler ve listenden çıkanlar burada.",15,MUTED);
        if(Session.owner(this).isEmpty())action("Instagram'a giriş yap",this::login);
        else action("Profilimi aç",this::loadOwn);
    }
    private void bottomNavigation(LinearLayout root){
        View divider=new View(this);divider.setBackgroundColor(CARD);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout bar=new LinearLayout(this);bar.setPadding(0,dp(6),0,dp(6));
        bar.addView(navItem("Ara",R.drawable.ic_search,!profilePage,this::showSearch),new LinearLayout.LayoutParams(0,dp(62),1));
        bar.addView(navItem("Profilim",R.drawable.ic_profile,profilePage,this::showOwn),new LinearLayout.LayoutParams(0,dp(62),1));root.addView(bar);
    }
    private View navItem(String name,int icon,boolean active,Runnable action){
        LinearLayout item=column();item.setGravity(Gravity.CENTER);if(active)item.setBackground(shape(CARD));
        ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(active?GREEN:MUTED);item.addView(image,new LinearLayout.LayoutParams(dp(27),dp(27)));
        TextView label=text(name,11,active?GREEN:MUTED);label.setGravity(Gravity.CENTER);item.addView(label);
        item.setContentDescription(name);item.setSelected(active);item.setFocusable(true);item.setClickable(true);item.setOnClickListener(v->action.run());return item;
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
            c.addView(text(Session.displayStatus(this,a.status),12,MUTED));c.setOnClickListener(v->{selected=a.id;page=0;query="";usePreview=true;tab="followers";render();});
        }
        gap();label("Veriler bu telefonda saklanır. Saatler cihazın saat dilimindedir. Android pil tasarrufu otomatik kontrolleri geciktirebilir.",12,MUTED);
    }
    private void detail(){
        Store.Account a=store.get(selected,Session.owner(this));if(a==null){selected=0;if(profilePage)ownWelcome();else home();return;}
        boolean own=profilePage && a.owner.equals(a.remote);
        if(!own)action("‹ Hesap geçmişine dön",()->{selected=0;history=true;render();});
        else {label("Profilim",16,GREEN);action("Instagram profilimi aç",()->openProfile(a.username));}
        label("@"+a.username,28,Color.WHITE);if(!a.title.isEmpty()) label(a.title,14,MUTED);
        LinearLayout summary=card();summary.addView(text((a.followers<0?"—":a.followers)+" takipçi      "+(a.following<0?"—":a.following)+" takip",22,GREEN));
        summary.addView(text("Profil sayılarının zamanı: "+date(a.profileAt),12,MUTED));
        summary.addView(text("Kişi listelerinin zamanı: "+date(a.lastSuccess),12,MUTED));summary.addView(text("Son liste denemesi: "+date(a.lastAttempt),12,MUTED));summary.addView(text(Session.displayStatus(this,a.status),13,Color.WHITE));
        if(a.profileAt==0) label("Profil henüz alınamadı. Çizgi işareti sıfır kişi anlamına gelmez.",13,MUTED);
        if(Session.blocked(this)) label("Profil ve listeler Instagram erişim engeli nedeniyle yenilenemiyor. Bekleme bitene kadar yeni veri istenmez. Önceki bilgiler varsa tarihleriyle gösterilir.",13,MUTED);
        if(own){
            action("Beni takipten çıkanlar",()->{tab="events";eventFilter=4;query="";page=0;render();});
            label("İlk tam taramadan sonra takipçi listenden çıkanlar burada birikir. Tespit edilen çıkış, engelleme veya hesabın kapanmasından da kaynaklanabilir.",12,MUTED);
            if(!ChangeNotifications.enabled(this))action("Değişiklik bildirimlerini aç",()->ChangeNotifications.request(this));
        }
        action("Listeyi şimdi yenile",()->check(a.id));
        action("Web üzerinden kaydırarak tara",()->{if(Monitor.BUSY.get()){toast("Mevcut kontrolün bitmesini bekle.");return;}startActivity(new Intent(this,WebScanActivity.class).putExtra("account",a.id));});
        rowButtons("Sayıları yenile",()->check(a.id,true),"Dışa aktar (.txt)",this::exportReport);
        action(a.enabled?"İzlemeyi durdur":"İzlemeyi sürdür",()->{store.enabled(a.id,a.owner,!a.enabled);render();});
        label("Yeni kayıtlardaki saat tespit zamanıdır. Aralık, önceki taramanın başlangıcı ile yeni taramanın bitişini gösterir; kesin takip saati değildir.",12,MUTED);gap();
        LinearLayout tabs=new LinearLayout(this);
        String[] kinds={"followers","following","events"},names={"Takipçiler","Takip edilen","Hareketler"};
        for(int i=0;i<3;i++){String k=kinds[i];Button b=button(names[i],()->{tab=k;page=0;query="";usePreview=true;render();});if(tab.equals(k)) b.setBackground(shape(CARD));tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}content.addView(tabs);
        Store.Preview preview=tab.equals("events")?null:store.preview(a.id,a.owner,tab);
        boolean showingPreview=preview!=null&&(usePreview||a.lastSuccess==0);
        if(preview!=null){
            label("Son tarama: "+preview.received+"/"+preview.expected+" kişi • "+(preview.received<preview.expected?"Eksik liste":"Tarama önizlemesi")+"\nAlınma: "+date(preview.observed),15,GREEN);
            label("Önizleme takip veya çıkış kaydı üretmez. Doğrulanmış geçmiş ayrı tutulur.",12,MUTED);
            if(a.lastSuccess>0)action(showingPreview?"Son doğrulanmış listeyi göster":"Son tarama önizlemesini göster",()->{usePreview=!usePreview;page=0;render();});
        }
        if(a.lastSuccess==0&&!showingPreview){label(tab.equals("events")?"Henüz iki liste birlikte doğrulanmadı; takip/çıkış geçmişi başlamadı.":"Bu liste için henüz gösterilecek kişi alınmadı. Listeyi şimdi yenile düğmesini kullanabilirsin.",15,MUTED);return;}
        if(showingPreview)label("GÖSTERİLEN: SON TARAMA ÖNİZLEMESİ",12,MUTED);
        else if(!tab.equals("events"))label("GÖSTERİLEN: SON DOĞRULANMIŞ LİSTE • "+date(a.lastSuccess),12,MUTED);
        EditText search=new EditText(this);search.setTextColor(Color.WHITE);search.setHintTextColor(MUTED);search.setHint(tab.equals("events")?"Hareketlerde kullanıcı adı veya isim ara":"Listede isim ara");search.setSingleLine(true);search.setText(query);content.addView(search);
        action("Ara",()->{query=search.getText().toString().trim().replaceFirst("^@","");page=0;render();});
        if(tab.equals("events")) {
            String[] options=own?new String[]{"Tüm hareketler","Yeni takipçiler","Yeni takipler","Listeden çıkanlar","Takipçilerimden çıkanlar"}:new String[]{"Tüm hareketler","Yeni takipçiler","Yeni takipler","Listeden çıkanlar"};
            if(eventFilter>=options.length)eventFilter=0;
            action("Filtre: "+options[eventFilter],()->new AlertDialog.Builder(this).setTitle("Hareketleri filtrele").setSingleChoiceItems(options,eventFilter,(d,which)->{eventFilter=which;query=search.getText().toString().trim().replaceFirst("^@","");page=0;d.dismiss();render();}).setNegativeButton("Kapat",null).show());
            label("“Listeden çıktı” kaydı, takipten çıkma, engelleme veya hesabın kapanması gibi farklı nedenlerle oluşabilir. İki kontrol arasındaki kısa süreli değişiklikler kaçırılabilir.",12,MUTED);
        }
        int count=0;boolean hasMore=false;
        String kind=(eventFilter==1||eventFilter==4)?"followers":eventFilter==2?"following":"",eventAction=eventFilter>=3?"removed":eventFilter==0?"":"added";
        try(Cursor c=tab.equals("events")?store.events(a.id,a.owner,query,kind,eventAction,page*100):showingPreview?store.previewEdges(a.id,a.owner,tab,query,page*100):store.edges(a.id,a.owner,tab,query,page*100)){
            while(c.moveToNext()) {
                if(count==100){hasMore=true;break;}
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
                    item.addView(text(showingPreview?"Takip zamanı bilinmiyor • tarama önizlemesi":c.getLong(2)==0?"Zaman bilinmiyor · ilk kayıtta mevcut":"Tespit: "+date(c.getLong(2))+"\nAralık: "+date(c.getLong(3))+" → "+date(c.getLong(2)),12,c.getLong(2)==0?MUTED:GREEN));
                }
            }
        }
        if(count==0) label(tab.equals("events")?(eventFilter==4?"Bu aramada takipçi listenden çıkan kimse kaydedilmemiş.":"Bu sayfada değişiklik kaydı yok."):"Bu sayfada kişi bulunamadı.",15,MUTED);
        label("Sayfa "+(page+1)+" • sayfa başına 100 kayıt. Kişiye dokunarak Instagram profilini açabilirsin.",12,MUTED);
        LinearLayout pages=new LinearLayout(this);if(page>0)pages.addView(button("‹ Önceki",()->{page--;render();}));if(hasMore)pages.addView(button("Sonraki ›",()->{page++;render();}));content.addView(pages);
    }
    private void check(long id){
        check(id,false);
    }
    private void check(long id,boolean profileOnly){
        if(Monitor.BUSY.get()){toast("Kontrol zaten sürüyor.");return;}
        if(!profileOnly){usePreview=true;page=0;}
        Context app=getApplicationContext();toast("Kontrol başlatılıyor…");
        new Thread(()->{String result=profileOnly?Monitor.profile(app,id):Monitor.run(app,id,true);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;refreshDisplayedData();new AlertDialog.Builder(this).setTitle("Kontrol sonucu").setMessage(result)
            .setNeutralButton("Bilgiyi kopyala",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Black Follow kontrol","Black Follow 0.4.0\n"+result));toast("Kontrol bilgisi kopyalandı");})
            .setPositiveButton("Tamam",null).show();});},"manual-check").start();
    }
    private void login(){if(Monitor.BUSY.get()){toast("Mevcut kontrolün bitmesini bekle.");return;}startActivity(new Intent(this,LoginActivity.class));}
    private void drawer(){
        Dialog dialog=new Dialog(this);LinearLayout panel=column();panel.setPadding(dp(22),dp(32),dp(22),dp(24));panel.setBackgroundColor(CARD);
        panel.addView(text("BLACK FOLLOW",22,GREEN));panel.addView(text("0.4.0",13,MUTED));
        panel.addView(button("Hesap geçmişi",()->{dialog.dismiss();profilePage=false;selected=0;history=true;render();}));
        panel.addView(button("Instagram oturumu",()->{dialog.dismiss();login();}));
        panel.addView(button("Kontrol ayarları",()->{dialog.dismiss();settings();}));
        panel.addView(button(ChangeNotifications.enabled(this)?"Bildirim ayarları":"Bildirimleri aç",()->{dialog.dismiss();ChangeNotifications.request(this);}));
        panel.addView(button("Son hata bilgisini kopyala",()->{dialog.dismiss();String detail=Session.prefs(this).getString("last_error_detail","Bu sürümde henüz istek hatası kaydedilmedi.");android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Black Follow tanı","Black Follow 0.4.0\n"+Session.globalStatus(this)+"\n"+detail+"\n"+Session.prefs(this).getString("last_list_detail","")));toast("Hata bilgisi kopyalandı");}));
        if(selected>0)panel.addView(button("Bu hesabı dışa aktar (.txt)",()->{dialog.dismiss();exportReport();}));
        if(selected>0) panel.addView(button("Bu hesabın kayıtlarını sil",()->{dialog.dismiss();remove();}));
        panel.addView(button("Instagram'dan çıkış",()->{dialog.dismiss();logout();}));
        panel.addView(text("İlk listeler: zaman bilinmiyor.\nSonraki değişiklikler: tespit zamanı.\n\nInstagram'a erişim sağlanamadığında eski kayıtlar korunur.",14,MUTED));
        ScrollView menuScroll=new ScrollView(this);menuScroll.addView(panel);dialog.setContentView(menuScroll);Window w=dialog.getWindow();if(w!=null){w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.86),-1);w.setGravity(Gravity.START);w.setBackgroundDrawableResource(android.R.color.transparent);}dialog.show();if(w!=null)w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.86),-1);
    }
    private void settings(){
        String[] options={"6 saatte bir","12 saatte bir","24 saatte bir","Otomatik kontrolü kapat"};
        new AlertDialog.Builder(this).setTitle("Arka plan kontrolü").setItems(options,(d,i)->{Session.prefs(this).edit().putBoolean("automatic",i<3).putInt("hours",i==0?6:i==1?12:24).apply();MonitorJob.schedule(this);render();}).setNegativeButton("Kapat",null).show();
    }
    private void remove(){
        long id=selected;new AlertDialog.Builder(this).setTitle("Bu hesabın geçmişi silinsin mi?").setMessage("Takipçi, takip ve hareket kayıtları bu telefondan silinecek; otomatik izleme duracak.").setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{if(Monitor.BUSY.get()){toast("Kontrol bitince tekrar dene.");return;}store.delete(id,Session.owner(this));ChangeNotifications.cancel(this,id);selected=0;if(searchSelected==id)searchSelected=0;render();}).show();
    }
    private void logout(){
        if(Monitor.BUSY.get()){toast("Önce mevcut kontrolün bitmesini bekle.");return;}
        new AlertDialog.Builder(this).setTitle("Oturumu kapat?").setMessage("Otomatik kontroller duracak. Kayıtların saklanacak ve aynı Instagram hesabıyla yeniden giriş yaptığında görünecek.").setNegativeButton("Vazgeç",null).setPositiveButton("Çıkış",(d,w)->{
            ChangeNotifications.clear(this);Session.pause(this,"Oturum kapalı");Session.prefs(this).edit().remove("owner").remove("viewer_name").apply();
            CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();resetChangedOwner();selected=0;render();});
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
    @Override public void onBackPressed(){if(profilePage){showSearch();}else if(selected>0){selected=0;render();}else super.onBackPressed();}
}
