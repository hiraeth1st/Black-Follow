import com.blackapps.follow.WebSession;
public class WebSessionTest {
    private static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static String verify(String before,String after,String owner,String current,boolean session,String result){return WebSession.verifiedName(before,after,owner,current,session,result);}
    public static void main(String[] args){
        String url="https://www.instagram.com/azizaksaz/";
        check(verify(url,url,"123","123",true,"\"viewer_test\"").equals("viewer_test"),"own navigation username accepted on another profile");
        check(verify(url,url,"123","456",true,"\"viewer_test\"").isEmpty(),"owner switch during evaluation rejected");
        check(verify(url,url,"123","123",false,"\"viewer_test\"").isEmpty(),"rendered navigation without session rejected");
        check(verify(url,url,"bad","bad",true,"\"viewer_test\"").isEmpty(),"invalid owner rejected");
        check(verify(url,url+"followers/","123","123",true,"\"viewer_test\"").isEmpty(),"navigation race rejected");
        for(String bad:new String[]{"http://www.instagram.com/","https://www.instagram.com.evil.example/","https://evil.example/","https://user@www.instagram.com/","https://www.instagram.com:444/","https://www.instagram.com/accounts/login/","https://www.instagram.com/challenge/123/","https://www.instagram.com/checkpoint/","https://www.instagram.com/oauth/", "broken"})
            check(!WebSession.trusted(bad),"untrusted or authentication page: "+bad);
        for(String bad:new String[]{"null","{}","[]","true","123","\"\"","\"name/path\"","\"bad name\""})
            check(verify(url,url,"123","123",true,bad).isEmpty(),"invalid script result: "+bad);
        check(WebSession.trusted("https://instagram.com/"),"official apex origin accepted");
        System.out.println("PASS: "+checks+" WebView session identity checks");
    }
}
