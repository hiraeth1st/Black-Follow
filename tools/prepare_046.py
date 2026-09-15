from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count=text.count(old)
    if count!=1:raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

# Fix final-field initialization and byte-to-hex conversion in the new mobile client.
mobile=Path('app/src/main/java/com/blackapps/follow/MobileInstagramClient.java')
value=mobile.read_text(encoding='utf-8')
old='''        uuid=stableUuid(prefs,"mobile_uuid");phoneId=stableUuid(prefs,"mobile_phone_id");
        androidId=prefs.getString("mobile_android_id","");
        if(androidId.isEmpty()) {
            String generated="android-"+hex(sha256(owner+":"+uuid)).substring(0,16);
            prefs.edit().putString("mobile_android_id",generated).apply();
            androidId=generated;
        }
'''
new='''        uuid=stableUuid(prefs,"mobile_uuid");phoneId=stableUuid(prefs,"mobile_phone_id");
        String savedAndroidId=prefs.getString("mobile_android_id","");
        if(savedAndroidId.isEmpty()) {
            savedAndroidId="android-"+hex(sha256(owner+":"+uuid)).substring(0,16);
            prefs.edit().putString("mobile_android_id",savedAndroidId).apply();
        }
        androidId=savedAndroidId;
'''
value=replace_once(value,old,new,'mobile final field')
value=replace_once(value,'for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b));','for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&0xff));','mobile hex')
mobile.write_text(value,encoding='utf-8')

client=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
value=client.read_text(encoding='utf-8')
trace='''    private void traceRestRecovery(String kind,String label,int pass,int pages,int rows,int added,int unique,int expected,boolean terminal,boolean limited,boolean unsupported) {
        listTrace.put("RestRecovery"+kind+label+pass,"REST kurtarma "+("followers".equals(kind)?"takipçi":"takip")+": yöntem="+label+", tur="+pass+", sayfa="+pages+", satır="+rows+", yeni="+added+", kişi="+unique+"/"+expected+", terminal="+terminal+", sınırlı="+limited+", destek="+(!unsupported));
        saveListTrace();
    }
'''
trace_new=trace+'''    private void traceMobile(String kind,MobileInstagramClient.Result result,int expected) {
        listTrace.put("Mobile"+kind,"Mobil özel API/GraphQL "+("followers".equals(kind)?"takipçi":"takip")+": kişi="+result.people.size()+"/"+expected+(result.detail.isEmpty()?"":"\\n"+result.detail));
        saveListTrace();
    }
'''
value=replace_once(value,trace,trace_new,'mobile trace')
old_snapshot='''        s.followers=people(s.profile.id,"followers",s.profile.followers,followerRank);
        s.following=people(s.profile.id,"following",s.profile.following,followingRank);
        s.followers=completeBySearch(s.profile.id,"followers",s.profile.followers,followerRank,oldFollowers,s.followers);
        s.following=completeBySearch(s.profile.id,"following",s.profile.following,followingRank,oldFollowing,s.following);
        if(s.followers.size()!=s.profile.followers || s.following.size()!=s.profile.following)
            throw new PartialLists("Normal liste, REST kurtarma ve liste aramalarıyla tam sonuç doğrulanamadı: "+s.followers.size()+"/"+s.profile.followers+" takipçi, "+s.following.size()+"/"+s.profile.following+" takip. Alınabilen kişiler önizleme olarak saklandı. Eksik kişiler takipten çıktı sayılmadı; doğrulanmış geçmiş değişmedi. [BF_LIST_PARTIAL]\\n"+String.join("\\n",listTrace.values()));
'''
new_snapshot='''        s.followers=people(s.profile.id,"followers",s.profile.followers,followerRank);
        s.following=people(s.profile.id,"following",s.profile.following,followingRank);
        MobileInstagramClient mobileClient=new MobileInstagramClient(context,owner,deadline,progress);
        if(s.followers.size()!=s.profile.followers) {
            MobileInstagramClient.Result mobile=mobileClient.complete(s.profile.id,"followers",s.profile.followers,s.followers);
            s.followers=mobile.people;traceMobile("followers",mobile,s.profile.followers);observer.received("followers",s.followers,s.profile.followers);
        }
        if(s.following.size()!=s.profile.following) {
            MobileInstagramClient.Result mobile=mobileClient.complete(s.profile.id,"following",s.profile.following,s.following);
            s.following=mobile.people;traceMobile("following",mobile,s.profile.following);observer.received("following",s.following,s.profile.following);
        }
        s.followers=completeBySearch(s.profile.id,"followers",s.profile.followers,followerRank,oldFollowers,s.followers);
        s.following=completeBySearch(s.profile.id,"following",s.profile.following,followingRank,oldFollowing,s.following);
        if(s.followers.size()!=s.profile.followers || s.following.size()!=s.profile.following)
            throw new PartialLists("Tam liste zorunlu olduğu için kontrol bitirilmedi: "+s.followers.size()+"/"+s.profile.followers+" takipçi, "+s.following.size()+"/"+s.profile.following+" takip. Web REST, mobil özel REST, güncel özel GraphQL ve liste aramaları tamamlandı; bulunan kimlikler kalıcı aday havuzunda saklandı ve otomatik kesinleştirme devam edecek. Geçmiş veya takipten çıkma olayı üretilmedi. [BF_STRICT_INCOMPLETE]\\n"+String.join("\\n",listTrace.values()));
'''
value=replace_once(value,old_snapshot,new_snapshot,'strict snapshot pipeline')
client.write_text(value,encoding='utf-8')

