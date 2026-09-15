package com.blackapps.follow;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Private-mobile completion path built from the authenticated WebView sessionid.
 * It never receives a password and never exports cookies off the device.
 */
public final class MobileInstagramClient {
    public static final class Result {
        public final LinkedHashMap<String,Store.Edge> people;
        public final String detail;
        Result(LinkedHashMap<String,Store.Edge> people,String detail){this.people=people;this.detail=detail;}
    }
    private static final class Unsupported extends IOException {Unsupported(String message){super(message);}}

    private final Context context;private final String owner;private final long deadline;private final InstagramClient.Progress progress;
    private final String uuid,phoneId,androidId;private long lastRequest;

    public MobileInstagramClient(Context context,String owner,long deadline,InstagramClient.Progress progress) {
        this.context=context.getApplicationContext();this.owner=owner;this.deadline=deadline;this.progress=progress;
        SharedPreferences prefs=Session.prefs(this.context);
        uuid=stableUuid(prefs,"mobile_uuid");phoneId=stableUuid(prefs,"mobile_phone_id");
        androidId=prefs.getString("mobile_android_id","");
        if(androidId.isEmpty()) {
            String generated="android-"+hex(sha256(owner+":"+uuid)).substring(0,16);
            prefs.edit().putString("mobile_android_id",generated).apply();
            androidId=generated;
        }
    }
    private static String stableUuid(SharedPreferences prefs,String key) {
        String value=prefs.getString(key,"");if(!value.isEmpty())return value;
        value=UUID.randomUUID().toString();prefs.edit().putString(key,value).apply();return value;
    }
    private static byte[] sha256(String value) {
        try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}

