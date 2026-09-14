from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count=text.count(old)
    if count!=1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    value,count=re.subn(pattern,replacement,text,count=1,flags=re.S)
    if count!=1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return value


path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
text=path.read_text(encoding='utf-8')
text=replace_once(text,
    'private final Context context;private final String owner;private final long deadline;private final int requestDelayMs;private long lastRequest=0;',
    'private final Context context;private final String owner;private final long deadline;private final int requestDelayMs;private long lastRequest=0;private int followersSearchMode,followingSearchMode;',
    'search mode fields')

old_headers='''        cn.setRequestProperty("Accept","application/json");cn.setRequestProperty("Referer",Session.ORIGIN+"/");
        cn.setRequestProperty("Origin",Session.ORIGIN);cn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        cn.setUseCaches(false);
        cn.setRequestProperty("X-IG-App-ID","936619743392459");
        cn.setRequestProperty("X-CSRFToken",Session.cookieValue(cookies,"csrftoken"));
'''
new_headers='''        cn.setRequestProperty("Accept","*/*");cn.setRequestProperty("Referer",Session.ORIGIN+"/");
        cn.setRequestProperty("Origin",Session.ORIGIN);cn.setRequestProperty("X-Requested-With","XMLHttpRequest");
        cn.setRequestProperty("Sec-Fetch-Site","same-origin");cn.setRequestProperty("Sec-Fetch-Mode","cors");cn.setRequestProperty("Sec-Fetch-Dest","empty");
        cn.setUseCaches(false);
        cn.setRequestProperty("X-IG-App-ID","936619743392459");cn.setRequestProperty("X-ASBD-ID","129477");
        String claim=Session.prefs(context).getString("www_claim","");if(!claim.isEmpty())cn.setRequestProperty("X-IG-WWW-Claim",claim);
        cn.setRequestProperty("X-CSRFToken",Session.cookieValue(cookies,"csrftoken"));
'''
text=replace_once(text,old_headers,new_headers,'web request headers')
text=replace_once(text,
    '            code=cn.getResponseCode();\n',
    '            code=cn.getResponseCode();\n            String newClaim=cn.getHeaderField("x-ig-set-www-claim");\n            if(newClaim!=null&&!newClaim.isEmpty()&&newClaim.length()<=1024)Session.prefs(context).edit().putString("www_claim",newClaim).apply();\n',
    'claim capture')

helper=r'''    private String listPath(String id,String kind,String rankToken,String cursor,String order) throws Exception {
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
'''
text=regex_once(text,r'    private String listPath\(String id,String kind,String query,String rankToken,String cursor,String order\) throws Exception \{.*?\n    \}\n',helper,'request helpers')

people=r'''    private LinkedHashMap<String,Store.Edge> people(String id,String kind,int expected,String rankToken) throws Exception {
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
'''
text=regex_once(text,r'    private LinkedHashMap<String,Store\.Edge> people\(.*?\n    \}\n    private static final class RestPassResult \{.*?\}\n',people,'primary relationship traversal')

rest=r'''    private RestPassResult mergeRestPass(String id,String kind,int expected,String order,int pass,LinkedHashMap<String,Store.Edge> found) throws Exception {
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
'''
text=regex_once(text,r'    private RestPassResult mergeRestPass\(.*?\n    \}\n    private void recoverRestPasses\(.*?\n    \}\n    private static final class PrefixResult \{\n',rest,'adaptive REST streams')
text=replace_once(text,
    '        try {j=get(RelationshipRequest.search(id,kind,query));}\n',
    '        try {j=relationshipSearch(id,kind,query);}\n',
    'dual search request')
text=replace_once(text,
    '        String followerRank=owner+"_"+UUID.randomUUID().toString(),followingRank=owner+"_"+UUID.randomUUID().toString();\n',
    '        String followerRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString()),followingRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());\n',
    'canonical initial rank tokens')
path.write_text(text,encoding='utf-8')

# Clear server claim whenever the authenticated Instagram account changes or signs out.
login=Path('app/src/main/java/com/blackapps/follow/LoginActivity.java')
value=login.read_text(encoding='utf-8')
value=replace_once(value,
    '                    if(!owner.equals(Session.owner(this))) ChangeNotifications.clear(this);\n                    Session.prefs(this).edit().putString("owner",owner).putString("viewer_name",username).putLong("verified_at",System.currentTimeMillis()).putBoolean("paused",false).remove("pause_reason").apply();\n',
    '                    boolean changed=!owner.equals(Session.owner(this));if(changed)ChangeNotifications.clear(this);\n                    android.content.SharedPreferences.Editor edit=Session.prefs(this).edit().putString("owner",owner).putString("viewer_name",username).putLong("verified_at",System.currentTimeMillis()).putBoolean("paused",false).remove("pause_reason");\n                    if(changed)edit.remove("www_claim");edit.apply();\n',
    'claim reset on account change')
login.write_text(value,encoding='utf-8')

main=Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
value=main.read_text(encoding='utf-8')
value=replace_once(value,
    'Session.prefs(this).edit().remove("owner").remove("viewer_name").apply();',
    'Session.prefs(this).edit().remove("owner").remove("viewer_name").remove("www_claim").apply();',
    'claim reset on logout')
main.write_text(value,encoding='utf-8')

trace=Path('app/src/main/java/com/blackapps/follow/RequestTrace.java')
value=trace.read_text(encoding='utf-8').replace('Takipçi listesi önek araması','Takipçi listesi araması').replace('Takip edilenler önek araması','Takip edilenler listesi araması')
trace.write_text(value,encoding='utf-8')

results=Path('TEST-RESULTS.md')
value=results.read_text(encoding='utf-8')
extra='''
- İlk REST akışından sonra, güncel web istemcilerindeki gibi farklı `hesap_id_UUID` rank tokenlarıyla toplam on akışa kadar birleştirme yapılır; art arda iki akış yeni kimlik getirmezse gereksiz istekler durur.
- İlişki araması önce web uyumlu `count=100`, HTTP 400/404 halinde count/rank/cursor içermeyen minimal biçimle denenir; çalışan biçim takipçi ve takip listesi için ayrı hatırlanır.
- Boş/tekrarlanan imleç ve üç sayfalık durgunluk, alınmış kimlikleri silmez; bağımsız REST akışları ve isteğe bağlı aramalar devam eder. Fazla kişi ve geçersiz kimlik ölümcül kalır.
- Web istekleri `X-ASBD-ID`, Fetch metadata ve sunucunun güncellediği `X-IG-WWW-Claim` ile gönderilir; hesap değişiminde claim temizlenir.
'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