monitor=Path('app/src/main/java/com/blackapps/follow/Monitor.java')
value=monitor.read_text(encoding='utf-8')
value=replace_once(value,'long deadline=System.currentTimeMillis()+(force&&!profileOnly?30:7)*60000;','long deadline=System.currentTimeMillis()+(force&&!profileOnly?45:7)*60000;','manual strict deadline')
old_progress='''            InstagramClient client=new InstagramClient(c,owner,deadline,(kind,page,received,total)->progress=(kind.startsWith("followers")?"Takipçiler":"Takip edilenler")+(kind.endsWith("_target")?" • hedefli arama":kind.endsWith("_search")?" • önek araması":"")+": "+received+"/"+total+" kişi • "+((kind.endsWith("_target")||kind.endsWith("_search"))?"istek ":"sayfa ")+page);
'''
new_progress='''            InstagramClient client=new InstagramClient(c,owner,deadline,(kind,page,received,total)->{
                String phase=kind.endsWith("_mobile_rest")?" • mobil REST":kind.endsWith("_mobile_gql")?" • mobil GraphQL":kind.endsWith("_target")?" • hedefli arama":kind.endsWith("_search")?" • liste araması":"";
                progress=(kind.startsWith("followers")?"Takipçiler":"Takip edilenler")+phase+": "+received+"/"+total+" kişi • "+((kind.endsWith("_target")||kind.endsWith("_search"))?"istek ":"sayfa ")+page;
            });
'''
value=replace_once(value,old_progress,new_progress,'strict progress labels')
old_commit='''                    if(store.commit(a,snapshot,Session.interval(c),force&&onlyId>0)) {Session.dataSucceeded(c);ok++;ChangeNotifications.post(c,store,a,before);if(onlyId>0)changes=a.lastSuccess==0?"\\nİlk tam liste kaydedildi. Sonraki yenilemelerde değişiklikler gösterilecek.":store.changes(a.id,owner,before);}
'''
new_commit='''                    if(store.commit(a,snapshot,Session.interval(c),force&&onlyId>0)) {Session.dataSucceeded(c);MonitorJob.cancelStrict(c);ok++;ChangeNotifications.post(c,store,a,before);if(onlyId>0)changes=a.lastSuccess==0?"\\nİlk tam liste kaydedildi. Sonraki yenilemelerde değişiklikler gösterilecek.":store.changes(a.id,owner,before);}
'''
value=replace_once(value,old_commit,new_commit,'cancel strict retry on success')
old_catch='''                    String message=e instanceof InstagramClient.AccessError && ((InstagramClient.AccessError)e).rate?Session.waitMessage(c):message(e);lastError=message;if(e instanceof InstagramClient.PartialLists)previews++;else failed++;
'''
new_catch='''                    boolean strictIncomplete=e instanceof InstagramClient.PartialLists;
                    String message=e instanceof InstagramClient.AccessError && ((InstagramClient.AccessError)e).rate?Session.waitMessage(c):message(e);lastError=message;if(strictIncomplete){previews++;MonitorJob.scheduleStrict(c);}else failed++;
'''
value=replace_once(value,old_catch,new_catch,'schedule strict retry')
value=replace_once(value,'                    long backoff=Session.interval(c);','                    long backoff=strictIncomplete?30*60000L:Session.interval(c);','strict retry due time')
value=replace_once(value,'+(previews>0?", "+previews+" hesabın önizlemesi alındı":"")','+(previews>0?", "+previews+" hesabın tamamlama işlemi otomatik sürdürülecek":"")','strict summary')
monitor.write_text(value,encoding='utf-8')

