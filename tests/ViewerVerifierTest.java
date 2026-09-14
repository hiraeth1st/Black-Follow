import com.blackapps.follow.ViewerVerifier;
import com.blackapps.follow.ResponsePolicy;
import org.json.JSONObject;
import java.io.IOException;

public class ViewerVerifierTest {
    private static int checks;
    interface Checked {void run() throws Exception;}
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static JSONObject json(String s) {return new JSONObject(s);}
    static void failure(String code,Checked r) throws Exception {
        try {r.run();throw new AssertionError("Expected "+code);}catch(ViewerVerifier.Failure e){check(e.code.equals(code),"expected "+code+" got "+e.code);}
    }
    public static void main(String[] args) throws Exception {
        int[] calls={0};
        check(ViewerVerifier.verify("123",path->{calls[0]++;check(path.equals(ViewerVerifier.VIEWER_PATH),"web viewer endpoint");return json("{\"data\":{\"user\":{\"id\":\"123\",\"username\":\"test_user\"}}}");}).equals("test_user"),"web viewer accepts matching identity");
        check(calls[0]==1,"known id needs one authenticated request");
        check(ViewerVerifier.verify("123",path->json("{\"data\":{\"user\":{\"pk\":123,\"username\":\"test_user\"}}}")).equals("test_user"),"numeric alternate id");
        calls[0]=0;
        check(ViewerVerifier.verify("123",path->{calls[0]++;if(calls[0]==1)return json("{\"data\":{\"user\":{\"username\":\"test_user\"}}}");
            check(path.equals("/web/search/topsearch/?context=blended&query=test_user&include_reel=false&count=1000&__a=1"),"only authenticated viewer name can be resolved with bounded search");
            return json("{\"users\":[{\"user\":{\"username\":\"test_user\",\"id\":\"123\"}}]}");}).equals("test_user"),"missing id resolved from viewer username");
        check(calls[0]==2,"one optional identity lookup");
        failure("BF_OWNER",()->ViewerVerifier.verify("",path->{throw new AssertionError("must not request without owner");}));
        failure("BF_OWNER",()->ViewerVerifier.verify("abc",path->{throw new AssertionError("must reject malformed owner");}));
        failure("BF_SIGN_IN",()->ViewerVerifier.verify("123",path->json("{\"data\":{\"user\":null}}")));
        failure("BF_VIEWER_SCHEMA",()->ViewerVerifier.verify("123",path->json("{\"status\":\"ok\"}")));
        failure("BF_VIEWER_SCHEMA",()->ViewerVerifier.verify("123",path->json("{\"data\":{}}")));
        failure("BF_VIEWER_NAME",()->ViewerVerifier.verify("123",path->json("{\"data\":{\"user\":{\"id\":\"123\"}}}")));
        failure("BF_VIEWER_NAME",()->ViewerVerifier.verify("123",path->json("{\"data\":{\"user\":{\"id\":\"123\",\"username\":\"bad&injected=value\"}}}")));
        failure("BF_IDENTITY",()->ViewerVerifier.verify("123",path->json("{\"data\":{\"user\":{\"id\":\"999\",\"username\":\"test_user\"}}}")));
        calls[0]=0;
        failure("BF_SEARCH_NO_EXACT",()->ViewerVerifier.verify("123",path->{calls[0]++;return calls[0]==1?json("{\"data\":{\"user\":{\"username\":\"test_user\"}}}"):json("{\"users\":[{\"user\":{\"username\":\"someone_else\",\"id\":\"123\"}}]}");}));
        calls[0]=0;IOException denied=new IOException("denied");
        try {ViewerVerifier.verify("123",path->{calls[0]++;throw denied;});throw new AssertionError("denial ignored");}catch(IOException e){check(e==denied && calls[0]==1,"auth/transport failure is propagated, no bypass or retry");}
        check(ResponsePolicy.gate(429,"",false,false,false).equals("BF_RATE"),"429 stops all requests");
        check(ResponsePolicy.gate(400,"feedback_required",false,false,false).equals("BF_ACTION_BLOCK"),"feedback restriction is not mislabeled a timed rate limit");
        check(ResponsePolicy.gate(200,"challenge_required",false,false,false).equals("BF_CHALLENGE"),"200 challenge is not success");
        check(ResponsePolicy.gate(200,"",true,false,false).equals("BF_CHALLENGE"),"challenge object stops requests");
        check(ResponsePolicy.gate(400,"checkpoint_required",false,false,false).equals("BF_CHALLENGE"),"checkpoint requires user action");
        check(ResponsePolicy.gate(200,"login_required",false,false,false).equals("BF_SIGN_IN"),"JSON login requirement");
        check(ResponsePolicy.gate(401,"",false,false,false).equals("BF_SIGN_IN"),"401 is logged out");
        check(ResponsePolicy.gate(403,"",false,false,false).equals("BF_FORBIDDEN"),"403 access restriction");
        check(ResponsePolicy.gate(302,"",false,false,false).equals("BF_REDIRECT"),"redirect never treated as JSON success");
        check(ResponsePolicy.gate(400,"",false,false,false).isEmpty(),"generic 400 stays diagnosable by status");
        check(ResponsePolicy.gate(200,"",false,false,false).isEmpty(),"normal response continues");
        check(ResponsePolicy.gate(403,"challenge_required",false,false,false).equals("BF_CHALLENGE"),"403 body still identifies challenge");
        check(ResponsePolicy.gate(200,"",false,false,true).equals("BF_ACTION_BLOCK"),"feedback object stops requests without inventing a timer");
        check(ResponsePolicy.gate(400,"Please wait a few minutes before you try again.",false,false,false).equals("BF_RATE"),"textual wait instruction is respected");
        check(ResponsePolicy.gate(429,"feedback_required",true,false,true).equals("BF_RATE"),"HTTP 429 always preserves rate wait");
        System.out.println("PASS: "+checks+" session regression checks");
    }
}
