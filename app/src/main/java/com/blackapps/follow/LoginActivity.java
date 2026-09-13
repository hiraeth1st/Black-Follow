package com.blackapps.follow;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class LoginActivity extends Activity {
    private WebView web;private TextView address;private Button verify;
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if(Monitor.BUSY.get()) {Toast.makeText(this,"Önce mevcut kontrolün bitmesini bekle.",Toast.LENGTH_LONG).show();finish();return;}
        Session.pause(this,"Instagram oturumunu doğrulaman gerekiyor.");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(13,16,21));root.setPadding(12,0,12,0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(12,insets.getSystemWindowInsetTop(),12,insets.getSystemWindowInsetBottom());return insets;});
        address=new TextView(this);address.setTextColor(Color.WHITE);address.setPadding(12,16,12,16);root.addView(address);
        TextView note=new TextView(this);note.setText("Instagram'ın kendi sayfasında kullanıcı adınla giriş yap. Giriş tamamlanınca aşağıdaki düğmeye bas. Facebook ile giriş bu sürümde desteklenmiyor.");note.setTextColor(Color.LTGRAY);note.setPadding(12,0,12,12);root.addView(note);
        web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSaveFormData(false);s.setSavePassword(false);s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        Session.prefs(this).edit().putString("user_agent",s.getUserAgentString()).apply();
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                Uri u=req.getUrl();String host=u.getHost();boolean allowed="https".equals(u.getScheme()) && ("www.instagram.com".equals(host)||"instagram.com".equals(host));
                if(!allowed && req.isForMainFrame()) Toast.makeText(LoginActivity.this,"Bu ekranda yalnızca Instagram'ın HTTPS sayfaları açılır.",Toast.LENGTH_LONG).show();
                return !allowed;
            }
            public void onPageFinished(WebView view,String url) {Uri u=Uri.parse(url);address.setText("Instagram oturumu • "+u.getHost());CookieManager.getInstance().flush();}
        });
        verify=new Button(this);verify.setText("Giriş yaptım • oturumu doğrula");verify.setOnClickListener(v->verify());root.addView(verify);
        Button back=new Button(this);back.setText("Geri dön");back.setOnClickListener(v->finish());root.addView(back);
        setContentView(root);web.loadUrl(Session.ORIGIN+"/accounts/login/");
    }
    private void verify() {
        CookieManager.getInstance().flush();
        String owner=Session.cookieValue(Session.cookies(),"ds_user_id");
        if(!owner.matches("[0-9]+") || !Session.matches(owner)) {Toast.makeText(this,"Önce Instagram girişini tamamla.",Toast.LENGTH_LONG).show();return;}
        if(Session.blocked(this)) {new AlertDialog.Builder(this).setTitle("Kontroller beklemede").setMessage(Session.waitMessage(this)).setPositiveButton("Tamam",null).show();return;}
        if(System.currentTimeMillis()-Session.prefs(this).getLong("last_verify_attempt",0)<30000) {Toast.makeText(this,"Tekrar denemeden önce 30 saniye bekle.",Toast.LENGTH_LONG).show();return;}
        if(!Monitor.BUSY.compareAndSet(false,true)) return;
        Session.prefs(this).edit().putLong("last_verify_attempt",System.currentTimeMillis()).apply();
        verify.setEnabled(false);verify.setText("Oturum doğrulanıyor…");
        android.content.Context app=getApplicationContext();
        new Thread(()->{
            String name="",error="";
            try {name=new InstagramClient(app,owner,System.currentTimeMillis()+60000).verifyViewer();}
            catch(Exception e) {
                error=InstagramClient.diagnostic(e);
                if(e instanceof InstagramClient.AccessError) {
                    InstagramClient.AccessError a=(InstagramClient.AccessError)e;error=a.getMessage();
                    if(a.rate) {Session.recordRate(app,a);error=Session.waitMessage(app);}
                }
            }
            if(error.isEmpty() && !Session.matches(owner)) error="Oturum kontrol sırasında değişti. Yeniden doğrula.";
            final String resultName=name, resultError=error;
            if(error.isEmpty() && Session.matches(owner)) {
                Session.prefs(app).edit().putString("owner",owner).putString("viewer_name",name).putBoolean("paused",false).putLong("verified_at",System.currentTimeMillis()).remove("pause_reason").apply();
                CookieManager.getInstance().flush();MonitorJob.schedule(app);
            }
            Monitor.BUSY.set(false);
            runOnUiThread(()->{if(isFinishing()||isDestroyed()) return;
                verify.setEnabled(true);verify.setText("Giriş yaptım • oturumu doğrula");
                if(resultError.isEmpty()) {Toast.makeText(this,"@"+resultName+" bağlandı",Toast.LENGTH_LONG).show();finish();}
                else new AlertDialog.Builder(this).setTitle("Oturum testi").setMessage(resultError+"\n\nHata bilgisini kopyalayıp paylaşabilirsin; parola veya oturum bilgisi içermez.")
                    .setNeutralButton("Hata bilgisini kopyala",(d,w)->{
                        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Black Follow hata bilgisi","Black Follow 0.2.1 • oturum testi\n"+resultError));
                        Toast.makeText(this,"Hata bilgisi kopyalandı",Toast.LENGTH_SHORT).show();
                    }).setPositiveButton("Tamam",null).show();
            });
        },"session-verification").start();
    }
    public void onDestroy() {if(web!=null) {web.stopLoading();web.destroy();}super.onDestroy();}
}
