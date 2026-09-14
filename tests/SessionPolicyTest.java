import com.blackapps.follow.*;
import android.content.*;
import android.webkit.CookieManager;

public class SessionPolicyTest {
    private static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static Context old(String source,long deadline){Context c=new Context();Session.prefs(c).edit().putLong("blocked_until",deadline).putString("blocked_source",source).putInt("rate_failures",8).putLong("last_verify_attempt",System.currentTimeMillis()).apply();return c;}
    public static void main(String[] args){
        long future=System.currentTimeMillis()+7200000;
        Context local=old("Instagram süre bildirmedi; uygulamanın kademeli beklemesi",future);
        check(!Session.blocked(local),"old two-hour local timer removed on first read");
        check(Session.manualRequired(local),"old rate response pauses only automatic data checks");
        check(Session.prefs(local).getInt("rate_failures",0)==0,"exponential failure counter removed");
        check(Session.prefs(local).getLong("last_verify_attempt",0)==0,"old login timer removed");
        check(!Session.waitMessage(local).contains("Yeniden deneme:"),"no invented retry date");
        check(!Session.displayStatus(local,"old [BF_RATE_WAIT]").contains("[BF_RATE_WAIT]"),"stale account countdown replaced");
        Context legacy=old("",future);check(!Session.blocked(legacy),"legacy unlabelled app timer removed");
        Context expired=old("",1);check(!Session.manualRequired(expired),"expired legacy timer does not suspend automatic checks");
        Context server=old("Instagram yanıtındaki bekleme süresi",future);
        check(Session.blocked(server),"server deadline survives upgrade");
        check(Session.prefs(server).getLong("blocked_until",0)==future,"server deadline unchanged");
        check(Session.blocked(old("unrecognized source",future)),"unknown future source retained conservatively");
        Session.recordRate(server,new InstagramClient.AccessError(-1));
        check(Session.prefs(server).getLong("blocked_until",0)==future,"missing header cannot erase prior server deadline");
        Session.dataSucceeded(server);check(Session.blocked(server),"success cannot erase an active server deadline");
        Context fresh=new Context();Session.recordRate(fresh,new InstagramClient.AccessError(-1));
        check(!Session.blocked(fresh),"first 429 without Retry-After creates no timer");
        for(int i=0;i<20;i++)Session.recordRate(fresh,new InstagramClient.AccessError(-1));
        check(!Session.blocked(fresh),"repeated unknown-duration 429 creates no exponential timer");
        check(Session.manualRequired(fresh),"429 stops unattended retries");
        Session.dataSucceeded(fresh);check(!Session.manualRequired(fresh),"successful data check resumes background work");
        Session.recordRate(fresh,new InstagramClient.AccessError(120000));
        check(Session.blocked(fresh),"new server Retry-After still enforced");
        check(Session.waitMessage(fresh).contains("Yeniden deneme:"),"date shown only for server deadline");
        Context zero=new Context();Session.recordRate(zero,new InstagramClient.AccessError(0));
        check(!Session.blocked(zero)&&Session.manualRequired(zero),"zero header permits manual action without automatic storm");
        Session.prefs(local).edit().putString("owner","123").putString("viewer_name","fixture").putLong("verified_at",1L).putBoolean("paused",false).apply();
        CookieManager.value="ds_user_id=123; sessionid=fixture-session";
        check(Session.authenticated(local,"123"),"verified session does not expire after an arbitrary ten minutes");
        CookieManager.value="ds_user_id=456; sessionid=fixture-session";
        check(!Session.authenticated(local,"123"),"account switch invalidates identity");
        CookieManager.value="ds_user_id=123";check(!Session.authenticated(local,"123"),"missing session cookie rejected");
        CookieManager.value="ds_user_id=123; sessionid=fixture-session";
        Session.pause(local,"challenge");check(!Session.authenticated(local,"123"),"paused authentication still blocks data requests");
        check(Session.globalStatus(local).equals("challenge"),"challenge shown before rate status");
        System.out.println("PASS: "+checks+" stored session/rate policy checks");
    }
}