job=Path('app/src/main/java/com/blackapps/follow/MonitorJob.java')
job.write_text(r'''package com.blackapps.follow;

import android.app.job.*;
import android.content.*;

public class MonitorJob extends JobService {
    private static final int PERIODIC_JOB=101,STRICT_JOB=102;
    private Thread worker;
    private static JobScheduler scheduler(Context c){return (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);}
    public static void schedule(Context c) {
        JobScheduler scheduler=scheduler(c);
        if(!Session.prefs(c).getBoolean("automatic",true)) {scheduler.cancel(PERIODIC_JOB);scheduler.cancel(STRICT_JOB);return;}
        JobInfo existing=scheduler.getPendingJob(PERIODIC_JOB);
        if(existing!=null && existing.getIntervalMillis()==Session.interval(c)) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setPeriodic(Session.interval(c)).setBackoffCriteria(3600000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void scheduleStrict(Context c) {
        if(!Session.prefs(c).getBoolean("automatic",true))return;
        JobScheduler scheduler=scheduler(c);if(scheduler.getPendingJob(STRICT_JOB)!=null)return;
        scheduler.schedule(new JobInfo.Builder(STRICT_JOB,new ComponentName(c,MonitorJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setMinimumLatency(30*60000L).setOverrideDeadline(2*3600000L)
            .setBackoffCriteria(30*60000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void cancelStrict(Context c){scheduler(c).cancel(STRICT_JOB);}
    public boolean onStartJob(JobParameters params) {
        worker=new Thread(()->{Monitor.run(getApplicationContext(),0,false);jobFinished(params,false);},"black-follow-job");worker.start();return true;
    }
    public boolean onStopJob(JobParameters params) {if(worker!=null) worker.interrupt();return true;}
}
''',encoding='utf-8')

login=Path('app/src/main/java/com/blackapps/follow/LoginActivity.java')
value=login.read_text(encoding='utf-8')
value=replace_once(value,'if(changed)edit.remove("www_claim");edit.apply();','if(changed)edit.remove("www_claim").remove("mobile_claim");edit.apply();','mobile claim reset on login change')
login.write_text(value,encoding='utf-8')

main=Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
value=main.read_text(encoding='utf-8')
value=replace_once(value,'.remove("www_claim").apply();','.remove("www_claim").remove("mobile_claim").apply();','mobile claim reset on logout')
value=value.replace('0.4.5','0.4.6')
main.write_text(value,encoding='utf-8')

test=Path('test.sh')
value=test.read_text(encoding='utf-8')
needle='''  java -cp "$BF_JSON_JAR:out/tests" ProfileLookupTest
'''
insert=needle+'''  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/tests app/src/main/java/com/blackapps/follow/RelationshipRequest.java app/src/main/java/com/blackapps/follow/MobileRequestLogic.java tests/MobileRequestLogicTest.java
  java -cp "$BF_JSON_JAR:out/tests" MobileRequestLogicTest
'''
value=replace_once(value,needle,insert,'mobile request test runner')
test.write_text(value,encoding='utf-8')

