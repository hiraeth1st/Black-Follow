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
    /** Migrate only identifiable app-generated waits; preserve server/unknown deadlines. */
    public static synchronized void migrateRatePolicy(Context c) {
        SharedPreferences p=prefs(c);
        if(p.getBoolean("rate_policy_v2",false)) return;
        String source=p.getString("blocked_source","");
        SharedPreferences.Editor edit=p.edit().putBoolean("rate_policy_v2",true).remove("rate_failures").remove("last_verify_attempt");
        if(source.isEmpty() || source.equals("Instagram süre bildirmedi; uygulamanın kademeli beklemesi") || source.equals("Önceki sürümün 24 saatlik yerel beklemesi")) {
            boolean active=p.getLong("blocked_until",0)>System.currentTimeMillis();
            edit.remove("blocked_until").remove("blocked_source");
            if(active) edit.putBoolean("rate_manual_required",true);
        }
        edit.apply();
    }
    public static boolean blocked(Context c) {migrateRatePolicy(c);return prefs(c).getLong("blocked_until",0)>System.currentTimeMillis();}
    public static boolean manualRequired(Context c) {migrateRatePolicy(c);return prefs(c).getBoolean("rate_manual_required",false);}
    public static synchronized void recordRate(Context c,InstagramClient.AccessError e) {
        migrateRatePolicy(c);long now=System.currentTimeMillis();SharedPreferences p=prefs(c);
        SharedPreferences.Editor edit=p.edit().putBoolean("rate_manual_required",true).putString("blocked_detail",e.getMessage());
        if(e.retryAfterMs>=0) edit.putLong("blocked_until",RetryPolicy.until(p.getLong("blocked_until",0),now,e.retryAfterMs))
            .putString("blocked_source","Instagram yanıtındaki bekleme süresi");
        edit.apply();
    }
    /** A successful user-requested data check resumes background checks. Login alone does not. */
    public static void dataSucceeded(Context c) {
        if(!blocked(c)) prefs(c).edit().putBoolean("rate_manual_required",false).remove("blocked_until").remove("blocked_source").remove("blocked_detail").apply();
    }
    public static String waitMessage(Context c) {
        migrateRatePolicy(c);long until=prefs(c).getLong("blocked_until",0),remaining=Math.max(0,until-System.currentTimeMillis());
        if(remaining==0) return "Instagram son veri isteğini sınırladı. Uygulamanın eklediği bir bekleme süresi yok. Elle yenileyebilirsin; otomatik kontroller başarılı bir yenilemeye kadar duraklatıldı. [BF_RATE_MANUAL]"+
            (prefs(c).getString("blocked_detail","").isEmpty()?"":"\nSon yanıt: "+prefs(c).getString("blocked_detail",""));
        String date=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.forLanguageTag("tr-TR")).format(new java.util.Date(until));
        long minutes=(remaining-1)/60000+1;
        return "Instagram'ın bildirdiği bekleme • kalan "+(minutes/60)+" sa "+(minutes%60)+" dk\nYeniden deneme: "+date+
            "\nSürenin bitmesi erişim garantisi değildir. [BF_RATE_WAIT]\n"+prefs(c).getString("blocked_detail","");
    }
    public static String displayStatus(Context c,String status) {
        // Old per-account messages are historical results, not a live countdown.
        if(status.contains("[BF_RATE_WAIT]")) return (blocked(c)||manualRequired(c))?waitMessage(c):"Önceki kontrol istek sınırına takıldı. Yerel bekleme kaldırıldı; elle yenileyebilirsin.";
        return status;
    }
    public static boolean authenticated(Context c,String owner) {
        return owner.equals(owner(c)) && !prefs(c).getBoolean("paused",false) && prefs(c).getLong("verified_at",0)>0 && matches(owner);
    }
    public static String globalStatus(Context c) {
        if(owner(c).isEmpty()) return "Instagram oturumu bağlanmadı";
        if(prefs(c).getBoolean("paused",false)) return prefs(c).getString("pause_reason","Oturum kontrolü gerekiyor");
        if(blocked(c)||manualRequired(c)) return waitMessage(c);
        if(!prefs(c).getBoolean("automatic",true)) return "@"+prefs(c).getString("viewer_name","")+" • otomatik kontrol kapalı";
        return "@"+prefs(c).getString("viewer_name","")+" • otomatik kontrol: "+prefs(c).getInt("hours",6)+" saatte bir";
    }
}
