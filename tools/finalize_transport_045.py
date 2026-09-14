from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count=text.count(old)
    if count!=1:raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

# Canonical list token plus dual web/mobile-compatible search request shapes.
request_path=Path('app/src/main/java/com/blackapps/follow/RelationshipRequest.java')
request=request_path.read_text(encoding='utf-8')
request_new=r'''package com.blackapps.follow;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Canonical request shapes for Instagram relationship lists and relationship-scoped searches. */
public final class RelationshipRequest {
    public static final int PAGE_SIZE=200;
    public static final int WEB_SEARCH_SIZE=100;
    public static final int MAX_PAGES=1000;

    private RelationshipRequest() {}

    private static void validate(String id,String kind) {
        if(id==null || !id.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz hesap kimliği.");
        if(!"followers".equals(kind) && !"following".equals(kind))throw new IllegalArgumentException("Geçersiz liste türü.");
    }
    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8.name());
    }

    public static String rankToken(String owner,String uuid) {
        if(owner==null||!owner.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz oturum kimliği.");
        if(uuid==null||!uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Geçersiz sıralama UUID'si.");
        return owner+"_"+uuid;
    }

    /** Cursor pagination. The maintained client implementation caps each page at 200. */
    public static String page(String id,String kind,String rankToken,String cursor,String order) throws Exception {
        validate(id,kind);
        if(rankToken==null || rankToken.isEmpty())throw new IllegalArgumentException("Liste sıralama kimliği eksik.");
        StringBuilder path=new StringBuilder("/api/v1/friendships/").append(id).append('/').append(kind)
            .append("/?count=").append(PAGE_SIZE)
            .append("&rank_token=").append(enc(rankToken))
            .append("&search_surface=follow_list_page&query=&enable_groups=true");
        if(order!=null && !order.isEmpty())path.append("&order=").append(enc(order));
        if(cursor!=null && !cursor.isEmpty())path.append("&max_id=").append(enc(cursor));
        return path.toString();
    }

    private static String searchBase(String id,String kind,String query) throws Exception {
        validate(id,kind);
        if(query==null || query.isEmpty())throw new IllegalArgumentException("Arama sorgusu boş olamaz.");
        StringBuilder path=new StringBuilder("/api/v1/friendships/").append(id).append('/').append(kind).append("/?");
        if("following".equals(kind))path.append("includes_hashtags=false&");
        return path.append("search_surface=follow_list_page&query=").append(enc(query)).append("&enable_groups=true").toString();
    }

    /** Browser-compatible shape seen on the www.instagram.com relationship surface. */
    public static String searchWeb(String id,String kind,String query) throws Exception {
        String path=searchBase(id,kind,query);
        int separator=path.indexOf('?');
        return path.substring(0,separator+1)+"count="+WEB_SEARCH_SIZE+"&"+path.substring(separator+1);
    }

    /** Private-client-compatible minimal shape; used automatically if the browser shape is rejected. */
    public static String searchMinimal(String id,String kind,String query) throws Exception {
        return searchBase(id,kind,query);
    }

    public static boolean optionalSearchFailure(String code) {
        return "BF_HTTP_400".equals(code) || "BF_HTTP_404".equals(code) ||
            "BF_QUERY".equals(code) || "BF_REJECTED".equals(code) || "BF_SEARCH_SCHEMA".equals(code);
    }
}
'''
request_path.write_text(request_new,encoding='utf-8')

