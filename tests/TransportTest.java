import com.blackapps.follow.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Runs the actual InstagramClient against in-process HTTPS response fixtures, without a live account. */
public class TransportTest {
    static int checks,requests;static Fixture fixture;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static class Fixture extends HttpURLConnection {
        int status;String body;boolean closed;Map<String,List<String>> headers=new HashMap<>();
        Fixture(int status,String body)throws Exception{super(new URL("https://www.instagram.com"));this.status=status;this.body=body;}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed=true;}
        public int getResponseCode(){return status;}public String getContentType(){return "application/json";}
        public InputStream getErrorStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        public InputStream getInputStream(){return getErrorStream();}
        public String getHeaderField(String name){List<String> v=headers.get(name);return v==null?null:v.get(0);}
        public Map<String,List<String>> getHeaderFields(){return headers;}
    }
    static InstagramClient client(){return new InstagramClient(new android.content.Context(),"123",System.currentTimeMillis()+60000);}
    static InstagramClient.AccessError denied(int status,String body)throws Exception{
        fixture=new Fixture(status,body);requests=0;
        try{client().readProfile(new Store.Account());throw new AssertionError("request should be denied");}
        catch(InstagramClient.AccessError e){check(requests==1 && fixture.closed,"denial stops after one request and closes connection");return e;}
    }
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){requests++;return fixture;}}:null);
        InstagramClient.AccessError e=denied(429,"{\"message\":\"private server response DO_NOT_LOG\"}");
        check(e.rate && e.retryAfterMs==-1,"429 without duration retains unknown server wait");
        check(e.getMessage().contains("HTTP 429") && e.getMessage().contains("Profil sayıları (web)"),"rate failure retains exact stage and HTTP status");
        check(!e.getMessage().contains("DO_NOT_LOG") && !Session.lastDetail.contains("target"),"cookies, body and username absent from diagnostics");
        e=denied(403,"{\"message\":\"challenge_required\"}");
        check(e.auth && !e.rate && e.getMessage().contains("BF_CHALLENGE"),"403 body parsed before generic forbidden gate");
        e=denied(400,"{\"message\":\"feedback_required\",\"feedback_title\":\"Restricted\"}");
        check(e.auth && !e.rate && e.getMessage().contains("BF_ACTION_BLOCK"),"feedback no longer fabricates rate countdown");
        e=denied(200,"{\"message\":\"Please wait a few minutes before you try again.\",\"status\":\"fail\"}");
        check(e.rate,"200 wait instruction still stops request");
        fixture=new Fixture(200,"{\"data\":{\"user\":{\"id\":\"456\",\"username\":\"target\",\"edge_followed_by\":{\"count\":28},\"edge_follow\":{\"count\":19}}},\"challenge\":null,\"checkpoint_url\":null,\"feedback_title\":null}");
        fixture.headers.put("set-cookie",Arrays.asList("test_cookie=updated"));requests=0;
        InstagramClient.Profile p=client().readProfile(new Store.Account());
        check(p.followers==28 && p.following==19 && requests==1,"null optional restriction fields do not reject valid profile");
        check(android.webkit.CookieManager.writes==1,"case-insensitive response cookie headers retained");
        check(fixture.closed,"successful connection is closed");
        fixture=new Fixture(429,"{}");fixture.headers.put("Retry-After",Arrays.asList("120"));
        try{client().readProfile(new Store.Account());throw new AssertionError("must stop");}
        catch(InstagramClient.AccessError wait){check(wait.rate && wait.retryAfterMs==120000,"transport preserves actual Retry-After");}
        System.out.println("PASS: "+checks+" fixture-based HTTP transport checks");
    }
}
