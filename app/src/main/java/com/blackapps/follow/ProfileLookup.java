package com.blackapps.follow;

import org.json.*;
import java.net.URLEncoder;

/** Authenticated profile adapter. One search + one profile query; never fall back after a denial. */
public final class ProfileLookup {
    public static final String DOC_ID="27937681195819736";
    public interface Request extends ViewerVerifier.Request {JSONObject post(String path,String form)throws Exception;}
    public static JSONObject resolve(String username,ViewerVerifier.Request request)throws Exception {
        if(!username.matches("[A-Za-z0-9._]{1,30}"))throw new ViewerVerifier.Failure("BF_PROFILE_NAME","Geçerli kullanıcı adı gerekli.");
        JSONObject root=request.get("/web/search/topsearch/?context=blended&query="+URLEncoder.encode(username,"UTF-8")+"&include_reel=false&__a=1");
        JSONArray users=root.optJSONArray("users");
        if(users==null)throw new ViewerVerifier.Failure("BF_SEARCH_SCHEMA","Instagram arama yanıtı beklenen biçimde değil.");
        JSONObject match=null;
        for(int i=0;i<users.length();i++) {
            JSONObject item=users.optJSONObject(i),u=item==null?null:item.optJSONObject("user");
            if(u!=null && username.equalsIgnoreCase(u.optString("username",""))) {
                if(match!=null && !id(match).equals(id(u)))throw new ViewerVerifier.Failure("BF_IDENTITY","Arama sonucu tek bir hesap kimliğine çözümlenemedi.");
                match=u;
            }
        }
        if(match==null)throw new ViewerVerifier.Failure("BF_SEARCH_NO_EXACT","Instagram aramasında tam eşleşme dönmedi; benzer bir hesap seçilmedi.");
        id(match);return match;
    }
    public static String id(JSONObject user)throws Exception {
        String id=user.optString("pk",user.optString("id",""));
        if(!id.matches("[0-9]+"))throw new ViewerVerifier.Failure("BF_PROFILE_ID","Instagram geçerli hesap kimliği göndermedi.");
        return id;
    }
    public static JSONObject read(String username,String knownId,Request request)throws Exception {
        String id=knownId.isEmpty()?id(resolve(username,request)):knownId;
        if(!id.matches("[0-9]+"))throw new ViewerVerifier.Failure("BF_PROFILE_ID","Geçersiz hesap kimliği.");
        JSONObject vars=new JSONObject().put("id",id).put("render_surface","PROFILE")
            .put("__relay_internal__pv__PolarisCannesGuardianExperienceEnabledrelayprovider",true)
            .put("__relay_internal__pv__PolarisCASB976ProfileEnabledrelayprovider",false)
            .put("__relay_internal__pv__PolarisRepostsConsumptionEnabledrelayprovider",false)
            .put("__relay_internal__pv__PolarisWebSchoolsEnabledrelayprovider",false)
            .put("enable_integrity_filters",true);
        JSONObject result=request.post("/graphql/query/","doc_id="+DOC_ID+"&variables="+URLEncoder.encode(vars.toString(),"UTF-8")+"&server_timestamps=true");
        JSONArray errors=result.optJSONArray("errors");
        if(errors!=null && errors.length()>0)throw new ViewerVerifier.Failure("BF_PROFILE_GRAPHQL","Instagram profil sorgusu hata döndürdü; eksik veriler kaydedilmedi.");
        JSONObject data=result.optJSONObject("data"),user=data==null?null:data.optJSONObject("user");
        if(user==null)throw new ViewerVerifier.Failure("BF_PROFILE_SCHEMA","Instagram profil ayrıntılarını göndermedi.");
        if(!id.equals(id(user)))throw new ViewerVerifier.Failure("BF_IDENTITY","Profil kimliği aranan hesapla uyuşmuyor.");
        String name=user.optString("username","");
        if(!name.matches("[A-Za-z0-9._]{1,30}") || (knownId.isEmpty()&&!username.equalsIgnoreCase(name)))throw new ViewerVerifier.Failure("BF_IDENTITY","Profil kullanıcı adı doğrulanamadı.");
        return user;
    }
}
