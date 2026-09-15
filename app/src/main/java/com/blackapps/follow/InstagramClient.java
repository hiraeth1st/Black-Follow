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
    private final LinkedHashMap<String,String> listTrace=new LinkedHashMap<>();
    private void traceList(String method,String kind,int page,int rows,int unique,int expected,boolean more,boolean cursor) {
        listTrace.put(method+kind,method+" "+("followers".equals(kind)?"takipçi":"takip")+": sayfa="+page+", satır="+rows+", tekrar="+(rows-unique)+", kişi="+unique+"/"+expected+", devam="+more+", imleç="+cursor);
        saveListTrace();
    }
    private void traceSearch(String kind,int requests,int rows,int added,int unique,int expected,int queued,int depth,boolean split) {
        listTrace.put("Search"+kind,"Liste araması "+("followers".equals(kind)?"takipçi":"takip")+": istek="+requests+", satır="+rows+", yeni="+added+", kişi="+unique+"/"+expected+", kuyruk="+queued+", derinlik="+depth+", bölündü="+split);
        saveListTrace();
    }
    private void traceSearchUnsupported(String kind,String type,String code,int unique,int expected) {
        listTrace.put("SearchUnsupported"+kind+type,"Liste araması "+("followers".equals(kind)?"takipçi":"takip")+": tür="+type+", desteklenmedi="+code+", kişi="+unique+"/"+expected+"; normal ve REST kurtarma sonuçları korundu");
        saveListTrace();
    }
    private void traceTarget(String kind,int requests,int candidates,int recovered,int unique,int expected) {
        listTrace.put("Target"+kind,"Önceki listeden hedefli arama "+("followers".equals(kind)?"takipçi":"takip")+": istek="+requests+", aday="+candidates+", bulunan="+recovered+", kişi="+unique+"/"+expected);
        saveListTrace();
    }
    private void traceRestRecovery(String kind,String label,int pass,int pages,int rows,int added,int unique,int expected,boolean terminal,boolean limited,boolean unsupported) {
        listTrace.put("RestRecovery"+kind+label+pass,"REST kurtarma "+("followers".equals(kind)?"takipçi":"takip")+": yöntem="+label+", tur="+pass+", sayfa="+pages+", satır="+rows+", yeni="+added+", kişi="+unique+"/"+expected+", terminal="+terminal+", sınırlı="+limited+", destek="+(!unsupported));
        saveListTrace();
    }
    private void traceMobile(String kind,MobileInstagramClient.Result result,int expected) {
        listTrace.put("Mobile"+kind,"Mobil özel API/GraphQL "+("followers".equals(kind)?"takipçi":"takip")+": kişi="+result.people.size()+"/"+expected+(result.detail.isEmpty()?"":"\n"+result.detail));
        saveListTrace();
    }
    private void saveListTrace(){Session.prefs(context).edit().putString("last_list_detail",String.join("\n",listTrace.values())).apply();}
    private final Progress progress;
    private final Context context;private final String owner;private final long deadline;private final int requestDelayMs;private long lastRequest=0;private int followersSearchMode,followingSearchMode;
    public InstagramClient(Context c,String owner,long deadline) {this(c,owner,deadline,(kind,page,received,expected)->{},1500);}
    public InstagramClient(Context c,String owner,long deadline,Progress progress) {this(c,owner,deadline,progress,1500);}
    /** Visible for deterministic transport tests; production callers use the default delay. */
    public InstagramClient(Context c,String owner,long deadline,Progress progress,int requestDelayMs) { context=c.getApplicationContext();this.owner=owner;this.deadline=deadline;this.progress=progress;this.requestDelayMs=Math.max(0,requestDelayMs); }
    private void guard() throws IOException {
        if(Thread.currentThread().isInterrupted() || System.currentTimeMillis()>deadline) throw new IOException("Kontrol tamamlanmadan durdu; geçmiş korunuyor.");
        if(!Session.matches(owner)) throw new AccessError("Instagram oturumu değişti veya sona erdi. Yeniden giriş yap.",true,false);
    }
    private JSONObject get(String path) throws Exception {return request(path,null);}
    private JSONObject request(String path,String form) throws Exception {
        guard();
        long delay=requestDelayMs-(System.currentTimeMillis()-lastRequest);if(delay>0) Thread.sleep(delay);
        guard();lastRequest=System.currentTimeMillis();
        HttpURLConnection cn=(HttpURLConnection)new URL(Session.ORIGIN+path).openConnection();
        cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(15000);cn.setReadTimeout(20000);cn.setRequestMethod(form==null?"GET":"POST");
        String cookies=Session.cookies();
        cn.setRequestProperty("Cookie",cookies);
        cn.setRequestProperty("User-Agent",Session.prefs(context).getString("user_agent","Mozilla/5.0"));
        cn.setRequestProperty("Accept","*/*");cn.setRequestProperty("Referer",Session.ORIGIN+"/");
        cn.setRequestProperty("Origin",Session.ORIGIN);cn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        cn.setRequestProperty("Sec-Fetch-Site","same-origin");cn.setRequestProperty("Sec-Fetch-Mode","cors");cn.setRequestProperty("Sec-Fetch-Dest","empty");
        cn.setUseCaches(false);
        cn.setRequestProperty("X-IG-App-ID","936619743392459");cn.setRequestProperty("X-ASBD-ID","129477");
        String claim=Session.prefs(context).getString("www_claim","");if(!claim.isEmpty())cn.setRequestProperty("X-IG-WWW-Claim",claim);
        cn.setRequestProperty("X-CSRFToken",Session.cookieValue(cookies,"csrftoken"));
        int code=-1;String gate="";
        try {
            if(form!=null) {
                byte[] body=form.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                cn.setDoOutput(true);cn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");cn.setFixedLengthStreamingMode(body.length);
                try(OutputStream out=cn.getOutputStream()){out.write(body);}
            }
            code=cn.getResponseCode();
            String newClaim=cn.getHeaderField("x-ig-set-www-claim");
            if(newClaim!=null&&!newClaim.isEmpty()&&newClaim.length()<=1024)Session.prefs(context).edit().putString("www_claim",newClaim).apply();
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
    private String listPath(String id,String kind,String rankToken,String cursor,String order) throws Exception {
        return RelationshipRequest.page(id,kind,rankToken,cursor,order);
    }
    private int searchMode(String kind){return "followers".equals(kind)?followersSearchMode:followingSearchMode;}
    private void searchMode(String kind,int mode){if("followers".equals(kind))followersSearchMode=mode;else followingSearchMode=mode;}
    private JSONObject relationshipSearch(String id,String kind,String query) throws Exception {
        int preferred=searchMode(kind),first=preferred==2?2:1,second=first==1?2:1;ViewerVerifier.Failure optional=null;
        for(int mode:new int[]{first,second}) {
            if(preferred!=0&&mode!=preferred&&optional==null)continue;
            String requestPath=mode==1?RelationshipRequest.searchWeb(id,kind,query):RelationshipRequest.searchMinimal(id,kind,query);
            try {
                JSONObject result=get(requestPath);searchMode(kind,mode);
                Session.prefs(context).edit().remove("last_error_detail").apply();return result;
            } catch(ViewerVerifier.Failure failure) {
                if(!RelationshipRequest.optionalSearchFailure(failure.code))throw failure;
                optional=failure;
            }
        }
        throw optional==null?new ViewerVerifier.Failure("BF_SEARCH_SCHEMA","Instagram liste araması desteklenen biçimlerden yanıt vermedi."):optional;
    }
    private Store.Edge edge(JSONObject u) throws Exception {
        String pk=u.isNull("pk")?u.optString("id",""):u.optString("pk","");String username=u.optString("username","");
        if(!pk.matches("[0-9]+"))throw new IOException("Liste geçersiz kişi kimliği içeriyor; geçmiş korunuyor. [BF_LIST_ID]");
        if(!username.matches("[A-Za-z0-9._]{1,30}"))throw new IOException("Eksik kullanıcı bilgisi; geçmiş korunuyor. [BF_LIST_USERNAME]");
        Store.Edge edge=new Store.Edge(pk,username,u.optString("full_name",""));
        String photo=u.optString("profile_pic_url","");if(ProfileLinks.avatar(photo))edge.avatar=photo;
        return edge;
    }
    private LinkedHashMap<String,Store.Edge> people(String id,String kind,int expected,String rankToken) throws Exception {
        LinkedHashMap<String,Store.Edge> found=new LinkedHashMap<>();
        if(expected==0){progress.page(kind,0,0,0);observer.received(kind,found,expected);return found;}
        RelationLogic.Pages validation=new RelationLogic.Pages(expected);String cursor="";int rawRows=0;
        for(int page=0;page<RelationshipRequest.MAX_PAGES;page++) {
            JSONObject j=get(listPath(id,kind,rankToken,cursor,""));
            JSONArray users=j.getJSONArray("users");rawRows+=users.length();ArrayList<String> ids=new ArrayList<>();
            for(int i=0;i<users.length();i++) {Store.Edge person=edge(users.getJSONObject(i));ids.add(person.id);found.put(person.id,person);}
            cursor=j.isNull("next_max_id")?"":j.optString("next_max_id","");
            boolean more=j.optBoolean("has_more",false) || !cursor.isEmpty();
            try {
                validation.add(ids,cursor,more);
                traceList("REST",kind,page+1,rawRows,validation.count(),expected,more,!cursor.isEmpty());
                progress.page(kind,page+1,validation.count(),expected);
                if(!more) {guard();observer.received(kind,found,expected);return found;}
            } catch(IllegalArgumentException issue) {
                if(RelationLogic.recoverable(issue)) {
                    traceList("REST-kesildi",kind,page+1,rawRows,validation.count(),expected,true,!cursor.isEmpty());
                    guard();observer.received(kind,found,expected);return found;
                }
                throw new IOException(("followers".equals(kind)?"Takipçi listesi":"Takip edilenler listesi")+" • sayfa "+(page+1)+" • "+validation.count()+"/"+expected+" benzersiz kişi\n"+issue.getMessage(),issue);
            }
        }
        traceList("REST-sınır",kind,RelationshipRequest.MAX_PAGES,rawRows,found.size(),expected,true,!cursor.isEmpty());
        guard();observer.received(kind,found,expected);return found;
    }
    private static final class RestPassResult {int pages,rows,added;boolean terminal,limited,unsupported;}
    private RestPassResult mergeRestPass(String id,String kind,int expected,String order,int pass,LinkedHashMap<String,Store.Edge> found) throws Exception {
        RestPassResult result=new RestPassResult();int beforeAll=found.size(),stagnant=0;String cursor="";
        HashSet<String> cursors=new HashSet<>();String token=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());
        for(int page=0;page<RelationshipRequest.MAX_PAGES;page++) {
            JSONObject j;
            try {j=get(listPath(id,kind,token,cursor,order));}
            catch(ViewerVerifier.Failure failure) {
                if(page==0&&!order.isEmpty()&&RelationshipRequest.optionalSearchFailure(failure.code)) {
                    result.unsupported=true;traceRestRecovery(kind,order,pass,0,0,0,found.size(),expected,false,false,true);return result;
                }
                throw failure;
            }
            result.pages++;JSONArray users=j.getJSONArray("users");result.rows+=users.length();int before=found.size();
            for(int i=0;i<users.length();i++) {Store.Edge person=edge(users.getJSONObject(i));found.put(person.id,person);}
            if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti; geçmiş korunuyor. [BF_LIST_CHANGED]");
            stagnant=found.size()==before?stagnant+1:0;result.limited|=j.optBoolean("should_limit_list_of_followers",false);
            String next=j.isNull("next_max_id")?"":j.optString("next_max_id","");boolean more=j.optBoolean("has_more",false)||!next.isEmpty();
            if(!more){result.terminal=true;break;}
            if(users.length()==0||next.isEmpty()||!cursors.add(next)||stagnant>=3)break;
            cursor=next;
        }
        result.added=found.size()-beforeAll;
        traceRestRecovery(kind,order.isEmpty()?"yeni-rank":order,pass,result.pages,result.rows,result.added,found.size(),expected,result.terminal,result.limited,false);
        return result;
    }
    private void recoverRestPasses(String id,String kind,int expected,LinkedHashMap<String,Store.Edge> found) throws Exception {
        int zeroNew=0;
        for(int stream=2;stream<=RelationshipRequest.MAX_STREAMS&&found.size()<expected&&zeroNew<RelationshipRequest.MAX_ZERO_STREAMS;stream++) {
            RestPassResult pass=mergeRestPass(id,kind,expected,"",stream,found);
            zeroNew=pass.added==0?zeroNew+1:0;
        }
        if("followers".equals(kind)&&found.size()<expected) {
            mergeRestPass(id,kind,expected,"date_followed_latest",1,found);
            if(found.size()<expected)mergeRestPass(id,kind,expected,"date_followed_earliest",1,found);
        }
    }
    private static final class PrefixResult {
        int requests,rows,matches,added;boolean incomplete,unsupported;String failureCode="";
    }
    private static final class TargetResult {int requests,recovered,unsupported;}
    private static final class PrefixTask {
        final String prefix;final int score;
        PrefixTask(String prefix,int score){this.prefix=prefix;this.score=score;}
    }
    private static final Comparator<PrefixTask> PREFIX_ORDER=(left,right)->{
        int byScore=Integer.compare(right.score,left.score);if(byScore!=0)return byScore;
        int byDepth=Integer.compare(right.prefix.length(),left.prefix.length());if(byDepth!=0)return byDepth;
        return left.prefix.compareTo(right.prefix);
    };
    private static List<String> usernames(LinkedHashMap<String,Store.Edge> found) {
        ArrayList<String> out=new ArrayList<>(found.size());for(Store.Edge edge:found.values())out.add(edge.username);return out;
    }
    private void scheduleChildren(PriorityQueue<PrefixTask> queue,Set<String> scheduled,String parent,LinkedHashMap<String,Store.Edge> found) {
        List<String> names=usernames(found);
        for(String child:PrefixSearchLogic.prioritizedChildren(parent,names))
            if(scheduled.add(child))queue.add(new PrefixTask(child,PrefixSearchLogic.population(child,names)));
    }
    /** Relationship search returns members of the target list. Query matching only guides partitioning; membership does not depend on the match reason. */
    private PrefixResult searchPrefix(String id,String kind,String query,LinkedHashMap<String,Store.Edge> found) throws Exception {
        PrefixResult result=new PrefixResult();result.requests=1;JSONObject j;
        try {j=relationshipSearch(id,kind,query);}
        catch(ViewerVerifier.Failure failure) {
            if(RelationshipRequest.optionalSearchFailure(failure.code)){result.unsupported=true;result.failureCode=failure.code;return result;}
            throw failure;
        }
        JSONArray users=j.optJSONArray("users");
        if(users==null){result.unsupported=true;result.failureCode="BF_SEARCH_SCHEMA";return result;}
        result.rows=users.length();HashSet<String> matched=new HashSet<>();
        for(int i=0;i<users.length();i++) {
            Store.Edge person=edge(users.getJSONObject(i));
            if(PrefixSearchLogic.matchesSearchResult(person.username,person.name,query))matched.add(person.id);
            if(!found.containsKey(person.id))result.added++;
            // Every row came from this target's followers/following search endpoint, so it is a valid relationship member.
            found.put(person.id,person);
        }
        result.matches=matched.size();
        result.incomplete=j.optBoolean("should_limit_list_of_followers",false)||users.length()>=PrefixSearchLogic.SEARCH_CAP_HINT;
        return result;
    }
    private TargetResult recoverBaseline(String id,String kind,int expected,LinkedHashMap<String,Store.Edge> baseline,LinkedHashMap<String,Store.Edge> found,int budget) throws Exception {
        TargetResult result=new TargetResult();
        if(baseline==null||baseline.isEmpty()||found.size()>=expected||budget<=0)return result;
        ArrayList<Store.Edge> candidates=new ArrayList<>();
        for(Store.Edge old:baseline.values())if(!found.containsKey(old.id))candidates.add(old);
        candidates.sort((a,b)->a.username.compareToIgnoreCase(b.username));
        int limit=Math.min(candidates.size(),Math.min(budget,PrefixSearchLogic.targetedLimit(expected-found.size())));
        for(int i=0;i<limit&&found.size()<expected;i++) {
            guard();Store.Edge candidate=candidates.get(i);boolean before=found.containsKey(candidate.id);
            PrefixResult part=searchPrefix(id,kind,candidate.username,found);result.requests+=part.requests;
            if(part.unsupported){result.unsupported++;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);break;}
            if(!before&&found.containsKey(candidate.id))result.recovered++;
            if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya hedefli arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
            traceTarget(kind,result.requests,limit,result.recovered,found.size(),expected);
            progress.page(kind+"_target",result.requests,found.size(),expected);
        }
        return result;
    }
    private LinkedHashMap<String,Store.Edge> completeBySearch(String id,String kind,int expected,String rankToken,LinkedHashMap<String,Store.Edge> baseline,LinkedHashMap<String,Store.Edge> found) throws Exception {
        if(found.size()==expected)return found;
        int requests=0,rows=0,added=0;boolean searchAvailable=true,searchProbed=false;
        PriorityQueue<PrefixTask> queue=new PriorityQueue<>(PREFIX_ORDER);HashSet<String> scheduled=new HashSet<>();
        try {
            // Cursor traversals are authoritative and do not depend on the optional search surface.
            recoverRestPasses(id,kind,expected,found);
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            TargetResult target=recoverBaseline(id,kind,expected,baseline,found,PrefixSearchLogic.MAX_QUERIES-requests);
            requests+=target.requests;searchAvailable=true;
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            if(searchAvailable) {
                for(String prefix:PrefixSearchLogic.roots()) {
                    if(found.size()>=expected||requests>=PrefixSearchLogic.MAX_QUERIES)break;
                    guard();int knownBefore=PrefixSearchLogic.population(prefix,usernames(found));
                    PrefixResult part=searchPrefix(id,kind,prefix,found);requests+=part.requests;rows+=part.rows;added+=part.added;
                    if(part.unsupported) {
                        boolean turkish=PrefixSearchLogic.isTurkishDisplayQuery(prefix);
                        traceSearchUnsupported(kind,turkish?"Türkçe":"ASCII",part.failureCode,found.size(),expected);
                        // The first ASCII root (a) is the capability probe. Later failures can be query-specific.
                        if(!turkish&&!searchProbed){searchAvailable=false;break;}
                        continue;
                    }
                    if(!PrefixSearchLogic.isTurkishDisplayQuery(prefix))searchProbed=true;
                    if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
                    boolean split=PrefixSearchLogic.shouldSplit(prefix,part.matches,part.rows,knownBefore,part.incomplete)||PrefixSearchLogic.shouldExploreRoot(prefix,knownBefore,expected-found.size());
                    if(split)scheduleChildren(queue,scheduled,prefix,found);
                    traceSearch(kind,requests,rows,added,found.size(),expected,queue.size(),prefix.length(),split);
                    progress.page(kind+"_search",requests,found.size(),expected);
                }
            }
            while(searchAvailable&&!queue.isEmpty()&&found.size()<expected&&requests<PrefixSearchLogic.MAX_QUERIES) {
                guard();PrefixTask task=queue.poll();int knownBefore=PrefixSearchLogic.population(task.prefix,usernames(found));
                PrefixResult part=searchPrefix(id,kind,task.prefix,found);requests+=part.requests;rows+=part.rows;added+=part.added;
                if(part.unsupported){traceSearchUnsupported(kind,"alt-önek",part.failureCode,found.size(),expected);continue;}
                if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
                boolean split=PrefixSearchLogic.shouldSplit(task.prefix,part.matches,part.rows,knownBefore,part.incomplete);
                if(split)scheduleChildren(queue,scheduled,task.prefix,found);
                traceSearch(kind,requests,rows,added,found.size(),expected,queue.size(),task.prefix.length(),split);
                progress.page(kind+"_search",requests,found.size(),expected);
            }
            guard();observer.received(kind,found,expected);return found;
        } catch(Exception e) {
            observer.received(kind,found,expected);throw e;
        }
    }
    public Profile readProfile(Store.Account a) throws Exception {
        Profile p=profile(a.username,a.remote);
        if(!a.remote.isEmpty() && !a.remote.equals(p.id)) throw new ViewerVerifier.Failure("BF_IDENTITY","Hesap kimliği değişti; sayılar kaydedilmedi.");
        if(p.followers<0 || p.following<0) throw new ViewerVerifier.Failure("BF_PROFILE_COUNTS","Profil sayıları eksik; önceki bilgiler korunuyor.");
        return p;
    }
    public Snapshot snapshot(Store.Account a,Profile profile,long start) throws Exception {
        listTrace.clear();Session.prefs(context).edit().remove("last_list_detail").apply();
        Snapshot s=new Snapshot();s.start=start;s.profile=profile;
        if(s.profile.restricted) throw new IOException("Gizli hesap: bu oturumun liste erişimi doğrulanamadı. Instagram'da takip onayını kontrol et.");
        String followerRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString()),followingRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());
        LinkedHashMap<String,Store.Edge> oldFollowers=new LinkedHashMap<>(),oldFollowing=new LinkedHashMap<>();
        try(Store history=new Store(context)) {
            oldFollowers=history.baseline(a.id,a.owner,"followers");
            oldFollowing=history.baseline(a.id,a.owner,"following");
        } catch(Exception ignored) { /* Baseline recovery is an optimization; the live scan remains authoritative. */ }
        s.followers=people(s.profile.id,"followers",s.profile.followers,followerRank);
        s.following=people(s.profile.id,"following",s.profile.following,followingRank);
        MobileInstagramClient mobileClient=new MobileInstagramClient(context,owner,deadline,progress);
        if(s.followers.size()!=s.profile.followers) {
            MobileInstagramClient.Result mobile=mobileClient.complete(s.profile.id,"followers",s.profile.followers,s.followers);
            s.followers=mobile.people;traceMobile("followers",mobile,s.profile.followers);observer.received("followers",s.followers,s.profile.followers);
        }
        if(s.following.size()!=s.profile.following) {
            MobileInstagramClient.Result mobile=mobileClient.complete(s.profile.id,"following",s.profile.following,s.following);
            s.following=mobile.people;traceMobile("following",mobile,s.profile.following);observer.received("following",s.following,s.profile.following);
        }
        s.followers=completeBySearch(s.profile.id,"followers",s.profile.followers,followerRank,oldFollowers,s.followers);
        s.following=completeBySearch(s.profile.id,"following",s.profile.following,followingRank,oldFollowing,s.following);
        if(s.followers.size()!=s.profile.followers || s.following.size()!=s.profile.following)
            throw new PartialLists("Tam liste zorunlu olduğu için kontrol bitirilmedi: "+s.followers.size()+"/"+s.profile.followers+" takipçi, "+s.following.size()+"/"+s.profile.following+" takip. Web REST, mobil özel REST, güncel özel GraphQL ve liste aramaları tamamlandı; bulunan kimlikler kalıcı aday havuzunda saklandı ve otomatik kesinleştirme devam edecek. Geçmiş veya takipten çıkma olayı üretilmedi. [BF_STRICT_INCOMPLETE]\n"+String.join("\n",listTrace.values()));
        Profile after=profile(s.profile.username,s.profile.id);
        if(!after.id.equals(s.profile.id) || after.followers!=s.profile.followers || after.following!=s.profile.following || after.restricted)
            throw new IOException("Hesap kontrol sırasında değişti; yeni liste kaydedilmedi.");
        guard();s.end=System.currentTimeMillis();return s;
    }
}
