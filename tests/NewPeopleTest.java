import com.blackapps.follow.NewPeople;
import com.blackapps.follow.RequestTrace;
public class NewPeopleTest {
    private static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        NewPeople p=new NewPeople();
        p.add(true,"followers","added","old_follower");p.add(true,"following","added","old_following");
        check(p.total()==0,"baseline never generates new-person alerts");
        p.add(false,"followers","removed","gone");check(p.total()==0,"removals alone do not notify");
        p.add(false,"following","added","firat");check(p.total()==1 && p.body().contains("@firat takip edildi"),"18 to 19 new following is named");
        p.add(false,"followers","added","ali");check(p.followers==1 && p.following==1,"counts separated by relation type");
        check(p.body().contains("@ali takipçi olarak eklendi"),"new follower wording");
        p.add(false,"invalid","added","ignored");check(p.total()==2,"unknown relation ignored");
        for(int i=0;i<30;i++)p.add(false,"followers","added","person"+i);
        check(p.total()==32 && p.followers==31,"bounded text retains complete counts");
        check(p.body().split("\n").length==16 && p.body().contains("Diğer 17 kişi"),"large changes produce bounded notification with remainder");
        check(new NewPeople().total()==0,"next scan starts empty, no historic repeats");
        check(RequestTrace.stage("/api/v1/users/web_profile_info/?username=secret").equals("Profil sayıları (web)"),"profile stage");
        check(RequestTrace.stage("/api/v1/friendships/123/followers/?max_id=secret").equals("Takipçi listesi"),"followers stage");
        check(RequestTrace.stage("/api/v1/friendships/123/following/").equals("Takip edilenler listesi"),"following stage");
        check(RequestTrace.stage("/graphql/query/?token=secret").equals("Oturum doğrulama"),"viewer stage");
        String d=RequestTrace.detail("/unknown?sessionid=secret",429,"secret");
        check(!d.contains("secret") && !d.contains("sessionid") && d.contains("HTTP 429"),"diagnostics cannot leak supplied data");
        check(RequestTrace.detail("",-1,"").contains("HTTP yanıtı yok"),"no invented HTTP response");
        System.out.println("PASS: "+checks+" new-person notification and safe diagnostic checks");
    }
}
