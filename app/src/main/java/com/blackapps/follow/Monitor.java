package com.blackapps.follow;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Monitor {
    public static final AtomicBoolean BUSY=new AtomicBoolean(false);
    public static volatile String progress="";
    public static final java.util.concurrent.atomic.AtomicLong REVISION=new java.util.concurrent.atomic.AtomicLong();
    public static String run(Context c,long onlyId,boolean force) {
        return run(c,onlyId,force,false);
    }
    public static String profile(Context c,long id) {return run(c,id,true,true);}
    private static String run(Context c,long onlyId,boolean force,boolean profileOnly) {
        if(!BUSY.compareAndSet(false,true)) return "Başka bir kontrol sürüyor.";
        try(Store store=new Store(c)) {
            String owner=Session.owner(c);
            if(!force && !Session.prefs(c).getBoolean("automatic",true)) return "Otomatik kontrol kapalı.";
            if(owner.isEmpty() || Session.prefs(c).getBoolean("paused",false)) return "Önce Instagram oturumunu doğrula.";
            if(Session.blocked(c) || (!force && Session.manualRequired(c))) return Session.waitMessage(c);
            if(!Session.authenticated(c,owner)) {Session.pause(c,"Instagram oturumu değişti veya sona erdi. Yeniden giriş yap.");return Session.globalStatus(c);}
            boolean due=false;
            for(Store.Account a:store.accounts(owner)) if((a.enabled||profileOnly||(force&&onlyId>0)) && (onlyId<=0 || a.id==onlyId) && (force||a.nextDue<=System.currentTimeMillis())) {due=true;break;}
            if(!due) return force?"Kontrol edilecek hesap yok.":"Henüz kontrol zamanı gelen hesap yok.";
            long deadline=System.currentTimeMillis()+7*60000;
            InstagramClient client=new InstagramClient(c,owner,deadline,(kind,page,received,total)->progress=(kind.startsWith("followers")?"Takipçiler":"Takip edilenler")+(kind.endsWith("_search")?" • önek araması":"")+": "+received+"/"+total+" kişi • sayfa "+page);
            int ok=0, failed=0, profiles=0, previews=0;String lastError="",changes="";
            for(Store.Account a:store.accounts(owner)) {
                if(Thread.currentThread().isInterrupted() || System.currentTimeMillis()>deadline) break;
                if(!force && !Session.prefs(c).getBoolean("automatic",true)) break;
                if((!a.enabled && !profileOnly && !(force&&onlyId>0)) || (onlyId>0 && a.id!=onlyId) || (!force && a.nextDue>System.currentTimeMillis())) continue;
                progress="@"+a.username+" profil bilgileri alınıyor…";
                store.profileAttempt(a);if(!profileOnly)store.status(a.id,owner,progress,false,0);
                try {
                    long scanStart=System.currentTimeMillis();
                    InstagramClient.Profile p=client.readProfile(a);
                    if(!Session.matches(owner) || !Session.owner(c).equals(owner)) throw new InstagramClient.AccessError("Oturum değişti; kontrol durduruldu.",true,false);
                    store.saveProfile(a,p);profiles++;
                    if(profileOnly) {Session.dataSucceeded(c);ok++;continue;}
                    progress="@"+a.username+" sayıları alındı • kişi listeleri taranıyor…";
                    client.observeLists((kind,people,expected)->{
                        if(!Session.matches(owner)||!Session.owner(c).equals(owner))throw new InstagramClient.AccessError("Oturum değişti; önizleme kaydedilmedi.",true,false);
                        store.savePreview(a,kind,people,expected);REVISION.incrementAndGet();
                    });
                    InstagramClient.Snapshot snapshot=client.snapshot(a,p,scanStart);
                    if(!Session.matches(owner) || !Session.owner(c).equals(owner)) throw new InstagramClient.AccessError("Oturum değişti; kontrol durduruldu.",true,false);
                    long before=store.lastEventId(a.id,owner);
                    if(store.commit(a,snapshot,Session.interval(c),force&&onlyId>0)) {Session.dataSucceeded(c);ok++;ChangeNotifications.post(c,store,a,before);if(onlyId>0)changes=a.lastSuccess==0?"\nİlk tam liste kaydedildi. Sonraki yenilemelerde değişiklikler gösterilecek.":store.changes(a.id,owner,before);}
                } catch(Exception e) {
                    if(e instanceof InstagramClient.AccessError) handle(c,(InstagramClient.AccessError)e);
                    String message=e instanceof InstagramClient.AccessError && ((InstagramClient.AccessError)e).rate?Session.waitMessage(c):message(e);lastError=message;if(e instanceof InstagramClient.PartialLists)previews++;else failed++;
                    // A newly searched alias may resolve to an already tracked numeric ID.
                    // Remove only its empty placeholder, so it cannot block the existing account's rename.
                    if(e instanceof android.database.sqlite.SQLiteConstraintException && a.lastSuccess==0 && a.remote.isEmpty()) store.delete(a.id,owner);
                    long backoff=Session.interval(c);
                    if(profileOnly)store.profileError(a,message);else store.status(a.id,owner,message,true,System.currentTimeMillis()+backoff);
                    if(e instanceof InstagramClient.AccessError) break;
                    if(e instanceof InterruptedException) {Thread.currentThread().interrupt();break;}
                }
            }
            if(onlyId>0 && previews>0)return lastError;
            return profileOnly?(ok>0?"Profil sayıları alındı. Kişileri görmek için Listeyi şimdi yenile düğmesini kullan.":lastError):profiles+" profil sayısı alındı • "+ok+" hesabın kişi listeleri güncellendi"+(previews>0?", "+previews+" hesabın önizlemesi alındı":"")+(failed>0?", "+failed+" kontrol tamamlanamadı":"")+(lastError.isEmpty()?".":". "+lastError)+changes;
        } catch(Exception e) {return message(e);}
        finally {progress="";REVISION.incrementAndGet();BUSY.set(false);}
    }
    private static void handle(Context c,InstagramClient.AccessError e) {
        if(e.auth) Session.pause(c,e.getMessage());
        if(e.rate) Session.recordRate(c,e);
    }
    private static String message(Exception e) {
        if(e instanceof org.json.JSONException) return "Instagram veri biçimi değişmiş veya liste eksik; geçmiş korunuyor.";
        if(e instanceof android.database.sqlite.SQLiteConstraintException) return "Bu hesap başka bir adla geçmişte kayıtlı. Mevcut kaydı aç.";
        if(e instanceof java.net.SocketTimeoutException) return "Bağlantı zaman aşımına uğradı; geçmiş korunuyor.";
        if(e instanceof java.net.UnknownHostException) return "İnternet bağlantısı kurulamadı; geçmiş korunuyor.";
        if(e instanceof IllegalArgumentException || e instanceof java.io.IOException) return e.getMessage()==null?"Veriler alınamadı.":e.getMessage();
        return "Kontrol tamamlanamadı; önceki kayıtlar korunuyor.";
    }
}
