import com.blackapps.follow.*;
import org.json.*;
import java.net.URLDecoder;
import java.io.IOException;

public class ProfileLookupTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    interface Checked{void run()throws Exception;}
    static void fails(String code,Checked run)throws Exception{
        try{run.run();throw new AssertionError("Expected "+code);}catch(ViewerVerifier.Failure e){check(code.equals(e.code),"expected "+code+" got "+e.code);}
    }
    static class Fake implements ProfileLookup.Request {
        int gets,posts;boolean failSearch,failProfile;String query;
        JSONObject search=new JSONObject("{\"users\":[{\"user\":{\"pk\":\"8\",\"username\":\"similar_name\"}},{\"user\":{\"pk\":\"456\",\"username\":\"target\"}}]}");
        JSONObject profile=new JSONObject("{\"data\":{\"user\":{\"pk\":\"456\",\"username\":\"target\",\"is_private\":true,\"follower_count\":28,\"following_count\":19}}}");
        public JSONObject get(String path)throws Exception{gets++;if(failSearch)throw new IOException("rate gate");check(path.startsWith("/web/search/topsearch/?"),"explicit account search");check(!path.contains("count=1000"),"account lookup avoids oversized result requests");return search;}
        public JSONObject post(String path,String form)throws Exception{posts++;query=URLDecoder.decode(form,"UTF-8");check(path.equals("/graphql/query/"),"profile POST target");if(failProfile)throw new IOException("rate gate");return profile;}
    }
    public static void main(String[] args)throws Exception{
        Fake f=new Fake();JSONObject u=ProfileLookup.read("TARGET","",f);
        check(f.gets==1&&f.posts==1&&u.getInt("following_count")==19,"exact username lookup plus profile counts");
        check(f.query.contains("doc_id="+ProfileLookup.DOC_ID)&&f.query.contains("\"id\":\"456\"")&&f.query.contains("\"enable_integrity_filters\":true"),"identity and integrity options in POST");
        check(u.getBoolean("is_private"),"private metadata preserved without fabricating access");
        f=new Fake();f.profile.getJSONObject("data").getJSONObject("user").put("username","renamed");
        check(ProfileLookup.read("target","456",f).getString("username").equals("renamed")&&f.gets==0&&f.posts==1,"known identity avoids search and survives rename");
        final Fake missing=new Fake();missing.search=new JSONObject("{\"users\":[{\"user\":{\"pk\":\"8\",\"username\":\"similar\"}}]}");
        fails("BF_SEARCH_NO_EXACT",()->ProfileLookup.read("target","",missing));check(missing.posts==0,"no near-match fallback");
        final Fake wrong=new Fake();wrong.profile.getJSONObject("data").getJSONObject("user").put("pk","999");
        fails("BF_IDENTITY",()->ProfileLookup.read("target","456",wrong));
        final Fake error=new Fake();error.profile.put("errors",new JSONArray().put(new JSONObject().put("message","private server content")));
        fails("BF_PROFILE_GRAPHQL",()->ProfileLookup.read("target","456",error));
        final Fake empty=new Fake();empty.profile=new JSONObject("{\"data\":{\"user\":null}}");
        fails("BF_PROFILE_SCHEMA",()->ProfileLookup.read("target","456",empty));
        final Fake invalid=new Fake();fails("BF_PROFILE_ID",()->ProfileLookup.read("target","bad",invalid));check(invalid.gets==0&&invalid.posts==0,"invalid identity rejected before network");
        for(boolean search:new boolean[]{true,false}){
            f=new Fake();f.failSearch=search;f.failProfile=!search;
            try{ProfileLookup.read("target","",f);throw new AssertionError("denial ignored");}catch(IOException e){check(f.gets==1&&f.posts==(search?0:1),"denial propagates without alternate endpoint retry");}
        }
        System.out.println("PASS: "+checks+" authenticated profile adapter checks");
    }
}
