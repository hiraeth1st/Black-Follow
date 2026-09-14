import com.blackapps.follow.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Runs the actual InstagramClient against in-process HTTPS response fixtures, without a live account. */
public class TransportTest {
    static int checks,requests;static Fixture fixture;
    static boolean queued;static final ArrayDeque<Fixture> responses=new ArrayDeque<>();static final List<String> paths=new ArrayList<>();
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static class Fixture extends HttpURLConnection {
        int status;String body;boolean closed;Map<String,List<String>> headers=new HashMap<>();
        Fixture(int status,String body)throws Exception{super(new URL("https://www.instagram.com"));this.status=status;this.body=body;}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed=true;}
        public int getResponseCode(){return status;}public String getContentType(){return "application/json";}
        public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
        public InputStream getErrorStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        public InputStream getInputStream(){return getErrorStream();}
        public String getHeaderField(String name){List<String> v=headers.get(name);return v==null?null:v.get(0);}
        public Map<String,List<String>> getHeaderFields(){return headers;}
    }
    static Store.Account account(){Store.Account a=new Store.Account();a.remote="456";return a;}
    static InstagramClient client(){return new InstagramClient(new android.content.Context(),"123",System.currentTimeMillis()+60000);}
    static InstagramClient.AccessError denied(int status,String body)throws Exception{
        fixture=new Fixture(status,body);requests=0;
        try{client().readProfile(account());throw new AssertionError("request should be denied");}
        catch(InstagramClient.AccessError e){check(requests==1 && fixture.closed,"denial stops after one request and closes connection");return e;}
    }
    static Fixture profileFixture(int followers,int following)throws Exception {
        return new Fixture(200,"{\"data\":{\"user\":{\"id\":\"456\",\"username\":\"target\",\"edge_followed_by\":{\"count\":"+followers+"},\"edge_follow\":{\"count\":"+following+"}}}}");
    }
    static InstagramClient.Profile profile(int followers,int following){InstagramClient.Profile p=new InstagramClient.Profile();p.id="456";p.username="target";p.followers=followers;p.following=following;return p;}
    static void overlappingPages(int first,int count)throws Exception {
        int last=first+count-1,start=first,page=0;
        while(true){int end=Math.min(start+99,last);boolean more=end<last;org.json.JSONArray users=new org.json.JSONArray();
            for(int i=start;i<=end;i++)users.put(new org.json.JSONObject().put("pk",""+i).put("username","person"+i));
            if(page==0)users.put(new org.json.JSONObject().put("pk",""+first).put("username","person"+first));
            responses.add(new Fixture(200,new org.json.JSONObject().put("users",users).put("has_more",more).put("next_max_id",more?"cursor-"+(++page):"").toString()));
            if(!more)break;start=end;
        }
    }
    static void paginationTests()throws Exception {
        queued=true;responses.clear();paths.clear();requests=0;
        overlappingPages(1,200);overlappingPages(10000,794);responses.add(profileFixture(200,794));
        int expectedRequests=responses.size();List<String> progress=new ArrayList<>();
        InstagramClient c=new InstagramClient(new android.content.Context(),"123",System.currentTimeMillis()+90000,(kind,page,got,total)->progress.add(kind+":"+got+"/"+total));
        InstagramClient.Snapshot snap=c.snapshot(account(),profile(200,794),System.currentTimeMillis());
        check(snap.followers.size()==200&&snap.following.size()==794,"200/794 snapshot completes despite overlapping pages");
        check(snap.followers.containsKey("200")&&snap.following.containsKey("10793"),"last pages remain in full snapshot");
        check(requests==expectedRequests&&responses.isEmpty(),"each page read once with one final count check");
        check(paths.stream().anyMatch(x->x.contains("max_id=cursor-1")),"pagination forwards returned cursor");
        check(progress.contains("followers:200/200")&&progress.contains("following:794/794"),"progress reports unique totals for both lists");
        responses.clear();requests=0;
        responses.add(new Fixture(200,"{\"users\":[{\"pk\":\"1\",\"username\":\"a\"},{\"pk\":\"2\",\"username\":\"b\"}],\"next_max_id\":\"p2\"}"));
        responses.add(new Fixture(200,"{\"users\":[{\"pk\":\"2\",\"username\":\"b\"}]}"));
        try {client().snapshot(account(),profile(3,0),0);throw new AssertionError("partial snapshot accepted");}
        catch(IOException e){check(e.getMessage().contains("2/3")&&e.getMessage().contains("sayfa 2"),"duplicates never disguise missing unique records");}
        check(requests==2,"incomplete followers do not proceed to following or count check");
        responses.clear();requests=0;
        responses.add(new Fixture(200,"{\"users\":[{\"pk\":null,\"id\":\"1\",\"username\":\"a\"}]}"));
        responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(profileFixture(1,0));
        check(client().snapshot(account(),profile(1,0),0).followers.size()==1,"null pk uses a valid id field");
        responses.clear();requests=0;responses.add(new Fixture(200,"{\"users\":[{\"pk\":null,\"username\":\"a\"}]}"));
        try{client().snapshot(account(),profile(1,0),0);throw new AssertionError("invalid ID accepted");}
        catch(IOException e){check(e.getMessage().contains("BF_LIST_ID")&&e.getMessage().contains("Takipçi listesi"),"invalid ID distinguished from harmless duplicate");}
        responses.clear();requests=0;
        responses.add(new Fixture(200,"{\"users\":[{\"pk\":\"1\",\"username\":\"a\"}],\"next_max_id\":\"p2\"}"));responses.add(new Fixture(429,"{}"));
        try{client().snapshot(account(),profile(2,0),0);throw new AssertionError("rate ignored");}
        catch(InstagramClient.AccessError e){check(e.rate&&requests==2,"rate denial during pagination stops without retries");}
        queued=false;
    }
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){requests++;paths.add(u.getPath()+"?"+u.getQuery());if(queued){if(responses.isEmpty())throw new AssertionError("unexpected extra request");return responses.removeFirst();}return fixture;}}:null);
        InstagramClient.AccessError e=denied(429,"{\"message\":\"private server response DO_NOT_LOG\"}");
        check(e.rate && e.retryAfterMs==-1,"429 without duration retains unknown server wait");
        check(e.getMessage().contains("HTTP 429") && e.getMessage().contains("Profil sayıları (GraphQL)"),"rate failure retains exact stage and HTTP status");
        check(!e.getMessage().contains("DO_NOT_LOG") && !Session.lastDetail.contains("target"),"cookies, body and username absent from diagnostics");
        e=denied(403,"{\"message\":\"challenge_required\"}");
        check(e.auth && !e.rate && e.getMessage().contains("BF_CHALLENGE"),"403 body parsed before generic forbidden gate");
        e=denied(400,"{\"message\":\"feedback_required\",\"feedback_title\":\"Restricted\"}");
        check(e.auth && !e.rate && e.getMessage().contains("BF_ACTION_BLOCK"),"feedback no longer fabricates rate countdown");
        e=denied(200,"{\"message\":\"Please wait a few minutes before you try again.\",\"status\":\"fail\"}");
        check(e.rate,"200 wait instruction still stops request");
        fixture=new Fixture(200,"{\"data\":{\"user\":{\"id\":\"456\",\"username\":\"target\",\"edge_followed_by\":{\"count\":28},\"edge_follow\":{\"count\":19}}},\"challenge\":null,\"checkpoint_url\":null,\"feedback_title\":null}");
        fixture.headers.put("set-cookie",Arrays.asList("test_cookie=updated"));requests=0;
        InstagramClient.Profile p=client().readProfile(account());
        check(p.followers==28 && p.following==19 && requests==1,"null optional restriction fields do not reject valid profile");
        check(android.webkit.CookieManager.writes==1,"case-insensitive response cookie headers retained");
        check(fixture.closed,"successful connection is closed");
        fixture=new Fixture(429,"{}");fixture.headers.put("Retry-After",Arrays.asList("120"));
        try{client().readProfile(account());throw new AssertionError("must stop");}
        catch(InstagramClient.AccessError wait){check(wait.rate && wait.retryAfterMs==120000,"transport preserves actual Retry-After");}
        paginationTests();
        System.out.println("PASS: "+checks+" fixture-based HTTP transport checks");
    }
}
