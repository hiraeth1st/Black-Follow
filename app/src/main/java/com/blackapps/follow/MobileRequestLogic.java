package com.blackapps.follow;

import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Pure request construction shared by the private mobile REST and GraphQL fallbacks. */
public final class MobileRequestLogic {
    public static final String API_ORIGIN="https://i.instagram.com";
    public static final String APP_ID="567067343352427";
    public static final String APP_VERSION="446.0.0.49.77";
    public static final String VERSION_CODE="385211303";
    public static final String BLOKS_VERSION="935a519904e9017324cdedb64a283a3c2c1a3d5b0bbc698b451f5aef72cc11df";
    public static final String USER_AGENT="Instagram 446.0.0.49.77 Android (34/14; 480dpi; 1344x2992; Google/google; Pixel 8 Pro; husky; husky; tr_TR; 385211303)";
    public static final String FOLLOWERS_DOC_ID="28479704797510738576165798526";
    public static final String FOLLOWING_DOC_ID="161046392817718486717479294775";
    public static final String FOLLOWERS_ROOT="xdt_api__v1__friendships__followers";
    public static final String FOLLOWING_ROOT="xdt_api__v1__friendships__following";

    private MobileRequestLogic() {}

    private static void validate(String id,String kind) {
        if(id==null||!id.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz hesap kimliği.");
        if(!"followers".equals(kind)&&!"following".equals(kind))throw new IllegalArgumentException("Geçersiz liste türü.");
    }
    private static String enc(String value)throws Exception{return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8.name());}

    public static String authorization(String owner,String sessionid) {
        if(owner==null||!owner.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz oturum kimliği.");
        if(sessionid==null||sessionid.length()<20)throw new IllegalArgumentException("Instagram oturum bilgisi eksik.");
        JSONObject data=new JSONObject();
        data.put("ds_user_id",owner);
        data.put("sessionid",sessionid);
        data.put("should_use_header_over_cookies",true);
        String encoded=Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8));
        return "Bearer IGT:2:"+encoded;
    }

    public static String restUrl(String id,String kind,String rankToken,String cursor,String order)throws Exception {
        validate(id,kind);
        String path=RelationshipRequest.page(id,kind,rankToken,cursor,order);
        return API_ORIGIN+path;
    }

    public static String friendlyName(String kind){validate("1",kind);return "followers".equals(kind)?"FollowersList":"FollowingList";}
    public static String rootName(String kind){validate("1",kind);return "followers".equals(kind)?FOLLOWERS_ROOT:FOLLOWING_ROOT;}
    public static String docId(String kind){validate("1",kind);return "followers".equals(kind)?FOLLOWERS_DOC_ID:FOLLOWING_DOC_ID;}

    public static JSONObject variables(String id,String kind,String rankToken,String cursor,String order) {
        validate(id,kind);
        JSONObject requestData=new JSONObject();
        JSONObject variables=new JSONObject();
        if("followers".equals(kind)) {
            requestData.put("rank_token",rankToken).put("enableGroups",true);
            variables.put("user_id",id)
                .put("skip_suggested_users",true)
                .put("skip_more_groups_available",true)
                .put("skip_friendship_followers_fields",true)
                .put("request_data",requestData)
                .put("skip_page_size",true)
                .put("skip_pending_admins",true)
                .put("skip_has_more",true)
                .put("search_surface","follow_list_page")
                .put("query","")
                .put("skip_big_list",true)
                .put("include_unseen_count",true);
        } else {
            requestData.put("search_surface","follow_list_page").put("rank_token",rankToken).put("includes_hashtags",true);
            variables.put("user_id",id)
                .put("skip_use_clickable_see_more",true)
                .put("skip_preview_hashtags",true)
                .put("skip_should_limit_list_of_followers",true)
                .put("skip_pending_admins",true)
                .put("skip_more_groups_available",true)
                .put("skip_friendship_followers_fields",false)
                .put("request_data",requestData)
                .put("skip_page_size",true)
                .put("skip_friend_requests",true)
                .put("skip_big_list",true)
                .put("query","")
                .put("include_profile_update_info",true)
                .put("skip_suggested_users",true)
                .put("include_unseen_count",true)
                .put("skip_has_more",true)
                .put("enable_groups",true)
                .put("skip_hashtag_count",true);
        }
        if(cursor!=null&&!cursor.isEmpty()) {
            try {variables.put("max_id",Long.parseLong(cursor));}
            catch(NumberFormatException ignored){variables.put("max_id",cursor);}
        }
        if(order!=null&&!order.isEmpty())variables.put("order",order);
        return variables;
    }

    public static String graphqlForm(String id,String kind,String rankToken,String cursor,String order)throws Exception {
        JSONObject variables=variables(id,kind,rankToken,cursor,order);
        StringBuilder form=new StringBuilder();
        append(form,"method","post");append(form,"pretty","false");append(form,"format","json");
        append(form,"server_timestamps","true");append(form,"locale","user");
        append(form,"fb_api_req_friendly_name",friendlyName(kind));
        append(form,"enable_canonical_naming","true");append(form,"enable_canonical_variable_overrides","true");
        append(form,"enable_canonical_naming_ambiguous_type_prefixing","true");
        append(form,"variables",variables.toString());append(form,"client_doc_id",docId(kind));
        return form.toString();
    }
    private static void append(StringBuilder out,String key,String value)throws Exception {
        if(out.length()>0)out.append('&');out.append(enc(key)).append('=').append(enc(value));
    }
}