    private void guard() throws Exception {
        if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>deadline)throw new IOException("Tam liste kontrolü zaman sınırında durdu; toplanan adaylar korunuyor.");
        if(!Session.matches(owner))throw new InstagramClient.AccessError("Instagram oturumu değişti; tam liste kontrolü durdu.",true,false);
    }
    private void throttle() throws Exception {
        long delay=1800-(System.currentTimeMillis()-lastRequest);if(delay>0)Thread.sleep(delay);guard();lastRequest=System.currentTimeMillis();
    }
    private String sessionid()throws Unsupported {
        String value=Session.cookieValue(Session.cookies(),"sessionid");if(value.isEmpty())throw new Unsupported("Mobil oturum için sessionid bulunamadı.");return value;
    }
    private void headers(HttpURLConnection cn,boolean graphql,String kind)throws Exception {
        String cookies=Session.cookies(),sessionid=sessionid();
        cn.setRequestProperty("Authorization",MobileRequestLogic.authorization(owner,sessionid));
        cn.setRequestProperty("Cookie",cookies);
        cn.setRequestProperty("User-Agent",MobileRequestLogic.USER_AGENT);
        cn.setRequestProperty("Accept","*/*");
        cn.setRequestProperty("Accept-Language","tr-TR, en-US");
        cn.setRequestProperty("X-IG-App-Locale","tr_TR");cn.setRequestProperty("X-IG-Device-Locale","tr_TR");cn.setRequestProperty("X-IG-Mapped-Locale","tr_TR");
        cn.setRequestProperty("X-Pigeon-Session-Id","UFS-"+uuid+"-1");
        cn.setRequestProperty("X-Pigeon-Rawclienttime",String.format(Locale.ROOT,"%.3f",System.currentTimeMillis()/1000.0));
        cn.setRequestProperty("X-IG-App-Startup-Country","TR");
        cn.setRequestProperty("X-Bloks-Version-Id",MobileRequestLogic.BLOKS_VERSION);
        cn.setRequestProperty("X-Bloks-Is-Layout-RTL","false");cn.setRequestProperty("X-Bloks-Is-Panorama-Enabled","true");
        cn.setRequestProperty("X-IG-Device-ID",uuid);cn.setRequestProperty("X-IG-Family-Device-ID",phoneId);cn.setRequestProperty("X-IG-Android-ID",androidId);
        cn.setRequestProperty("X-IG-Timezone-Offset","10800");cn.setRequestProperty("X-IG-Connection-Type","WIFI");cn.setRequestProperty("X-IG-Capabilities","3brTv10=");
        cn.setRequestProperty("X-IG-App-ID",MobileRequestLogic.APP_ID);cn.setRequestProperty("Priority",graphql?"u=3, i":"u=3");
        cn.setRequestProperty("X-FB-HTTP-Engine","Tigon/MNS/TCP");cn.setRequestProperty("X-Tigon-Is-Retry","False");
        cn.setRequestProperty("IG-INTENDED-USER-ID",owner);cn.setRequestProperty("IG-U-DS-USER-ID",owner);
        cn.setRequestProperty("X-IG-Nav-Chain","9MV:self_profile:2,ProfileMediaTabFragment:self_profile:3,9Xf:self_"+kind+":4");
        String mid=Session.cookieValue(cookies,"mid");if(!mid.isEmpty())cn.setRequestProperty("X-MID",mid);
        String claim=Session.prefs(context).getString("mobile_claim","0");cn.setRequestProperty("X-IG-WWW-Claim",claim.isEmpty()?"0":claim);
        if(graphql) {
            String friendly=MobileRequestLogic.friendlyName(kind),doc=MobileRequestLogic.docId(kind);
            cn.setRequestProperty("X-FB-Friendly-Name",friendly);cn.setRequestProperty("X-Root-Field-Name",MobileRequestLogic.rootName(kind));
            cn.setRequestProperty("X-Client-Doc-Id",doc);cn.setRequestProperty("X-FB-RMD","state=URL_ELIGIBLE");
            cn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        }
    }
    private JSONObject request(String url,String body,String kind)throws Exception {
        guard();throttle();boolean graphql=body!=null;
        HttpURLConnection cn=(HttpURLConnection)new URL(url).openConnection();cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(20000);cn.setReadTimeout(30000);cn.setUseCaches(false);
        cn.setRequestMethod(graphql?"POST":"GET");headers(cn,graphql,kind);
        int code=-1;try {
            if(graphql) {byte[] bytes=body.getBytes(StandardCharsets.UTF_8);cn.setDoOutput(true);cn.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=cn.getOutputStream()){out.write(bytes);}}
            code=cn.getResponseCode();String claim=cn.getHeaderField("ig-set-www-claim");if(claim!=null&&!claim.isEmpty()&&claim.length()<1024)Session.prefs(context).edit().putString("mobile_claim",claim).apply();
            long retry=RetryPolicy.serverDelay(cn.getHeaderField("Retry-After"),System.currentTimeMillis());
            InputStream stream=code>=200&&code<300?cn.getInputStream():cn.getErrorStream();String text=read(stream,8*1024*1024);
            JSONObject json=parse(text);
            String message=json.optString("message","");String gate=ResponsePolicy.gate(code,message,!json.isNull("challenge"),!json.isNull("checkpoint_url"),!json.isNull("feedback_title"));
            if("BF_RATE".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste isteğini sınırladı. [BF_MOBILE_RATE]",false,true,retry);
            if("BF_CHALLENGE".equals(gate)||"BF_ACTION_BLOCK".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste doğrulaması istiyor. Instagram uygulamasındaki uyarıyı tamamla. [BF_MOBILE_CHALLENGE]",true,false);
            if(code==401||code==403||code==400||code==404)throw new Unsupported("Mobil yöntem kabul edilmedi (HTTP "+code+").");
            if(code<200||code>=300)throw new IOException("Mobil Instagram isteği HTTP "+code+" ile tamamlanamadı.");
            JSONArray errors=json.optJSONArray("errors");if(errors!=null&&errors.length()>0)throw new Unsupported("Mobil GraphQL sorgusu bu oturumda desteklenmedi.");
            if(json.has("status")&&!"ok".equals(json.optString("status"))&&!json.has("data"))throw new Unsupported("Mobil yöntem yanıtı kabul edilmedi.");
            return json;
        } finally {cn.disconnect();}
    }
    private static String read(InputStream in,int max)throws IOException {
        if(in==null)return "{}";ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream source=in){byte[] b=new byte[8192];int n;while((n=source.read(b))!=-1){if(out.size()+n>max)throw new IOException("Mobil yanıt güvenli boyut sınırını aştı.");out.write(b,0,n);}}return out.toString("UTF-8");
    }
    private static JSONObject parse(String text)throws IOException {
        try{return new JSONObject(text);}catch(JSONException first) {
            for(String line:text.split("\\r?\\n"))if(!line.trim().isEmpty())try{return new JSONObject(line.trim());}catch(JSONException ignored){}
            throw new IOException("Mobil Instagram yanıtı JSON olarak okunamadı.",first);
        }
    }
    private static Store.Edge edge(JSONObject u)throws Exception {
        String id=u.isNull("pk")?u.optString("id",u.optString("pk_id","")):u.optString("pk","");String username=u.optString("username","");
        if(!id.matches("[0-9]+")||!username.matches("[A-Za-z0-9._]{1,30}"))throw new IOException("Mobil listede geçersiz kullanıcı kimliği bulundu.");
        Store.Edge e=new Store.Edge(id,username,u.optString("full_name",""));String photo=u.optString("profile_pic_url","");if(ProfileLinks.avatar(photo))e.avatar=photo;return e;
    }
    private static int merge(JSONArray users,LinkedHashMap<String,Store.Edge> found,int expected)throws Exception {
        int before=found.size();for(int i=0;i<users.length();i++){Store.Edge e=edge(users.getJSONObject(i));found.put(e.id,e);}if(found.size()>expected)throw new IOException("Mobil taramada profil toplamından fazla kişi bulundu; hesap tarama sırasında değişti.");return found.size()-before;
    }
    private int restPass(String id,String kind,int expected,String order,LinkedHashMap<String,Store.Edge> found)throws Exception {
        String cursor="",rank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());HashSet<String> cursors=new HashSet<>();int added=0,stagnant=0;
        for(int page=0;page<RelationshipRequest.MAX_PAGES&&found.size()<expected;page++) {
            JSONObject json=request(MobileRequestLogic.restUrl(id,kind,rank,cursor,order),null,kind);JSONArray users=json.optJSONArray("users");if(users==null)throw new Unsupported("Mobil REST kullanıcı listesi bulunamadı.");
            int fresh=merge(users,found,expected);added+=fresh;stagnant=fresh==0?stagnant+1:0;progress.page(kind+"_mobile_rest",page+1,found.size(),expected);
            String next=json.isNull("next_max_id")?"":json.optString("next_max_id","");boolean more=json.optBoolean("has_more",false)||!next.isEmpty();
            if(!more||users.length()==0||next.isEmpty()||!cursors.add(next)||stagnant>=3)break;cursor=next;
        }
        return added;
    }
    private static JSONObject root(JSONObject body,String kind)throws Unsupported {
        JSONObject payload=body.optJSONObject("data");if(payload==null)payload=body;String target=MobileRequestLogic.rootName(kind);
        JSONObject exact=payload.optJSONObject(target);if(exact!=null)return exact;
        Iterator<String> keys=payload.keys();while(keys.hasNext()){String key=keys.next();if(key.contains(target)){JSONObject value=payload.optJSONObject(key);if(value!=null)return value;}}
        throw new Unsupported("Mobil GraphQL ilişki kökü bulunamadı.");
    }
    private int graphPass(String id,String kind,int expected,String order,LinkedHashMap<String,Store.Edge> found)throws Exception {
        String cursor="",rank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());HashSet<String> cursors=new HashSet<>();int added=0,stagnant=0;
        for(int page=0;page<RelationshipRequest.MAX_PAGES&&found.size()<expected;page++) {
            String form=MobileRequestLogic.graphqlForm(id,kind,rank,cursor,order);JSONObject connection=root(request(MobileRequestLogic.API_ORIGIN+"/graphql/query",form,kind),kind);
            JSONArray users=connection.optJSONArray("users");if(users==null)throw new Unsupported("Mobil GraphQL kullanıcı listesi bulunamadı.");
            int fresh=merge(users,found,expected);added+=fresh;stagnant=fresh==0?stagnant+1:0;progress.page(kind+"_mobile_gql",page+1,found.size(),expected);
            String next=connection.isNull("next_max_id")?"":connection.optString("next_max_id","");
            if(next.isEmpty()||users.length()==0||!cursors.add(next)||stagnant>=3)break;cursor=next;
        }
        return added;
    }

    public Result complete(String id,String kind,int expected,LinkedHashMap<String,Store.Edge> initial)throws Exception {
        LinkedHashMap<String,Store.Edge> found=new LinkedHashMap<>(initial);StringBuilder detail=new StringBuilder();
        if(found.size()==expected)return new Result(found,"Mobil yöntem gerekmedi: "+expected+"/"+expected+".");
        try {
            int zero=0;
            for(int stream=1;stream<=6&&found.size()<expected&&zero<2;stream++) {
                int added=restPass(id,kind,expected,"",found);detail.append("Mobil REST ").append(stream).append(": +").append(added).append(", ").append(found.size()).append('/').append(expected).append("\n");zero=added==0?zero+1:0;
            }
            if("followers".equals(kind)&&found.size()<expected)for(String order:new String[]{"date_followed_latest","date_followed_earliest"}) {
                int added=restPass(id,kind,expected,order,found);detail.append("Mobil REST ").append(order).append(": +").append(added).append(", ").append(found.size()).append('/').append(expected).append("\n");
            }
        } catch(Unsupported unsupported){detail.append("Mobil REST desteklenmedi: ").append(unsupported.getMessage()).append("\n");}
        if(found.size()<expected)try {
            int zero=0;
            for(int stream=1;stream<=4&&found.size()<expected&&zero<2;stream++) {
                int added=graphPass(id,kind,expected,"",found);detail.append("Mobil GraphQL ").append(stream).append(": +").append(added).append(", ").append(found.size()).append('/').append(expected).append("\n");zero=added==0?zero+1:0;
            }
            if(found.size()<expected)for(String order:new String[]{"date_followed_latest","date_followed_earliest"}) {
                int added=graphPass(id,kind,expected,order,found);detail.append("Mobil GraphQL ").append(order).append(": +").append(added).append(", ").append(found.size()).append('/').append(expected).append("\n");
            }
        } catch(Unsupported unsupported){detail.append("Mobil GraphQL desteklenmedi: ").append(unsupported.getMessage()).append("\n");}
        return new Result(found,detail.toString().trim());
    }
}
