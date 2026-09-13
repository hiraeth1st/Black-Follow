package com.blackapps.follow;

import android.content.*;
import android.webkit.CookieManager;

public final class Session {
    public static final String ORIGIN="https://www.instagram.com";
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings",Context.MODE_PRIVATE); }
    public static String owner(Context c) { return prefs(c).getString("owner",""); }
    public static String cookieValue(String cookies,String key) {
        if(cookies==null) return "";
        for(String part:cookies.split(";")) { int at=part.indexOf('='); if(at>0 && part.substring(0,at).trim().equals(key)) return part.substring(at+1).trim(); }
        return "";
    }
    public static String cookies() { String v=CookieManager.getInstance().getCookie(ORIGIN); return v==null?"":v; }
    public static boolean matches(String owner) {
        String c=cookies();return !owner.isEmpty() && owner.equals(cookieValue(c,"ds_user_id")) && !cookieValue(c,"sessionid").isEmpty();
    }
    public static long interval(Context c) { return prefs(c).getInt("hours",6)*3600000L; }
    public static void pause(Context c,String reason) { prefs(c).edit().putBoolean("paused",true).putString("pause_reason",reason).apply(); }
    public static boolean blocked(Context c) {return prefs(c).getLong("blocked_until",0)>System.currentTimeMillis();}
    public static void recordRate(Context c,InstagramClient.AccessError e) {
        long now=System.currentTimeMillis();int failures=prefs(c).getInt("rate_failures",0);
        long until=RetryPolicy.until(prefs(c).getLong("blocked_until",0),now,RetryPolicy.delay(e.retryAfterMs,failures));
        prefs(c).edit().putLong("blocked_until",until).putLong("blocked_since",now).putInt("rate_failures",Math.min(20,failures+1))
            .putString("blocked_detail",e.getMessage()).putString("blocked_source",e.retryAfterMs>=0?"Instagram yanıtındaki bekleme süresi":"Instagram süre bildirmedi; uygulamanın kademeli beklemesi").apply();
    }
    public static String waitMessage(Context c) {
        long until=prefs(c).getLong("blocked_until",0),remaining=Math.max(0,until-System.currentTimeMillis());
        if(remaining==0) return "İstek sınırı beklemesi tamamlandı; yeniden kontrol edebilirsin.";
        String date=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.forLanguageTag("tr-TR")).format(new java.util.Date(until));
        long minutes=(remaining-1)/60000+1;
        return "Kontroller beklemede • kalan "+(minutes/60)+" sa "+(minutes%60)+" dk\nYeniden deneme: "+date+"\n"+
            prefs(c).getString("blocked_source","Önceki sürümün 24 saatlik yerel beklemesi")+". Sürenin bitmesi erişim garantisi değildir. [BF_RATE_WAIT]"+
            (prefs(c).getString("blocked_detail","").isEmpty()?"\nBu beklemenin istek ayrıntısı önceki sürümde kaydedilmemiş.":"\nİlk engel: "+prefs(c).getString("blocked_detail",""));
    }
    public static boolean recentlyVerified(Context c,String owner) {
        long age=System.currentTimeMillis()-prefs(c).getLong("verified_at",0);
        return owner.equals(owner(c)) && !prefs(c).getBoolean("paused",false) && age>=0 && age<10*60000L && matches(owner);
    }
    public static String globalStatus(Context c) {
        if(owner(c).isEmpty()) return "Instagram oturumu bağlanmadı";
        if(prefs(c).getBoolean("paused",false)) return prefs(c).getString("pause_reason","Oturum kontrolü gerekiyor");
        long until=prefs(c).getLong("blocked_until",0);
        if(until>System.currentTimeMillis()) return waitMessage(c);
        if(!prefs(c).getBoolean("automatic",true)) return "@"+prefs(c).getString("viewer_name","")+" • otomatik kontrol kapalı";
        return "@"+prefs(c).getString("viewer_name","")+" • otomatik kontrol: "+prefs(c).getInt("hours",6)+" saatte bir";
    }
}
