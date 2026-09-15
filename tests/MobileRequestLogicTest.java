import com.blackapps.follow.MobileRequestLogic;
import org.json.JSONObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class MobileRequestLogicTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        String session="123456789%3Along_session_value_that_is_long_enough";
        String auth=MobileRequestLogic.authorization("123456789",session);
        check(auth.startsWith("Bearer IGT:2:"),"mobile authorization prefix");
        String decoded=new String(Base64.getDecoder().decode(auth.substring("Bearer IGT:2:".length())),StandardCharsets.UTF_8);
        JSONObject authJson=new JSONObject(decoded);
        check(authJson.getString("ds_user_id").equals("123456789"),"authorization carries viewer id");
        check(authJson.getString("sessionid").equals(session),"authorization carries current sessionid");
        check(authJson.getBoolean("should_use_header_over_cookies"),"authorization selects header session");
        String rest=URLDecoder.decode(MobileRequestLogic.restUrl("77","followers","123_uuid","cursor","date_followed_latest"),"UTF-8");
        check(rest.startsWith("https://i.instagram.com/api/v1/friendships/77/followers/"),"mobile REST origin and path");
        check(rest.contains("count=200")&&rest.contains("rank_token=123_uuid")&&rest.contains("max_id=cursor"),"mobile REST cursor shape");
        JSONObject followers=MobileRequestLogic.variables("77","followers","rank","12","");
        check(followers.getString("user_id").equals("77"),"followers GraphQL target");
        check(followers.getJSONObject("request_data").getString("rank_token").equals("rank"),"followers GraphQL rank token");
        check(followers.getLong("max_id")==12L,"numeric GraphQL cursor");
        JSONObject following=MobileRequestLogic.variables("77","following","rank","","date_followed_earliest");
        check(following.getJSONObject("request_data").getBoolean("includes_hashtags"),"following GraphQL request data");
        check(following.getString("order").equals("date_followed_earliest"),"following GraphQL order");
        String form=URLDecoder.decode(MobileRequestLogic.graphqlForm("77","following","rank","",""),"UTF-8");
        check(form.contains("fb_api_req_friendly_name=FollowingList"),"current following friendly name");
        check(form.contains("client_doc_id="+MobileRequestLogic.FOLLOWING_DOC_ID),"current following document id");
        check(MobileRequestLogic.rootName("followers").equals(MobileRequestLogic.FOLLOWERS_ROOT),"followers root field");
        check(MobileRequestLogic.APP_ID.equals("567067343352427"),"private mobile app id");
        check(MobileRequestLogic.USER_AGENT.contains(MobileRequestLogic.APP_VERSION),"current mobile app profile");
        System.out.println("PASS: "+checks+" private-mobile request checks");
    }
}
