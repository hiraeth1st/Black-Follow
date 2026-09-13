import com.blackapps.follow.HistoryText;
import com.blackapps.follow.ProfileLinks;
import java.io.StringWriter;
import java.time.*;
import java.util.*;

public class HistoryTextTest {
    private static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static long at(String day){return Instant.parse(day+"T12:00:00Z").toEpochMilli();}
    public static void main(String[] args)throws Exception {
        TimeZone zone=TimeZone.getTimeZone("Europe/Istanbul");
        String first=HistoryText.event("following","added","ali",1,0,zone);
        check(first.equals("@ali takip edildi"),"first observed follow");
        String again=HistoryText.event("following","added","mehmet",2,at("2026-01-20"),zone);
        check(again.contains("20 Ocak 2026")&&again.contains("2. takip"),"prior removal and second follow");
        check(HistoryText.event("following","added","renamed_user",3,at("2026-03-01"),zone).contains("izleme süresinde 3. takip"),"third follow is observed count");
        check(HistoryText.event("followers","added","mehmet",2,at("2026-01-20"),zone).contains("takipçi olarak"),"followers and following have distinct labels");
        check(!HistoryText.event("following","removed","mehmet",2,at("2026-01-20"),zone).contains("2. takip"),"removed is not another follow");
        StringWriter out=new StringWriter();HistoryText.Report r=new HistoryText.Report(out,"example",at("2026-03-30"),at("2026-01-19"),at("2026-01-19"),zone);
        r.counts(at("2026-01-19"),28,28,"profile");r.counts(at("2026-01-19")+1000,28,28,"full");
        r.event(at("2026-01-20"),"following","added","ali",1,0,at("2026-01-19"));
        r.event(at("2026-01-20")+1000,"following","removed","mehmet",0,0,at("2026-01-19"));
        r.event(at("2026-01-21"),"following","added","mehmet",1,at("2026-01-20"),at("2026-01-20"));
        r.event(at("2026-03-30"),"following","added","mehmet",3,at("2026-03-01"),at("2026-03-29"));r.finish();String text=out.toString();
        check(text.contains("28 takipçi / 28 takip"),"baseline counts exported");
        check(text.split("28 takipçi / 28 takip",-1).length==2,"same-day identical counts not duplicated");
        check(text.indexOf("\n19 Ocak 2026\n")<text.indexOf("\n20 Ocak 2026\n")&&text.indexOf("\n20 Ocak 2026\n")<text.indexOf("\n21 Ocak 2026\n"),"chronological date headings");
        check(text.contains("izleme süresinde 3. takip")&&text.contains("4 hareket"),"all events and recurrence exported");
        check(text.contains("Europe/Istanbul")&&text.contains("TESPİT")&&text.contains("bilinmiyor"),"time zone and uncertainty preserved");
        StringWriter many=new StringWriter();HistoryText.Report full=new HistoryText.Report(many,"many",at("2026-03-30"),0,0,zone);
        for(int i=0;i<205;i++)full.event(at("2026-01-20")+i*1000,"following","added","user"+i,1,0,0);full.finish();
        check(many.toString().contains("@user204")&&many.toString().contains("205 hareket"),"report is not limited to UI page size");
        check(ProfileLinks.profile("firat.test").equals("https://www.instagram.com/firat.test/"),"safe clickable profile URL");
        check(ProfileLinks.avatar("https://scontent.cdninstagram.com/path.jpg?x=1"),"Instagram CDN image");
        check(ProfileLinks.avatar("https://scontent.xx.fbcdn.net/photo.jpg"),"Meta CDN image");
        check(!ProfileLinks.avatar("http://scontent.cdninstagram.com/a"),"no cleartext images");
        check(!ProfileLinks.avatar("https://cdninstagram.com.evil.test/a"),"host suffix confusion blocked");
        check(!ProfileLinks.avatar("https://127.0.0.1/a")&&!ProfileLinks.avatar("file:///tmp/a"),"local network/file URLs blocked");
        check(!ProfileLinks.avatar("https://user:pass@scontent.cdninstagram.com/a"),"userinfo blocked");
        try{ProfileLinks.profile("x/?bad=1");throw new AssertionError("unsafe profile path");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("PASS: "+checks+" history export and profile link checks");
    }
}