request_test=Path('tests/RelationshipRequestTest.java')
request_test.write_text(r'''import com.blackapps.follow.RelationshipRequest;
import java.net.URLDecoder;

public class RelationshipRequestTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void fails(Runnable r,String name){boolean thrown=false;try{r.run();}catch(Exception e){thrown=true;}check(thrown,name);}
    public static void main(String[] args)throws Exception{
        String uuid="123e4567-e89b-12d3-a456-426614174000";
        check(RelationshipRequest.rankToken("123",uuid).equals("123_"+uuid),"rank token follows owner_UUID format");
        String page=RelationshipRequest.page("123","followers",RelationshipRequest.rankToken("123",uuid),"cursor value","");
        String decoded=URLDecoder.decode(page,"UTF-8");
        check(decoded.contains("count=200"),"normal page size is 200");
        check(decoded.contains("rank_token=123_"+uuid),"normal page keeps canonical ranking context");
        check(decoded.contains("max_id=cursor value"),"cursor is sent only on list pages");
        check(decoded.contains("query="),"normal page uses an empty query");
        String ordered=URLDecoder.decode(RelationshipRequest.page("123","followers",RelationshipRequest.rankToken("123",uuid),"","date_followed_latest"),"UTF-8");
        check(ordered.contains("order=date_followed_latest"),"optional follower order supported");
        String web=URLDecoder.decode(RelationshipRequest.searchWeb("123","followers","a"),"UTF-8");
        check(web.contains("count=100"),"web relationship search asks for a supported bounded result size");
        check(web.contains("query=a"),"web relationship search includes query");
        check(!web.contains("rank_token="),"web relationship search is not a ranked traversal");
        check(!web.contains("max_id="),"web relationship search is one response");
        String minimal=URLDecoder.decode(RelationshipRequest.searchMinimal("123","followers","a"),"UTF-8");
        check(!minimal.contains("count="),"minimal search omits count for private-client compatibility");
        check(!minimal.contains("rank_token=")&&!minimal.contains("max_id="),"minimal search omits cursor state");
        String following=URLDecoder.decode(RelationshipRequest.searchWeb("123","following","ş"),"UTF-8");
        check(following.contains("includes_hashtags=false"),"following search excludes hashtags");
        check(following.contains("query=ş"),"UTF-8 Turkish query preserved");
        check(RelationshipRequest.optionalSearchFailure("BF_HTTP_400"),"HTTP 400 can trigger the alternate search shape");
        check(!RelationshipRequest.optionalSearchFailure("BF_HTTP_429"),"rate errors are never swallowed as optional");
        check(RelationshipRequest.PAGE_SIZE==200&&RelationshipRequest.WEB_SEARCH_SIZE==100&&RelationshipRequest.MAX_PAGES==1000,"bounded request constants");
        fails(()->RelationshipRequest.rankToken("bad",uuid),"invalid rank owner rejected");
        fails(()->RelationshipRequest.rankToken("123","bad"),"invalid rank UUID rejected");
        fails(()->{try{RelationshipRequest.searchWeb("bad","followers","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid identity rejected");
        fails(()->{try{RelationshipRequest.searchMinimal("123","likes","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid relationship type rejected");
        System.out.println("PASS: "+checks+" relationship request checks");
    }
}
''',encoding='utf-8')

# Typed recoverable page failures allow independent REST passes to repair cursor stalls.
relation_path=Path('app/src/main/java/com/blackapps/follow/RelationLogic.java')
relation=relation_path.read_text(encoding='utf-8')
relation=replace_once(relation,
'''public final class RelationLogic {
    public static final int MAX_EXPECTED=100000;
''',
'''public final class RelationLogic {
    public static final int MAX_EXPECTED=100000;
    public static final class PageIssue extends IllegalArgumentException {
        public final boolean recoverable;
        PageIssue(String message,boolean recoverable){super(message);this.recoverable=recoverable;}
    }
    public static boolean recoverable(Throwable error){return error instanceof PageIssue && ((PageIssue)error).recoverable;}
''','page issue type')
replacements={
'if(finished) throw new IllegalArgumentException("Biten listeye yeni sayfa geldi.");':'if(finished) throw new PageIssue("Biten listeye yeni sayfa geldi.",false);',
'if(id==null || !id.matches("[0-9]+"))throw new IllegalArgumentException("Liste geçersiz kişi kimliği içeriyor; geçmiş korunuyor. [BF_LIST_ID]");':'if(id==null || !id.matches("[0-9]+"))throw new PageIssue("Liste geçersiz kişi kimliği içeriyor; geçmiş korunuyor. [BF_LIST_ID]",false);',
'if(ids.size()>expected) throw new IllegalArgumentException("Liste kontrol sırasında değişti; geçmiş korunuyor.");':'if(ids.size()>expected) throw new PageIssue("Liste kontrol sırasında değişti; geçmiş korunuyor.",false);',
'                throw new IllegalArgumentException("Liste tamamlanamadı; geçmiş korunuyor.");':'                throw new PageIssue("Liste imleci tamamlanamadı; bağımsız kurtarma geçişi denenecek. [BF_LIST_CURSOR]",true);',
'if(more && stagnantPages>=3)throw new IllegalArgumentException("Liste üç sayfadır ilerlemiyor ("+ids.size()+"/"+expected+"); geçmiş korunuyor. [BF_LIST_STALLED]");':'if(more && stagnantPages>=3)throw new PageIssue("Liste üç sayfadır ilerlemiyor ("+ids.size()+"/"+expected+"); bağımsız kurtarma geçişi denenecek. [BF_LIST_STALLED]",true);',
'if(!finished || ids.size()!=expected) throw new IllegalArgumentException("Listenin tamamı alınamadı ("+ids.size()+"/"+expected+"); geçmiş korunuyor.");':'if(!finished || ids.size()!=expected) throw new PageIssue("Listenin tamamı alınamadı ("+ids.size()+"/"+expected+"); geçmiş korunuyor.",true);'
}
for old,new in replacements.items():relation=replace_once(relation,old,new,'relation issue replacement')
relation_path.write_text(relation,encoding='utf-8')