gradle=Path('app/build.gradle')
value=replace_once(gradle.read_text(encoding='utf-8'),"versionCode 20; versionName '0.4.5-test'","versionCode 21; versionName '0.4.6-test'",'Gradle version')
gradle.write_text(value,encoding='utf-8')
manifest=Path('app/src/main/AndroidManifest.xml')
value=replace_once(manifest.read_text(encoding='utf-8'),'android:versionCode="20" android:versionName="0.4.5-test"','android:versionCode="21" android:versionName="0.4.6-test"','Manifest version')
manifest.write_text(value,encoding='utf-8')
build=Path('build-apk.sh')
build.write_text(build.read_text(encoding='utf-8').replace('Black-Follow-0.4.5-test.apk','Black-Follow-0.4.6-test.apk'),encoding='utf-8')
for name in ['README.md','TEST-RESULTS.md','GITHUB-SETUP.md']:
    path=Path(name);value=path.read_text(encoding='utf-8').replace('0.4.5','0.4.6').replace('versionCode 20','versionCode 21').replace('versionCode=20','versionCode=21')
    path.write_text(value,encoding='utf-8')
readme=Path('README.md');value=readme.read_text(encoding='utf-8')
marker="Instagram takipçi ve takip listelerine oturumun izin verdiği ölçüde erişip yerel geçmiş tutan bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.\n"
section='''\n## 0.4.6: tam liste zorunluluğu ve mobil özel veri yolları\n\n- Eksik tarama artık nihai sonuç veya tamamlanmış kontrol sayılmaz. Geçmiş, bildirim ve takipten çıkma olayları yalnızca iki liste profil toplamlarıyla birebir eşleştiğinde üretilir.\n- Web REST akışına ek olarak, WebView oturumundaki `sessionid` cihazdan çıkarılmadan özel mobil yetkilendirme başlığına dönüştürülür ve `i.instagram.com` mobil REST listeleri denenir.\n- Mobil REST de kısa kalırsa güncel `FollowersList` / `FollowingList` özel GraphQL sorguları, güncel document ID ve değişken biçimleriyle denenir. Eski `query_hash` GraphQL yöntemi geri getirilmemiştir.\n- Parola istenmez veya saklanmaz. Mobil yöntem aynı yerel Instagram oturumunu ve cihazda saklanan kararlı cihaz kimliklerini kullanır.\n- Eksik kimlikler farklı yöntem ve sonraki kontroller arasında birleşik aday havuzunda tutulur. Tam liste oluşmazsa 30 dakika sonra otomatik kesinleştirme işi planlanır ve tamamlanana kadar doğrulanmış geçmiş değiştirilmez.\n- Elle tam kontrol 45 dakikalık güvenli üst sınıra sahiptir; sunucunun rate-limit veya doğrulama yanıtı her yöntemi durdurur.\n'''
if section.strip() not in value:value=replace_once(value,marker,marker+section,'README 0.4.6 section')
readme.write_text(value,encoding='utf-8')
results=Path('TEST-RESULTS.md');value=results.read_text(encoding='utf-8')
extra='''\n## 0.4.6 tamamlama zinciri\n\n- Web REST → özel mobil REST → güncel özel mobil GraphQL → hedefli/liste araması sırası uygulanır.\n- Mobil yetkilendirme `Bearer IGT:2:<base64-json>` biçiminde yalnızca mevcut `ds_user_id`, `sessionid` ve `should_use_header_over_cookies` alanlarından oluşturulur.\n- Mobil uygulama profili Instagram 446.0.0.49.77, App ID 567067343352427 ve güncel Bloks sürüm kimliğiyle sabitlenmiştir.\n- FollowersList document ID `28479704797510738576165798526`; FollowingList document ID `161046392817718486717479294775` ve güncel kök alanları birim testleriyle doğrulanır.\n- Mobil yöntem sonuçları da sayısal kullanıcı kimliğiyle tekilleştirilir; profil toplamından fazla kişi veya tarama sonu toplam değişikliği ölümcül kalır.\n- Eksik tamamlama bir defalık hata sayılmaz; 30 dakika gecikmeli tekil JobScheduler işiyle yeniden denenir. Başarılı tam kayıt bu işi iptal eder.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
