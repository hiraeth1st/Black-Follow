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
    static Fixture graphqlPage(String kind,int expected,int first,int count,String cursor,boolean more)throws Exception {
        org.json.JSONArray edges=new org.json.JSONArray();
        for(int id=first;id<first+count;id++)edges.put(new org.json.JSONObject().put("node",new org.json.JSONObject().put("id",""+id).put("username","person"+id)));
        org.json.JSONObject connection=new org.json.JSONObject().put("count",expected).put("edges",edges).put("page_info",new org.json.JSONObject().put("end_cursor",cursor).put("has_next_page",more));
        return new Fixture(200,new org.json.JSONObject().put("data",new org.json.JSONObject().put("user",new org.json.JSONObject().put("id","456").put(kind.equals("followers")?"edge_followed_by":"edge_follow",connection))).toString());
    }
    static void graphqlPages(String kind,int first,int count)throws Exception {
        int size=kind.equals("followers")?12:24;
        for(int offset=0;offset<count;offset+=size)responses.add(graphqlPage(kind,count,first+offset,Math.min(size,count-offset),"gql +/="+offset,offset+size<count));
    }
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
        responses.add(new Fixture(200,"{\"users\":[]}"));
        responses.add(graphqlPage("followers",3,1,2,"",false));
        try {client().snapshot(account(),profile(3,0),0);throw new AssertionError("partial snapshot accepted");}
        catch(IOException e){check(e.getMessage().contains("2/3")&&e.getMessage().contains("BF_LIST_PARTIAL"),"duplicates never disguise missing unique records");}
        check(requests==4,"incomplete followers read following then attempt one independent traversal");
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
        responses.clear();requests=0;
        // Nine follower pages total only 182 identities although the profile says 200.
        for(int page=0;page<9;page++){
            org.json.JSONArray users=new org.json.JSONArray();int first=page*21+1,last=Math.min(182,first+20);
            for(int id=first;id<=last;id++)users.put(new org.json.JSONObject().put("pk",""+id).put("username","person"+id));
            responses.add(new Fixture(200,new org.json.JSONObject().put("users",users).put("next_max_id",page==8?"":"p"+(page+1)).toString()));
        }
        overlappingPages(10000,794);responses.add(graphqlPage("followers",200,1,182,"",false));int pageRequests=responses.size();List<String> previews=new ArrayList<>();
        InstagramClient partialClient=new InstagramClient(new android.content.Context(),"123",System.currentTimeMillis()+90000);
        partialClient.observeLists((kind,people,expected)->previews.add(kind+":"+people.size()+"/"+expected));
        try{partialClient.snapshot(account(),profile(200,794),0);throw new AssertionError("partial scan returned complete snapshot");}
        catch(IOException e){check(e.getMessage().contains("182/200")&&e.getMessage().contains("794/794")&&e.getMessage().contains("BF_LIST_PARTIAL"),"both partial and available totals reported");}
        check(previews.equals(Arrays.asList("followers:182/200","following:794/794","followers:182/200")),"separate previews retained when both methods return 182 followers");
        check(requests==pageRequests&&responses.isEmpty(),"one alternate traversal; no restart loop or unnecessary following refetch");
        responses.clear();requests=0;previews.clear();
        responses.add(new Fixture(200,"{\"users\":[{\"pk\":\"1\",\"username\":\"a\"}]}"));responses.add(new Fixture(429,"{}"));
        try{partialClient.snapshot(account(),profile(2,1),0);throw new AssertionError("rate ignored after preview");}
        catch(InstagramClient.AccessError e){check(e.rate&&requests==2&&previews.equals(Arrays.asList("followers:1/2")),"completed follower preview survives subsequent rate denial with no further request");}
        queued=false;
    }
    static void independentTraversalTests()throws Exception {
        queued=true;responses.clear();paths.clear();requests=0;
        overlappingPages(1,178);overlappingPages(10000,787);
        graphqlPages("followers",1,200);graphqlPages("following",10000,794);responses.add(profileFixture(200,794));
        List<String> previews=new ArrayList<>(),progress=new ArrayList<>();
        InstagramClient c=new InstagramClient(new android.content.Context(),"123",System.currentTimeMillis()+180000,(kind,page,got,total)->progress.add(kind+":"+got));
        c.observeLists((kind,people,total)->previews.add(kind+":"+people.size()));
        InstagramClient.Snapshot full=c.snapshot(account(),profile(200,794),0);
        check(full.followers.size()==200&&full.following.size()==794,"178/200 and 787/794 recover through independent complete GraphQL traversals");
        check(full.followers.containsKey("200")&&full.following.containsKey("10793"),"both GraphQL final pages included");
        check(previews.equals(Arrays.asList("followers:178","following:787","followers:200","following:794")),"full alternatives replace individual previews");
        check(progress.contains("followers_graphql:200")&&progress.contains("following_graphql:794"),"UI distinguishes second traversal and progress");
        check(responses.isEmpty()&&paths.get(paths.size()-1).equals("/graphql/query/?null"),"final profile validation only after both complete alternatives");
        List<org.json.JSONObject> vars=new ArrayList<>();
        for(String path:paths)if(path.contains("query_hash=")) {
            String encoded=path.substring(path.indexOf("&variables=")+11);vars.add(new org.json.JSONObject(URLDecoder.decode(encoded,"UTF-8")));
        }
        check(vars.size()==51&&vars.get(0).getInt("first")==12&&vars.get(17).getInt("first")==24,"bounded documented page sizes for each connection");
        check(!vars.get(0).has("after")&&!vars.get(17).has("after")&&vars.get(1).getString("after").equals("gql +/=0"),"independent cursors start empty and opaque special characters round-trip");
        responses.clear();paths.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(graphqlPage("followers",3,2,2,"",false));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("union accepted");}
        catch(InstagramClient.PartialLists e){check(e.getMessage().contains("2/3")&&requests==3,"two partial sets whose union matches count remain incomplete");}
        responses.clear();requests=0;previews.clear();
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(new Fixture(429,"{}"));
        InstagramClient limited=client();limited.observeLists((kind,people,total)->previews.add(kind+":"+people.size()));
        try{limited.snapshot(account(),profile(3,0),0);throw new AssertionError("429 ignored");}
        catch(InstagramClient.AccessError e){check(e.rate&&requests==3&&previews.size()==2&&e.getMessage().contains("ikinci yöntem"),"GraphQL rate error stops immediately with original previews and correct stage");}
        responses.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(new Fixture(200,"{\"errors\":[{\"message\":\"challenge_required\"}]}"));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("GraphQL challenge ignored");}
        catch(InstagramClient.AccessError e){check(e.auth&&requests==3,"GraphQL error envelope respects challenge restriction");}
        responses.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(new Fixture(200,"{\"data\":{\"user\":null}}"));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("missing connection accepted");}
        catch(ViewerVerifier.Failure e){check(e.code.equals("BF_LIST_SCHEMA")&&requests==3,"missing connection never treated as empty full list");}
        responses.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(graphqlPage("followers",3,1,1,"repeat",true));responses.add(graphqlPage("followers",3,2,1,"repeat",true));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("cursor loop accepted");}
        catch(IOException e){check(requests==4&&e.getMessage().contains("ikinci yöntem"),"GraphQL cursor loop stops before another request");}
        responses.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(graphqlPage("followers",4,1,3,"",false));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("changed count accepted");}
        catch(IOException e){check(e.getMessage().contains("BF_LIST_CHANGED")&&requests==3,"connection count mismatch prevents commit");}
        responses.clear();requests=0;
        overlappingPages(1,2);responses.add(new Fixture(200,"{\"users\":[]}"));responses.add(graphqlPage("followers",3,1,3,"",false));responses.add(profileFixture(4,0));
        try{client().snapshot(account(),profile(3,0),0);throw new AssertionError("changed final profile accepted");}
        catch(IOException e){check(e.getMessage().contains("kontrol sırasında değişti")&&requests==4,"final profile count change rejects even complete alternative");}
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
        independentTraversalTests();
        System.out.println("PASS: "+checks+" fixture-based HTTP transport checks");
    }
}
