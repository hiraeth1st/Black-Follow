package com.blackapps.follow;

import java.io.IOException;
import java.net.URLEncoder;
import org.json.JSONObject;

/** Authenticated web viewer query. A cookie or a public profile alone is not proof of login. */
public final class ViewerVerifier {
    public interface Request { JSONObject get(String path) throws Exception; }
    public static final String VIEWER_PATH="/graphql/query/?query_hash=d6f4427fbe92d846298cf93df0b937d3&variables=%7B%7D";
    public static final class Failure extends IOException {
        public final String code;
        public Failure(String code,String message){super(message+" ["+code+"]");this.code=code;}
    }
    public static String verify(String expectedOwner,Request request) throws Exception {
        if(expectedOwner==null || !expectedOwner.matches("[0-9]+")) throw new Failure("BF_OWNER","Oturum kimliği bulunamadı. Instagram girişini tamamla.");
        JSONObject root=request.get(VIEWER_PATH),data=root.optJSONObject("data");
        if(data==null) throw new Failure("BF_VIEWER_SCHEMA","Instagram oturum bilgisi beklenen biçimde gelmedi.");
        if(data.has("user") && data.isNull("user")) throw new Failure("BF_SIGN_IN","Instagram bu oturumu giriş yapılmış olarak görmüyor.");
        JSONObject user=data.optJSONObject("user");
        if(user==null) throw new Failure("BF_VIEWER_SCHEMA","Instagram oturum bilgisi beklenen biçimde gelmedi.");
        String username=user.optString("username","");
        if(!username.matches("[A-Za-z0-9._]{1,30}")) throw new Failure("BF_VIEWER_NAME","Instagram oturum kullanıcı adını göndermedi.");
        String id=user.optString("id",user.optString("pk",""));
        if(id.isEmpty()) {
            // Resolve the username that the authenticated viewer query returned; never trust a user-entered name.
            JSONObject p=ProfileLookup.resolve(username,request);
            if(!username.equalsIgnoreCase(p.optString("username",""))) throw new Failure("BF_IDENTITY","Oturum ve profil bilgisi uyuşmuyor. Yeniden giriş yap.");
            id=p.optString("id",p.optString("pk",""));
        }
        if(!expectedOwner.equals(id)) throw new Failure("BF_IDENTITY","Oturum ve profil kimliği uyuşmuyor. Yeniden giriş yap.");
        return username;
    }
}