relation_test=Path('tests/RelationLogicTest.java')
value=relation_test.read_text(encoding='utf-8')
needle='''        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"x",true);p.add(set("2"),"x",true);},"cursor loop");
'''
insert=needle+'''        try {RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"x",true);p.add(set("2"),"x",true);}catch(IllegalArgumentException e){check(RelationLogic.recoverable(e),"cursor loop is recoverable by another traversal");}
'''
value=replace_once(value,needle,insert,'recoverable cursor test')
needle2='''        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(set("1","2"),"",false);},"count changed during scan");
'''
insert2=needle2+'''        try {RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(set("1","2"),"",false);}catch(IllegalArgumentException e){check(!RelationLogic.recoverable(e),"over-count is never recoverable");}
'''
value=replace_once(value,needle2,insert2,'fatal overcount test')
relation_test.write_text(value,encoding='utf-8')

# Transport: web headers/claim, valid rank token, recover partial first traversal, and dual search shapes.
client_path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
client=client_path.read_text(encoding='utf-8')
client=replace_once(client,
'    private final Context context;private final String owner;private final long deadline;private final int requestDelayMs;private long lastRequest=0;\n',
'    private final Context context;private final String owner;private final long deadline;private final int requestDelayMs;private long lastRequest=0;private int followersSearchMode,followingSearchMode;\n',
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
client=replace_once(client,old_headers,new_headers,'web headers')
client=replace_once(client,
'            code=cn.getResponseCode();\n',
'''            code=cn.getResponseCode();
            String newClaim=cn.getHeaderField("x-ig-set-www-claim");
            if(newClaim!=null&&!newClaim.isEmpty()&&newClaim.length()<=1024)Session.prefs(context).edit().putString("www_claim",newClaim).apply();
''','claim capture')
# Replace listPath helper with page-only helper; searches use explicit dual shapes.
old_list='''    private String listPath(String id,String kind,String query,String rankToken,String cursor,String order) throws Exception {
        return query==null||query.isEmpty()?RelationshipRequest.page(id,kind,rankToken,cursor,order):RelationshipRequest.search(id,kind,query);
    }
'''
new_list='''    private String listPath(String id,String kind,String rankToken,String cursor,String order) throws Exception {
        return RelationshipRequest.page(id,kind,rankToken,cursor,order);
    }
    private int searchMode(String kind){return "followers".equals(kind)?followersSearchMode:followingSearchMode;}
    private void searchMode(String kind,int mode){if("followers".equals(kind))followersSearchMode=mode;else followingSearchMode=mode;}
    private JSONObject relationshipSearch(String id,String kind,String query) throws Exception {
        int preferred=searchMode(kind),first=preferred==2?2:1,second=first==1?2:1;ViewerVerifier.Failure optional=null;
        for(int mode:new int[]{first,second}) {
            if(preferred!=0&&mode!=preferred&&optional==null)continue;
            String path=mode==1?RelationshipRequest.searchWeb(id,kind,query):RelationshipRequest.searchMinimal(id,kind,query);
            try {JSONObject result=get(path);searchMode(kind,mode);Session.prefs(context).edit().remove("last_error_detail").apply();return result;}
            catch(ViewerVerifier.Failure failure) {
                if(!RelationshipRequest.optionalSearchFailure(failure.code))throw failure;
                optional=failure;
            }
        }
        throw optional==null?new ViewerVerifier.Failure("BF_SEARCH_SCHEMA","Instagram liste araması desteklenen biçimlerden yanıt vermedi."):optional;
    }
'''
client=replace_once(client,old_list,new_list,'request shape helpers')
client=client.replace('listPath(id,kind,"",rankToken,cursor,"")','listPath(id,kind,rankToken,cursor,"")')
client=client.replace('listPath(id,kind,"",token,cursor,order)','listPath(id,kind,token,cursor,order)')
# First traversal: cursor/stall problems become a valid partial input for recovery passes.
old_catch='''            } catch(IllegalArgumentException e) {
                throw new IOException(("followers".equals(kind)?"Takipçi listesi":"Takip edilenler listesi")+" • sayfa "+(page+1)+" • "+validation.count()+"/"+expected+" benzersiz kişi\n"+e.getMessage(),e);
            }
        }
        throw new IOException("Sayfa sınırına ulaşıldı; eksik liste kaydedilmedi.");
'''
new_catch='''            } catch(IllegalArgumentException e) {
                if(RelationLogic.recoverable(e)) {
                    traceList("REST-kesildi",kind,page+1,rawRows,validation.count(),expected,true,!cursor.isEmpty());
                    guard();observer.received(kind,found,expected);return found;
                }
                throw new IOException(("followers".equals(kind)?"Takipçi listesi":"Takip edilenler listesi")+" • sayfa "+(page+1)+" • "+validation.count()+"/"+expected+" benzersiz kişi\n"+e.getMessage(),e);
            }
        }
        traceList("REST-sınır",kind,RelationshipRequest.MAX_PAGES,rawRows,found.size(),expected,true,!cursor.isEmpty());
        guard();observer.received(kind,found,expected);return found;
'''
client=replace_once(client,old_catch,new_catch,'recoverable first traversal')
client=replace_once(client,
'HashSet<String> cursors=new HashSet<>();String token=owner+"_"+kind+"_recovery_"+pass+"_"+UUID.randomUUID().toString();',
'HashSet<String> cursors=new HashSet<>();String token=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());',
'canonical recovery rank token')
client=replace_once(client,
'        try {j=get(RelationshipRequest.search(id,kind,query));}\n',
'        try {j=relationshipSearch(id,kind,query);}\n',
'dual search transport')
client=replace_once(client,
'        String followerRank=owner+"_"+UUID.randomUUID().toString(),followingRank=owner+"_"+UUID.randomUUID().toString();\n',
'        String followerRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString()),followingRank=RelationshipRequest.rankToken(owner,UUID.randomUUID().toString());\n',
'canonical initial rank token')
client_path.write_text(client,encoding='utf-8')

results=Path('TEST-RESULTS.md')
value=results.read_text(encoding='utf-8')
extra='''\n- Web ilişki araması önce `count=100` ile denenir; HTTP 400/404 olursa aynı sorgu count/rank/cursor içermeyen minimal istemci biçimiyle otomatik yeniden denenir ve çalışan biçim liste türü bazında hatırlanır.\n- `rank_token`, güncel istemcideki `hesap_id_UUID` biçimiyle hem ilk hem kurtarma geçişlerinde doğrulanır.\n- Boş/tekrarlanan imleç veya üç sayfalık durgunluk, geçerli kısmı silmez; bağımsız REST geçişleri ve arama kurtarması devam eder. Fazla kişi veya geçersiz kimlik ölümcül kalır.\n- Web isteklerinde güncel App ID yanında `X-ASBD-ID`, Fetch metadata ve sunucunun döndürdüğü `X-IG-WWW-Claim` kullanılır.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
