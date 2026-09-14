package com.blackapps.follow;

import android.content.Context;
import android.webkit.CookieManager;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Experimental website adapter. No challenge bypass, proxy rotation, or password collection. */
public final class InstagramClient {
    public static class AccessError extends IOException {
        public final boolean auth,rate;
        public final long retryAfterMs;
        public AccessError(String msg,boolean auth,boolean rate) {this(msg,auth,rate,-1);}
        public AccessError(String msg,boolean auth,boolean rate,long retryAfterMs) { super(msg);this.auth=auth;this.rate=rate;this.retryAfterMs=retryAfterMs; }
    }
    public static class PartialLists extends IOException {public PartialLists(String message){super(message);}}
    public static class Profile {
        public String id,username,name;public int followers,following;public boolean restricted;
    }
    public static class Snapshot {
        public Profile profile; public long start,end;
        public LinkedHashMap<String,Store.Edge> followers,following;
    }
    public interface Progress {void page(String kind,int page,int received,int expected);}
    public interface ListObserver {void received(String kind,LinkedHashMap<String,Store.Edge> people,int expected) throws Exception;}
    private ListObserver observer=(kind,people,expected)->{};
    public void observeLists(ListObserver observer){this.observer=observer;}
    private final Progress progress;
    private final Context context;private final String owner;private final long deadline;private long lastRequest=0;
    public InstagramClient(Context c,String owner,long deadline) {this(c,owner,deadline,(kind,page,received,expected)->{});}
    public InstagramClient(Context c,String owner,long deadline,Progress progress) { context=c.getApplicationContext();this.owner=owner;this.deadline=deadline;this.progress=progress; }
    private void guard() throws IOException {
        if(Thread.currentThread().isInterrupted() || System.currentTimeMillis()>deadline) throw new IOException("Kontrol tamamlanmadan durdu; geçmiş korunuyor.");
        if(!Session.matches(owner)) throw new AccessError("Instagram oturumu değişti veya sona erdi. Yeniden giriş yap.",true,false);
    }
    private JSONObject get(String path) throws Exception {return request(path,null);}
    private JSONObject request(String path,String form) throws Exception {
        guard();
        long delay=1500-(System.currentTimeMillis()-lastRequest);if(delay>0) Thread.sleep(delay);
        guard();lastRequest=System.currentTimeMillis();
        HttpURLConnection cn=(HttpURLConnection)new URL(Session.ORIGIN+path).openConnection();
        cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(15000);cn.setReadTimeout(20000);cn.setRequestMethod(form==null?"GET":"POST");
        String cookies=Session.cookies();
        cn.setRequestProperty("Cookie",cookies);
        cn.setRequestProperty("User-Agent",Session.prefs(context).getString("user_agent","Mozilla/5.0"));
        cn.setRequestProperty("Accept","application/json");cn.setRequestProperty("Referer",Session.ORIGIN+"/");
        cn.setRequestProperty("Origin",Session.ORIGIN);cn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        cn.setUseCaches(false);
        cn.setRequestProperty("X-IG-App-ID","936619743392459");
        cn.setRequestProperty("X-CSRFToken",Session.cookieValue(cookies,"csrftoken"));
        int code=-1;String gate="";
        try {
            if(form!=null) {
                byte[] body=form.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                cn.setDoOutput(true);cn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");cn.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=cn.getOutputStream()){out.write(body);}
            }
            code=cn.getResponseCode();
            long retryAfter=RetryPolicy.serverDelay(cn.getHeaderField("Retry-After"),System.currentTimeMillis());
            if(code!=200) {
                JSONObject errorBody=null;
                try(InputStream error=cn.getErrorStream()) {
                    if(error!=null) {ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[2048];int n;while((n=error.read(b))!=-1 && bytes.size()+n<=32768) bytes.write(b,0,n);errorBody=new JSONObject(bytes.toString("UTF-8"));}
                } catch(IOException|JSONException ignored) { /* HTTP status remains sufficient and contains no secrets. */ }
                gate=errorBody==null?ResponsePolicy.gate(code,"",false,false,false):ResponsePolicy.gate(code,errorBody.optString("message"),!errorBody.isNull("challenge"),!errorBody.isNull("checkpoint_url"),!errorBody.isNull("feedback_title"));
                enforceGate(gate,retryAfter);
                throw new ViewerVerifier.Failure("BF_HTTP_"+code,"Instagram isteği tamamlayamadı (HTTP "+code+"). Geçmiş korunuyor.");
            }
            String ct=cn.getContentType();if(ct==null || !ct.toLowerCase(Locale.ROOT).contains("json")) throw new ViewerVerifier.Failure("BF_NOT_JSON","Instagram veri yerine bir web sayfası gönderdi. Giriş ekranını kontrol et.");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=cn.getInputStream()) { byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1) { guard();out.write(buf,0,n);if(out.size()>4*1024*1024) throw new IOException("Instagram yanıtı işlenemeyecek kadar büyük."); } }
            JSONObject j;
            try {j=new JSONObject(out.toString("UTF-8"));} catch(JSONException malformed) {throw new ViewerVerifier.Failure("BF_JSON","Instagram yanıtı okunamadı.");}
            gate=ResponsePolicy.gate(code,j.optString("message"),!j.isNull("challenge"),!j.isNull("checkpoint_url"),!j.isNull("feedback_title"));
            enforceGate(gate,retryAfter);
            if(j.has("status") && !"ok".equals(j.optString("status"))) throw new ViewerVerifier.Failure("BF_REJECTED","Instagram veri isteğini kabul etmedi. Geçmiş korunuyor.");
            JSONArray errors=j.optJSONArray("errors");
            if(errors!=null && errors.length()>0) {
                for(int i=0;i<errors.length();i++) {
                    JSONObject error=errors.optJSONObject(i);
                    if(error!=null) {gate=ResponsePolicy.gate(code,error.optString("message"),false,false,false);enforceGate(gate,retryAfter);}
                }
                throw new ViewerVerifier.Failure("BF_QUERY","Instagram sorguyu tamamlayamadı. Önceki bilgiler korunuyor.");
            }
            guard();
            for(Map.Entry<String,List<String>> header:cn.getHeaderFields().entrySet()) if("Set-Cookie".equalsIgnoreCase(header.getKey()) && header.getValue()!=null)
                for(String cookie:header.getValue()) CookieManager.getInstance().setCookie(Session.ORIGIN,cookie);
            return j;
        } catch(Exception e) {
            String detail=RequestTrace.detail(form==null?path:path+"?doc_id="+ProfileLookup.DOC_ID,code,gate);
            Session.prefs(context).edit().putString("last_error_detail",detail).apply();
            if(e instanceof AccessError) {AccessError a=(AccessError)e;throw new AccessError(a.getMessage()+"\n"+detail,a.auth,a.rate,a.retryAfterMs);}
            if(e instanceof ViewerVerifier.Failure) {ViewerVerifier.Failure f=(ViewerVerifier.Failure)e;throw new ViewerVerifier.Failure(f.code,f.getMessage()+"\n"+detail);}
            throw e;
        } finally { cn.disconnect(); }
    }
    public String verifyViewer() throws Exception {
        try {return ViewerVerifier.verify(owner,this::get);}
        catch(ViewerVerifier.Failure e) {
            if(e.code.equals("BF_SIGN_IN") || e.code.equals("BF_IDENTITY") || e.code.equals("BF_OWNER")) throw new AccessError(e.getMessage(),true,false);
            throw e;
        }
    }
    private static void enforceGate(String code,long retryAfterMs) throws AccessError {
        if(code.isEmpty()) return;
        if(code.equals("BF_RATE")) throw new AccessError("Instagram istek sınırı yanıtı verdi. [BF_RATE]",false,true,retryAfterMs);
        if(code.equals("BF_ACTION_BLOCK")) throw new AccessError("Instagram bu işlemi kısıtladı. Instagram oturum ekranındaki uyarıyı kontrol et; otomatik kontroller durduruldu. [BF_ACTION_BLOCK]",true,false);
        if(code.equals("BF_CHALLENGE")) throw new AccessError("Instagram güvenlik doğrulaması istiyor. Instagram sayfasındaki işlemi tamamla. [BF_CHALLENGE]",true,false);
        if(code.equals("BF_FORBIDDEN")) throw new AccessError("Instagram bu isteğe izin vermedi. [BF_FORBIDDEN]",true,false);
        if(code.equals("BF_REDIRECT")) throw new AccessError("Instagram isteği başka bir sayfaya yönlendirdi. Giriş ekranını kontrol et. [BF_REDIRECT]",true,false);
        throw new AccessError("Instagram yeniden giriş istiyor. [BF_SIGN_IN]",true,false);
    }
    public static String diagnostic(Exception e) {
        if(e instanceof ViewerVerifier.Failure || e instanceof AccessError) return e.getMessage();
        if(e instanceof java.net.SocketTimeoutException) return "Instagram bağlantısı zaman aşımına uğradı. [BF_TIMEOUT]";
        if(e instanceof java.net.UnknownHostException) return "Instagram adresine ulaşılamadı. İnternet bağlantısını kontrol et. [BF_DNS]";
        if(e instanceof javax.net.ssl.SSLException) return "Güvenli bağlantı kurulamadı. Cihaz tarihini ve internet bağlantısını kontrol et. [BF_TLS]";
        if(e instanceof JSONException) return "Instagram yanıtının biçimi beklenenden farklı. [BF_SCHEMA]";
        if(e instanceof InterruptedException) return "Oturum kontrolü kesildi. [BF_CANCELLED]";
        if(e instanceof IOException) return "Instagram bağlantısı tamamlanamadı. [BF_NETWORK]";
        return "Oturum kontrolünde uygulama hatası oluştu. [BF_INTERNAL]";
    }
    private Profile profile(String username,String knownId) throws Exception {
        JSONObject u=ProfileLookup.read(username,knownId,new ProfileLookup.Request(){
            public JSONObject get(String path)throws Exception{return InstagramClient.this.get(path);}
            public JSONObject post(String path,String form)throws Exception{return InstagramClient.this.request(path,form);}
        });
        Profile p=new Profile();p.id=u.optString("pk",u.optString("id",""));p.username=u.getString("username");p.name=u.optString("full_name","");
        if(!p.id.matches("[0-9]+")) throw new IOException("Instagram hesap kimliği göndermedi.");
        p.followers=u.has("edge_followed_by")?u.getJSONObject("edge_followed_by").getInt("count"):u.getInt("follower_count");
        p.following=u.has("edge_follow")?u.getJSONObject("edge_follow").getInt("count"):u.getInt("following_count");
        boolean follows=u.optBoolean("followed_by_viewer",false);
        boolean permissionKnown=u.has("followed_by_viewer");
        JSONObject friendship=u.optJSONObject("friendship_status");if(friendship!=null) {follows=friendship.optBoolean("following",follows);permissionKnown=permissionKnown||friendship.has("following");}
        // If visibility metadata is absent, the list endpoint itself remains the authority.
        p.restricted=u.optBoolean("is_private",false) && permissionKnown && !follows && !owner.equals(p.id);
        return p;
    }
    private LinkedHashMap<String,Store.Edge> people(String id,String kind,int expected) throws Exception {
        RelationLogic.Pages validation=new RelationLogic.Pages(expected);
        LinkedHashMap<String,Store.Edge> found=new LinkedHashMap<>();String cursor="";
        for(int page=0;page<200;page++) {
            JSONObject j=get("/api/v1/friendships/"+id+"/"+kind+"/?count=100"+(cursor.isEmpty()?"":"&max_id="+URLEncoder.encode(cursor,"UTF-8")));
            JSONArray users=j.getJSONArray("users");ArrayList<String> ids=new ArrayList<>();
            for(int i=0;i<users.length();i++) {
                JSONObject u=users.getJSONObject(i);String pk=u.isNull("pk")?u.optString("id",""):u.optString("pk","");String username=u.getString("username");
                if(username.isEmpty()) throw new IOException("Eksik kullanıcı bilgisi; geçmiş korunuyor.");
                Store.Edge edge=new Store.Edge(pk,username,u.optString("full_name",""));
                String photo=u.optString("profile_pic_url","");if(ProfileLinks.avatar(photo))edge.avatar=photo;
                ids.add(pk);found.put(pk,edge);
            }
            cursor=j.isNull("next_max_id")?"":j.optString("next_max_id","");
            // big_list describes the collection size, not reliably the existence of a next page.
            boolean more=j.optBoolean("has_more",false) || !cursor.isEmpty();
            try {
                validation.add(ids,cursor,more);
                progress.page(kind,page+1,validation.count(),expected);
                if(!more) {guard();observer.received(kind,found,expected);return found;}
            } catch(IllegalArgumentException e) {
                throw new IOException(("followers".equals(kind)?"Takipçi listesi":"Takip edilenler listesi")+" • sayfa "+(page+1)+" • "+validation.count()+"/"+expected+" benzersiz kişi\n"+e.getMessage(),e);
            }
        }
        throw new IOException("Sayfa sınırına ulaşıldı; eksik liste kaydedilmedi.");
    }
    public Profile readProfile(Store.Account a) throws Exception {
        Profile p=profile(a.username,a.remote);
        if(!a.remote.isEmpty() && !a.remote.equals(p.id)) throw new ViewerVerifier.Failure("BF_IDENTITY","Hesap kimliği değişti; sayılar kaydedilmedi.");
        if(p.followers<0 || p.following<0) throw new ViewerVerifier.Failure("BF_PROFILE_COUNTS","Profil sayıları eksik; önceki bilgiler korunuyor.");
        return p;
    }
    /** Independent connection traversal, used only after successful but short REST lists.
     * Query definitions: Instaloader Profile.get_followers/get_followees and instagrapi
     * user_followers_gql_chunk/user_following_gql_chunk. No merging across traversals.
     */
    private LinkedHashMap<String,Store.Edge> peopleGraphql(String id,String kind,int expected) throws Exception {
        boolean followers="followers".equals(kind);
        String hash=followers?"37479f2b8209594dde7facb0d904896a":"58712303d941c6855d4e888c5f0cd22f";
        String field=followers?"edge_followed_by":"edge_follow";
        RelationLogic.Pages validation=new RelationLogic.Pages(expected);
        LinkedHashMap<String,Store.Edge> found=new LinkedHashMap<>();String cursor="";
        progress.page(kind+"_graphql",0,0,expected);
        for(int page=0;page<1000;page++) {
            JSONObject variables=new JSONObject().put("id",id).put("include_reel",true).put("fetch_mutual",false).put("first",followers?12:24);
            if(!cursor.isEmpty())variables.put("after",cursor);
            JSONObject j=get("/graphql/query/?query_hash="+hash+"&variables="+URLEncoder.encode(variables.toString(),"UTF-8"));
            try {
                JSONObject user=j.getJSONObject("data").getJSONObject("user");
                if(user.has("id") && !id.equals(user.getString("id")))throw new IOException("Liste başka hesaba ait; geçmiş korunuyor. [BF_IDENTITY]");
                JSONObject connection=user.getJSONObject(field);
                if(connection.getInt("count")!=expected)throw new IOException("Liste toplamı kontrol sırasında değişti; geçmiş korunuyor. [BF_LIST_CHANGED]");
                JSONArray edges=connection.getJSONArray("edges");ArrayList<String> ids=new ArrayList<>();
                for(int i=0;i<edges.length();i++) {
                    JSONObject u=edges.getJSONObject(i).getJSONObject("node");
                    String pk=u.getString("id"),username=u.getString("username");
                    if(username.isEmpty())throw new IOException("Eksik kullanıcı bilgisi; geçmiş korunuyor.");
                    Store.Edge edge=new Store.Edge(pk,username,u.optString("full_name",""));
                    String photo=u.optString("profile_pic_url","");if(ProfileLinks.avatar(photo))edge.avatar=photo;
                    ids.add(pk);found.put(pk,edge);
                }
                JSONObject info=connection.getJSONObject("page_info");boolean more=info.getBoolean("has_next_page");
                cursor=info.isNull("end_cursor")?"":info.getString("end_cursor");
                validation.add(ids,cursor,more);
                progress.page(kind+"_graphql",page+1,validation.count(),expected);
                if(!more){guard();return found;}
            } catch(JSONException malformed) {
                throw new ViewerVerifier.Failure("BF_LIST_SCHEMA","İkinci liste yönteminin yanıtı okunamadı; ilk tarama önizlemesi korunuyor. [BF_LIST_SCHEMA]");
            } catch(IllegalArgumentException invalid) {
                throw new IOException((followers?"Takipçi":"Takip edilenler")+" • ikinci yöntem • sayfa "+(page+1)+" • "+validation.count()+"/"+expected+"\n"+invalid.getMessage(),invalid);
            }
        }
        throw new IOException("İkinci liste yönteminde sayfa sınırına ulaşıldı; geçmiş korunuyor.");
    }
    private LinkedHashMap<String,Store.Edge> completeList(String id,String kind,int expected,LinkedHashMap<String,Store.Edge> first) throws Exception {
        if(first.size()==expected)return first;
        LinkedHashMap<String,Store.Edge> second=peopleGraphql(id,kind,expected);
        // Keep the more informative individual preview; never union two incomplete scans.
        if(second.size()>=first.size()){guard();observer.received(kind,second,expected);return second;}
        return first;
    }
    public Snapshot snapshot(Store.Account a,Profile profile,long start) throws Exception {
        Snapshot s=new Snapshot();s.start=System.currentTimeMillis();
        s.start=start;s.profile=profile;
        if(s.profile.restricted) throw new IOException("Gizli hesap: bu oturumun liste erişimi doğrulanamadı. Instagram'da takip onayını kontrol et.");
        s.followers=people(s.profile.id,"followers",s.profile.followers);
        s.following=people(s.profile.id,"following",s.profile.following);
        s.followers=completeList(s.profile.id,"followers",s.profile.followers,s.followers);
        s.following=completeList(s.profile.id,"following",s.profile.following,s.following);
        if(s.followers.size()!=s.profile.followers || s.following.size()!=s.profile.following)
            throw new PartialLists("İki liste yöntemiyle de tam sonuç doğrulanamadı: "+s.followers.size()+"/"+s.profile.followers+" takipçi, "+s.following.size()+"/"+s.profile.following+" takip. Alınabilen en geniş tek tarama ilgili sekmede. Eksik kişiler takipten çıktı sayılmadı; doğrulanmış geçmiş değişmedi. [BF_LIST_PARTIAL]");
        Profile after=profile(s.profile.username,s.profile.id);
        if(!after.id.equals(s.profile.id) || after.followers!=s.profile.followers || after.following!=s.profile.following || after.restricted)
            throw new IOException("Hesap kontrol sırasında değişti; yeni liste kaydedilmedi.");
        guard();s.end=System.currentTimeMillis();return s;
    }
}
