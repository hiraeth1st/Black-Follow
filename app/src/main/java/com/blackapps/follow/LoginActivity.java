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
    private boolean ownsLock=false,checking=false,pageReady=false;private int navigation=0,attempt=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if(!Monitor.BUSY.compareAndSet(false,true)) {Toast.makeText(this,"Önce mevcut kontrolün bitmesini bekle.",Toast.LENGTH_LONG).show();finish();return;}
        ownsLock=true;Session.migrateRatePolicy(this);
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
            public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {pageReady=false;navigation++;}
            public void onPageFinished(WebView view,String url) {pageReady=true;Uri u=Uri.parse(url);address.setText("Instagram oturumu • "+u.getHost());CookieManager.getInstance().flush();}
        });
        verify=new Button(this);verify.setText("Giriş yaptım • oturumu doğrula");verify.setOnClickListener(v->verify());root.addView(verify);
        Button back=new Button(this);back.setText("Geri dön");back.setOnClickListener(v->finish());root.addView(back);
        setContentView(root);web.loadUrl(Session.ORIGIN+"/accounts/login/");
    }
    private void verify() {
        if(checking) return;
        CookieManager.getInstance().flush();
        final String owner=Session.cookieValue(Session.cookies(),"ds_user_id"),url=web.getUrl();
        if(!pageReady || !WebSession.trusted(url) || !owner.matches("[0-9]+") || !Session.matches(owner)) {
            Toast.makeText(this,"Instagram girişini ve varsa doğrulamayı tamamlayıp ana sayfanın açılmasını bekle.",Toast.LENGTH_LONG).show();return;
        }
        final int startedAt=navigation,attemptId=++attempt;final String script;
        try(java.io.InputStream in=getResources().openRawResource(R.raw.session_probe);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);script=out.toString("UTF-8");
        } catch(Exception e) {showError("Giriş kontrolü yüklenemedi. [BF_LOGIN_RESOURCE]");return;}
        checking=true;verify.setEnabled(false);verify.setText("Instagram sayfasındaki oturum kontrol ediliyor…");
        final Runnable timeout=()->{if(checking){checking=false;resetButton();showError("Instagram sayfası yanıt vermedi. Sayfa yüklendiğinde tekrar deneyebilirsin. [BF_LOGIN_PAGE]");}};
        handler.postDelayed(timeout,10000);
        web.evaluateJavascript(script,result->{
            if(attemptId!=attempt || !checking || isFinishing() || isDestroyed()) return;
            checking=false;handler.removeCallbacks(timeout);resetButton();
            String name=WebSession.verifiedName(url,web.getUrl(),owner,Session.cookieValue(Session.cookies(),"ds_user_id"),Session.matches(owner),result);
            if(startedAt!=navigation || !pageReady || name.isEmpty()) {
                showError("Instagram sayfasında giriş yapılmış hesabın profil düğmesi doğrulanamadı. Instagram ana sayfasını açıp sayfa tamamen yüklendiğinde tekrar bas. [BF_LOGIN_PAGE]");return;
            }
            boolean changed=!owner.equals(Session.owner(this));if(changed) ChangeNotifications.clear(this);
            android.content.SharedPreferences.Editor edit=Session.prefs(this).edit().putString("owner",owner).putString("viewer_name",name).putBoolean("paused",false)
                .putLong("verified_at",System.currentTimeMillis()).remove("pause_reason");
            if(changed)edit.remove("www_claim").remove("mobile_claim");edit.apply();
            CookieManager.getInstance().flush();MonitorJob.schedule(this);
            Toast.makeText(this,"@"+name+" bağlandı",Toast.LENGTH_LONG).show();finish();
        });
    }
    private void resetButton() {verify.setEnabled(true);verify.setText("Giriş yaptım • oturumu doğrula");}
    private void showError(String error) {
        new AlertDialog.Builder(this).setTitle("Instagram oturumu").setMessage(error)
            .setNeutralButton("Instagram ana sayfası",(d,w)->web.loadUrl(Session.ORIGIN+"/"))
            .setPositiveButton("Tamam",null).show();
    }
    public void onDestroy() {
        checking=false;handler.removeCallbacksAndMessages(null);
        if(web!=null) {web.stopLoading();web.destroy();}
        if(ownsLock) {ownsLock=false;Monitor.BUSY.set(false);}
        super.onDestroy();
    }
}
