import com.blackapps.follow.*;
import android.content.*;
import android.webkit.CookieManager;
public class MonitorPolicyTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static Context fresh(){
        Context c=new Context();Session.prefs(c).edit().putString("owner","123").putLong("verified_at",1).putBoolean("paused",false).apply();
        CookieManager.value="ds_user_id=123; sessionid=fixture-session";
        InstagramClient.requests=0;InstagramClient.profileFailure=null;InstagramClient.snapshotFailure=null;
        Store.profiles=0;Store.commits=0;ChangeNotifications.posts=0;Store.ACCOUNT.nextDue=Long.MAX_VALUE;
        return c;
    }
    public static void main(String[] args){
        Context c=fresh();Session.prefs(c).edit().putLong("blocked_until",System.currentTimeMillis()+7200000).putString("blocked_source","Instagram süre bildirmedi; uygulamanın kademeli beklemesi").apply();
        Monitor.profile(c,1);
        check(InstagramClient.requests==1&&Store.profiles==1,"manual profile executes despite old local timer and future next_due");
        check(!Session.manualRequired(c),"successful manual profile resumes background checks");
        check(!Monitor.BUSY.get(),"worker lock released after success");
        c=fresh();InstagramClient.profileFailure=new InstagramClient.AccessError("rate",false,true);
        String result=Monitor.profile(c,1);
        check(result.contains("BF_RATE_MANUAL")&&!result.contains("BF_RATE_WAIT"),"unknown-duration 429 returns no countdown");
        check(!Session.blocked(c)&&Session.manualRequired(c),"unknown rate stops automatic work only");
        Store.ACCOUNT.nextDue=0;Monitor.run(c,0,false);
        check(InstagramClient.requests==1,"background work sends no request after rate response");
        Monitor.profile(c,1);check(InstagramClient.requests==2,"explicit manual retry sends one request without local timer");
        c=fresh();Session.recordRate(c,new InstagramClient.AccessError("server",false,true,120000));
        Monitor.profile(c,1);check(InstagramClient.requests==0,"manual refresh respects actual server deadline");
        check(Session.authenticated(c,"123"),"data deadline does not invalidate verified login");
        c=fresh();CookieManager.value="ds_user_id=456; sessionid=fixture-session";
        Monitor.profile(c,1);check(InstagramClient.requests==0&&Session.prefs(c).getBoolean("paused",false),"switched owner stops before request");
        c=fresh();InstagramClient.snapshotFailure=new InstagramClient.AccessError("list rate",false,true);
        Monitor.run(c,1,true);
        check(Store.profiles==1&&Store.commits==0&&ChangeNotifications.posts==0,"rate during lists preserves profile and commits no partial history or notifications");
        check(Session.manualRequired(c),"list rate pauses automatic work");
        InstagramClient.snapshotFailure=null;Monitor.run(c,1,true);
        check(Store.commits==1&&ChangeNotifications.posts==1&&!Session.manualRequired(c),"successful manual complete scan commits and resumes");
        c=fresh();Session.pause(c,"challenge");Monitor.profile(c,1);
        check(InstagramClient.requests==0,"challenge pause still prevents requests");
        check(!Monitor.BUSY.get(),"worker lock released after early rejection");
        System.out.println("PASS: "+checks+" actual monitor flow regression checks");
    }
}
