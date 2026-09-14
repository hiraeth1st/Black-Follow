from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


relationship_request = r'''package com.blackapps.follow;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Canonical request shapes for Instagram relationship lists and relationship-scoped searches. */
public final class RelationshipRequest {
    public static final int PAGE_SIZE=200;
    public static final int MAX_PAGES=1000;

    private RelationshipRequest() {}

    private static void validate(String id,String kind) {
        if(id==null || !id.matches("[0-9]+"))throw new IllegalArgumentException("Geçersiz hesap kimliği.");
        if(!"followers".equals(kind) && !"following".equals(kind))throw new IllegalArgumentException("Geçersiz liste türü.");
    }
    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8.name());
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

    /** Relationship search is a separate, non-cursor request. Do not attach count, rank_token or max_id. */
    public static String search(String id,String kind,String query) throws Exception {
        validate(id,kind);
        if(query==null || query.isEmpty())throw new IllegalArgumentException("Arama sorgusu boş olamaz.");
        StringBuilder path=new StringBuilder("/api/v1/friendships/").append(id).append('/').append(kind).append("/?");
        if("following".equals(kind))path.append("includes_hashtags=false&");
        path.append("search_surface=follow_list_page&query=").append(enc(query)).append("&enable_groups=true");
        return path.toString();
    }

    public static boolean optionalSearchFailure(String code) {
        return "BF_HTTP_400".equals(code) || "BF_HTTP_404".equals(code) ||
            "BF_QUERY".equals(code) || "BF_REJECTED".equals(code) || "BF_SEARCH_SCHEMA".equals(code);
    }
}
'''
Path('app/src/main/java/com/blackapps/follow/RelationshipRequest.java').write_text(relationship_request,encoding='utf-8')

relationship_test = r'''import com.blackapps.follow.RelationshipRequest;
import java.net.URLDecoder;

public class RelationshipRequestTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void fails(Runnable r,String name){boolean thrown=false;try{r.run();}catch(Exception e){thrown=true;}check(thrown,name);}
    public static void main(String[] args)throws Exception{
        String page=RelationshipRequest.page("123","followers","123_rank token","cursor value","");
        String decoded=URLDecoder.decode(page,"UTF-8");
        check(decoded.contains("count=200"),"normal page size is 200");
        check(decoded.contains("rank_token=123_rank token"),"normal page keeps one ranking context");
        check(decoded.contains("max_id=cursor value"),"cursor is sent only on list pages");
        check(decoded.contains("query="),"normal page uses an empty query");
        String ordered=URLDecoder.decode(RelationshipRequest.page("123","followers","rank","","date_followed_latest"),"UTF-8");
        check(ordered.contains("order=date_followed_latest"),"optional follower order supported");
        String search=URLDecoder.decode(RelationshipRequest.search("123","followers","a"),"UTF-8");
        check(search.contains("query=a"),"relationship search includes query");
        check(!search.contains("count="),"relationship search never sends an oversized count");
        check(!search.contains("rank_token="),"relationship search is not a ranked cursor traversal");
        check(!search.contains("max_id="),"relationship search is one response, not cursor pagination");
        String following=URLDecoder.decode(RelationshipRequest.search("123","following","ş"),"UTF-8");
        check(following.contains("includes_hashtags=false"),"following search excludes hashtags");
        check(following.contains("query=ş"),"UTF-8 Turkish query preserved");
        check(RelationshipRequest.optionalSearchFailure("BF_HTTP_400"),"HTTP 400 can disable only optional search recovery");
        check(!RelationshipRequest.optionalSearchFailure("BF_HTTP_429"),"rate errors are never swallowed as optional");
        check(RelationshipRequest.PAGE_SIZE==200&&RelationshipRequest.MAX_PAGES==1000,"bounded page constants");
        fails(()->{try{RelationshipRequest.search("bad","followers","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid identity rejected");
        fails(()->{try{RelationshipRequest.search("123","likes","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid relationship type rejected");
        System.out.println("PASS: "+checks+" relationship request checks");
    }
}
'''
Path('tests/RelationshipRequestTest.java').write_text(relationship_test,encoding='utf-8')

preview_logic = r'''package com.blackapps.follow;

import java.util.*;

/** Keeps the richest safe candidate preview without treating it as verified history. */
public final class PreviewLogic {
    private PreviewLogic() {}

    public static <T> LinkedHashMap<String,T> merge(Map<String,T> older,Map<String,T> fresh,int expected) {
        if(expected<0)throw new IllegalArgumentException("Geçersiz beklenen liste sayısı.");
        LinkedHashMap<String,T> oldCopy=new LinkedHashMap<>(),freshCopy=new LinkedHashMap<>();
        if(older!=null)oldCopy.putAll(older);if(fresh!=null)freshCopy.putAll(fresh);
        if(oldCopy.size()>expected || freshCopy.size()>expected)throw new IllegalArgumentException("Önizleme beklenen toplamı aşamaz.");
        LinkedHashMap<String,T> union=new LinkedHashMap<>(oldCopy);union.putAll(freshCopy);
        if(union.size()<=expected)return union;
        // A changed-but-equal profile count can make old and new partial sets incompatible.
        // In that case retain the larger individual observation; equal sizes prefer the fresh metadata.
        return freshCopy.size()>=oldCopy.size()?freshCopy:oldCopy;
    }
}
'''
Path('app/src/main/java/com/blackapps/follow/PreviewLogic.java').write_text(preview_logic,encoding='utf-8')

preview_test = r'''import com.blackapps.follow.PreviewLogic;
import java.util.*;

public class PreviewLogicTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static LinkedHashMap<String,String> map(String...values){LinkedHashMap<String,String> out=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)out.put(values[i],values[i+1]);return out;}
    public static void main(String[] args){
        LinkedHashMap<String,String> merged=PreviewLogic.merge(map("1","old1","2","old2"),map("2","new2","3","new3"),4);
        check(merged.size()==3,"different partial scans accumulate candidates");
        check(merged.get("2").equals("new2"),"fresh metadata replaces older metadata");
        LinkedHashMap<String,String> overflow=PreviewLogic.merge(map("1","a","2","b","3","c"),map("3","c2","4","d","5","e"),4);
        check(overflow.size()==3&&overflow.containsKey("5")&&!overflow.containsKey("1"),"incompatible union falls back to equally large fresh scan");
        LinkedHashMap<String,String> keep=PreviewLogic.merge(map("1","a","2","b","3","c"),map("2","b2"),3);
        check(keep.size()==3&&keep.containsKey("1"),"smaller scan cannot erase richer preview");
        check(PreviewLogic.merge(Collections.emptyMap(),Collections.emptyMap(),0).isEmpty(),"empty relationship supported");
        boolean failed=false;try{PreviewLogic.merge(map("1","a","2","b"),Collections.emptyMap(),1);}catch(IllegalArgumentException e){failed=true;}check(failed,"oversized preview rejected");
        System.out.println("PASS: "+checks+" preview merge checks");
    }
}
'''
Path('tests/PreviewLogicTest.java').write_text(preview_test,encoding='utf-8')

prefix_logic = r'''package com.blackapps.follow;

import java.util.*;

/** Pure rules for bounded relationship-scoped username and display-name searches. */
public final class PrefixSearchLogic {
    public static final String USERNAME_ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final String TURKISH_SEARCH_ALPHABET="çğıöşü";
    public static final String SEARCH_ALPHABET=USERNAME_ALPHABET+TURKISH_SEARCH_ALPHABET;
    public static final String ALPHABET=USERNAME_ALPHABET;
    public static final int SEARCH_CAP_HINT=40;
    public static final int SPLIT_AT=40;
    public static final int ROWS_AT=40;
    public static final int MAX_DEPTH=4;
    public static final int MAX_QUERIES=260;
    public static final int ROOT_ROUNDS=1;
    public static final int MAX_TARGETED=64;
    public static final int EXTRA_REST_PASSES=2;
    private static final Locale TURKISH=Locale.forLanguageTag("tr-TR");

    private PrefixSearchLogic() {}

    public static List<String> roots() {
        ArrayList<String> result=new ArrayList<>(SEARCH_ALPHABET.length());
        for(int i=0;i<SEARCH_ALPHABET.length();i++)result.add(String.valueOf(SEARCH_ALPHABET.charAt(i)));
        return result;
    }

    public static boolean isTurkishDisplayQuery(String query) {
        if(query==null||query.isEmpty())return false;
        String lower=query.toLowerCase(TURKISH);
        for(int i=0;i<lower.length();i++)if(TURKISH_SEARCH_ALPHABET.indexOf(lower.charAt(i))>=0)return true;
        return false;
    }

    public static List<String> children(String prefix) {
        if(prefix==null || prefix.length()>=MAX_DEPTH || isTurkishDisplayQuery(prefix))return Collections.emptyList();
        ArrayList<String> result=new ArrayList<>(USERNAME_ALPHABET.length());
        for(int i=0;i<USERNAME_ALPHABET.length();i++)result.add(prefix+USERNAME_ALPHABET.charAt(i));
        return result;
    }

    public static List<String> prioritizedChildren(String prefix,Collection<String> usernames) {
        ArrayList<String> result=new ArrayList<>(children(prefix));
        HashMap<String,Integer> counts=new HashMap<>();
        String normalized=prefix==null?"":prefix.toLowerCase(Locale.ROOT);
        if(usernames!=null)for(String username:usernames) {
            if(username==null)continue;
            String lower=username.toLowerCase(Locale.ROOT);
            if(!lower.startsWith(normalized) || lower.length()<=normalized.length())continue;
            char next=lower.charAt(normalized.length());
            if(USERNAME_ALPHABET.indexOf(next)<0)continue;
            String child=normalized+next;
            counts.put(child,counts.getOrDefault(child,0)+1);
        }
        result.sort((a,b)->{
            int byCount=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0));
            if(byCount!=0)return byCount;
            return Integer.compare(USERNAME_ALPHABET.indexOf(a.charAt(a.length()-1)),USERNAME_ALPHABET.indexOf(b.charAt(b.length()-1)));
        });
        return result;
    }

    public static int population(String prefix,Collection<String> usernames) {
        int count=0;if(prefix==null||usernames==null||isTurkishDisplayQuery(prefix))return 0;
        for(String username:usernames)if(matches(username,prefix))count++;
        return count;
    }

    public static boolean matches(String username,String prefix) {
        if(username==null || prefix==null || prefix.isEmpty())return false;
        return username.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public static boolean matchesSearchResult(String username,String fullName,String query) {
        if(isTurkishDisplayQuery(query)) {
            String name=fullName==null?"":fullName.toLowerCase(TURKISH);
            return name.contains(query.toLowerCase(TURKISH));
        }
        return matches(username,query);
    }

    public static int targetedLimit(int missing) {
        if(missing<=0)return 0;
        return Math.min(MAX_TARGETED,Math.max(12,missing*4));
    }

    public static boolean shouldExploreRoot(String prefix,int knownPopulation,int missing) {
        return missing>0 && prefix!=null && prefix.length()==1 && !isTurkishDisplayQuery(prefix) && knownPopulation>0;
    }

    public static boolean shouldSplit(String prefix,int matchingRows,int totalRows,int knownPopulation,boolean incomplete) {
        return prefix!=null && !isTurkishDisplayQuery(prefix) && prefix.length()<MAX_DEPTH &&
            (incomplete || matchingRows<knownPopulation || matchingRows>=SPLIT_AT ||
                (matchingRows>0 && totalRows>=ROWS_AT));
    }
}
'''
Path('app/src/main/java/com/blackapps/follow/PrefixSearchLogic.java').write_text(prefix_logic,encoding='utf-8')

prefix_test = r'''import com.blackapps.follow.PrefixSearchLogic;
import java.util.*;

public class PrefixSearchLogicTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        List<String> roots=PrefixSearchLogic.roots();
        check(roots.size()==44,"ASCII and six Turkish search roots covered");
        check(roots.containsAll(Arrays.asList("ç","ğ","ı","ö","ş","ü")),"all Turkish letters included");
        check(roots.get(0).equals("a")&&roots.contains("0")&&roots.contains(".")&&roots.contains("_"),"username roots preserved");
        check(new HashSet<>(roots).size()==roots.size(),"root prefixes unique");
        check(PrefixSearchLogic.matches("Abc.Def","ab"),"username prefix match is case insensitive");
        check(PrefixSearchLogic.matchesSearchResult("oznur","Öznur Aksaz","ö"),"Turkish display-name match works");
        check(PrefixSearchLogic.matchesSearchResult("isik","Işık","ı"),"Turkish dotless-I folding works");
        check(PrefixSearchLogic.children("a").size()==38,"username child alphabet unchanged");
        check(PrefixSearchLogic.children("ş").isEmpty(),"Turkish display search does not create impossible username branches");
        check(PrefixSearchLogic.shouldSplit("a",9,9,10,false),"known omission splits a username branch");
        check(!PrefixSearchLogic.shouldSplit("ş",40,40,0,true),"Turkish display root remains one-shot");
        check(PrefixSearchLogic.shouldExploreRoot("a",3,1),"populated root can be explored when an unknown identity remains");
        check(!PrefixSearchLogic.shouldExploreRoot("ş",3,1),"Turkish display root is never partitioned");
        List<String> names=Arrays.asList("ab1","ab2","ab3","ac1","a9x","other");
        List<String> children=PrefixSearchLogic.prioritizedChildren("a",names);
        check(children.size()==38&&children.get(0).equals("ab"),"densest observed child is first");
        check(children.get(1).equals("ac")&&children.get(2).equals("a9"),"equal populations retain alphabet priority");
        check(PrefixSearchLogic.population("ab",names)==3,"known prefix population counted");
        check(PrefixSearchLogic.targetedLimit(6)==24,"target allowance scales with missing count");
        check(PrefixSearchLogic.ROOT_ROUNDS==1,"canonical search is not repeated with meaningless rank tokens");
        check(PrefixSearchLogic.SEARCH_CAP_HINT==40,"dense search responses are partitioned");
        System.out.println("PASS: "+checks+" prefix-search checks");
    }
}
'''
Path('tests/PrefixSearchLogicTest.java').write_text(prefix_test,encoding='utf-8')

# Canonical account lookup: exact username search does not need or safely accept an arbitrary 1000 result request.
profile_path=Path('app/src/main/java/com/blackapps/follow/ProfileLookup.java')
profile=profile_path.read_text(encoding='utf-8')
profile=replace_once(profile,'&include_reel=false&count=1000&__a=1','&include_reel=false&__a=1','profile search request shape')
profile_path.write_text(profile,encoding='utf-8')

viewer_test=Path('tests/ViewerVerifierTest.java')
value=viewer_test.read_text(encoding='utf-8')
value=value.replace('&include_reel=false&count=1000&__a=1','&include_reel=false&__a=1')
viewer_test.write_text(value,encoding='utf-8')

profile_test=Path('tests/ProfileLookupTest.java')
value=profile_test.read_text(encoding='utf-8')
value=replace_once(value,'check(path.startsWith("/web/search/topsearch/?"),"explicit account search");return search;','check(path.startsWith("/web/search/topsearch/?"),"explicit account search");check(!path.contains("count=1000"),"account lookup avoids oversized result requests");return search;','profile lookup test shape')
profile_test.write_text(value,encoding='utf-8')

# Raise the previous arbitrary 10k validation cap; actual scans remain deadline and page bounded.
relation_path=Path('app/src/main/java/com/blackapps/follow/RelationLogic.java')
relation=relation_path.read_text(encoding='utf-8')
relation=replace_once(relation,'public final class RelationLogic {','public final class RelationLogic {\n    public static final int MAX_EXPECTED=100000;','relation maximum constant')
relation=replace_once(relation,'if(expected<0 || expected>10000) throw new IllegalArgumentException("Bu test sürümü liste başına en fazla 10.000 kişiyi destekliyor.");','if(expected<0 || expected>MAX_EXPECTED) throw new IllegalArgumentException("Bu sürüm liste başına en fazla "+MAX_EXPECTED+" kişiyi doğrular.");','relation maximum validation')
relation_path.write_text(relation,encoding='utf-8')

relation_test=Path('tests/RelationLogicTest.java')
value=relation_test.read_text(encoding='utf-8')
value=value.replace('fails(()->new RelationLogic.Pages(10001),"explicit size cap");','fails(()->new RelationLogic.Pages(RelationLogic.MAX_EXPECTED+1),"explicit size cap");check(new RelationLogic.Pages(10001).count()==0,"lists above the former 10k cap are accepted");')
relation_test.write_text(value,encoding='utf-8')

# Preview persistence now accumulates compatible partial observations instead of replacing a richer candidate set.
store_path=Path('app/src/main/java/com/blackapps/follow/Store.java')
store=store_path.read_text(encoding='utf-8')
old_save=re.search(r'    public void savePreview\(Account a,String kind,LinkedHashMap<String,Edge> people,int expected\) \{.*?\n    \}\n    public Cursor previewEdges',store,flags=re.S)
if not old_save:raise SystemExit('savePreview block not found')
new_save=r'''    private LinkedHashMap<String,Edge> previewCandidates(long account,String owner,String kind,int expected) {
        LinkedHashMap<String,Edge> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.person,e.username,e.name,e.avatar FROM preview_edges e JOIN previews p ON p.account=e.account AND p.kind=e.kind JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.kind=? AND p.expected=? AND p.observed>=a.last_success",new String[]{""+account,owner,kind,""+expected})) {
            while(c.moveToNext()) {Edge e=new Edge(c.getString(0),c.getString(1),c.getString(2));e.avatar=c.getString(3);out.put(e.id,e);}
        }
        return out;
    }
    public void savePreview(Account a,String kind,LinkedHashMap<String,Edge> people,int expected) {
        if(!(kind.equals("followers")||kind.equals("following")) || expected<0 || people.size()>expected)throw new IllegalArgumentException("Geçersiz liste önizlemesi.");
        LinkedHashMap<String,Edge> merged=PreviewLogic.merge(previewCandidates(a.id,a.owner,kind,expected),people,expected);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(get(a.id,a.owner)==null)return;
            db.delete("previews","account=? AND kind=?",new String[]{""+a.id,kind});
            ContentValues v=new ContentValues();v.put("account",a.id);v.put("kind",kind);v.put("expected",expected);v.put("received",merged.size());v.put("observed",System.currentTimeMillis());db.insertOrThrow("previews",null,v);
            for(Edge e:merged.values()) {
                ContentValues row=new ContentValues();row.put("account",a.id);row.put("kind",kind);row.put("person",e.id);row.put("username",e.username);row.put("name",e.name);row.put("avatar",e.avatar);db.insertOrThrow("preview_edges",null,row);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public Cursor previewEdges'''
store=store[:old_save.start()]+new_save+store[old_save.end():]
store_path.write_text(store,encoding='utf-8')

# Relationship client audit.
client_path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
client=client_path.read_text(encoding='utf-8')
old_trace='''    private void traceSearch(String kind,int requests,int rows,int unique,int expected,int queued,int depth,boolean split) {
        listTrace.put("Search"+kind,"Önek araması "+("followers".equals(kind)?"takipçi":"takip")+": istek="+requests+", satır="+rows+", kişi="+unique+"/"+expected+", kuyruk="+queued+", derinlik="+depth+", bölündü="+split);
        saveListTrace();
    }
'''
new_trace='''    private void traceSearch(String kind,int requests,int rows,int added,int unique,int expected,int queued,int depth,boolean split) {
        listTrace.put("Search"+kind,"Liste araması "+("followers".equals(kind)?"takipçi":"takip")+": istek="+requests+", satır="+rows+", yeni="+added+", kişi="+unique+"/"+expected+", kuyruk="+queued+", derinlik="+depth+", bölündü="+split);
        saveListTrace();
    }
    private void traceSearchUnsupported(String kind,String type,String code,int unique,int expected) {
        listTrace.put("SearchUnsupported"+kind+type,"Liste araması "+("followers".equals(kind)?"takipçi":"takip")+": tür="+type+", desteklenmedi="+code+", kişi="+unique+"/"+expected+"; normal ve REST kurtarma sonuçları korundu");
        saveListTrace();
    }
'''
client=replace_once(client,old_trace,new_trace,'search trace')
old_list_path=re.search(r'    private String listPath\(String id,String kind,String query,String rankToken,String cursor,String order\) throws Exception \{.*?\n    \}',client,flags=re.S)
if not old_list_path:raise SystemExit('listPath method not found')
new_list_path='''    private String listPath(String id,String kind,String query,String rankToken,String cursor,String order) throws Exception {
        return query==null||query.isEmpty()?RelationshipRequest.page(id,kind,rankToken,cursor,order):RelationshipRequest.search(id,kind,query);
    }'''
client=client[:old_list_path.start()]+new_list_path+client[old_list_path.end():]
client=client.replace('for(int page=0;page<200;page++) {','for(int page=0;page<RelationshipRequest.MAX_PAGES;page++) {')

prefix_start=client.index('    private static final class PrefixResult')
profile_start=client.index('    public Profile readProfile',prefix_start)
new_search_block=r'''    private static final class PrefixResult {
        int requests,rows,matches,added;boolean incomplete,unsupported;String failureCode="";
    }
    private static final class TargetResult {int requests,recovered;boolean unsupported;}
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
        try {j=get(RelationshipRequest.search(id,kind,query));}
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
            if(part.unsupported){result.unsupported=true;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);break;}
            if(!before&&found.containsKey(candidate.id))result.recovered++;
            if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya hedefli arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
            traceTarget(kind,result.requests,limit,result.recovered,found.size(),expected);
            progress.page(kind+"_target",result.requests,found.size(),expected);
        }
        return result;
    }
    private LinkedHashMap<String,Store.Edge> completeBySearch(String id,String kind,int expected,String rankToken,LinkedHashMap<String,Store.Edge> baseline,LinkedHashMap<String,Store.Edge> found) throws Exception {
        if(found.size()==expected)return found;
        int requests=0,rows=0,added=0;boolean searchAvailable=true;
        PriorityQueue<PrefixTask> queue=new PriorityQueue<>(PREFIX_ORDER);HashSet<String> scheduled=new HashSet<>();
        try {
            // Cursor traversals are authoritative and do not depend on the optional search surface.
            recoverRestPasses(id,kind,expected,found);
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            TargetResult target=recoverBaseline(id,kind,expected,baseline,found,PrefixSearchLogic.MAX_QUERIES-requests);
            requests+=target.requests;searchAvailable=!target.unsupported;
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            if(searchAvailable) {
                for(String prefix:PrefixSearchLogic.roots()) {
                    if(found.size()>=expected||requests>=PrefixSearchLogic.MAX_QUERIES)break;
                    guard();int knownBefore=PrefixSearchLogic.population(prefix,usernames(found));
                    PrefixResult part=searchPrefix(id,kind,prefix,found);requests+=part.requests;rows+=part.rows;added+=part.added;
                    if(part.unsupported) {
                        traceSearchUnsupported(kind,PrefixSearchLogic.isTurkishDisplayQuery(prefix)?"Türkçe":"ASCII",part.failureCode,found.size(),expected);
                        if(PrefixSearchLogic.isTurkishDisplayQuery(prefix))continue;
                        searchAvailable=false;break;
                    }
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
                if(part.unsupported){traceSearchUnsupported(kind,"alt-önek",part.failureCode,found.size(),expected);searchAvailable=false;break;}
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
'''
client=client[:prefix_start]+new_search_block+client[profile_start:]
client_path.write_text(client,encoding='utf-8')

# Better fixed diagnostics without exposing queries or identities.
trace_path=Path('app/src/main/java/com/blackapps/follow/RequestTrace.java')
trace=trace_path.read_text(encoding='utf-8')
trace=trace.replace('"Takipçi listesi önek araması"','"Takipçi listesi araması"').replace('"Takip edilenler önek araması"','"Takip edilenler listesi araması"')
old_detail='''        return "Aşama: "+stage(path)+" • "+(status>=100&&status<=599?"HTTP "+status:"HTTP yanıtı yok")+" • "+reason;
'''
new_detail='''        boolean relationSearch=path.startsWith("/api/v1/friendships/")&&path.matches(".*[?&]query=[^&].*");
        String shape=relationSearch?(path.contains("count=")||path.contains("rank_token=")||path.contains("max_id=")?" • arama biçimi=genişletilmiş":" • arama biçimi=kanonik"):"";
        return "Aşama: "+stage(path)+" • "+(status>=100&&status<=599?"HTTP "+status:"HTTP yanıtı yok")+" • "+reason+shape;
'''
trace=replace_once(trace,old_detail,new_detail,'request trace shape')
trace_path.write_text(trace,encoding='utf-8')

# Manual full scans have enough time for multiple cursor passes and bounded recovery; automatic jobs remain conservative.
monitor_path=Path('app/src/main/java/com/blackapps/follow/Monitor.java')
monitor=monitor_path.read_text(encoding='utf-8')
monitor=replace_once(monitor,'long deadline=System.currentTimeMillis()+(force&&!profileOnly?12:7)*60000;','long deadline=System.currentTimeMillis()+(force&&!profileOnly?30:7)*60000;','manual scan deadline')
monitor_path.write_text(monitor,encoding='utf-8')

# Test runner covers the new pure request and preview invariants.
test_path=Path('test.sh')
test=test_path.read_text(encoding='utf-8')
needle='''java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/PrefixSearchLogic.java tests/PrefixSearchLogicTest.java
java -cp out/tests PrefixSearchLogicTest
'''
insert=needle+'''java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RelationshipRequest.java tests/RelationshipRequestTest.java
java -cp out/tests RelationshipRequestTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/PreviewLogic.java tests/PreviewLogicTest.java
java -cp out/tests PreviewLogicTest
'''
test=replace_once(test,needle,insert,'test runner additions')
test_path.write_text(test,encoding='utf-8')

# Version and documentation.
main_path=Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
main_path.write_text(main_path.read_text(encoding='utf-8').replace('0.4.4','0.4.5'),encoding='utf-8')

gradle=Path('app/build.gradle')
gradle.write_text(replace_once(gradle.read_text(encoding='utf-8'),"versionCode 19; versionName '0.4.4-test'","versionCode 20; versionName '0.4.5-test'",'Gradle version'),encoding='utf-8')

manifest=Path('app/src/main/AndroidManifest.xml')
manifest.write_text(replace_once(manifest.read_text(encoding='utf-8'),'android:versionCode="19" android:versionName="0.4.4-test"','android:versionCode="20" android:versionName="0.4.5-test"','Manifest version'),encoding='utf-8')

build=Path('build-apk.sh')
build.write_text(build.read_text(encoding='utf-8').replace('Black-Follow-0.4.4-test.apk','Black-Follow-0.4.5-test.apk'),encoding='utf-8')

for name in ['README.md','TEST-RESULTS.md','GITHUB-SETUP.md']:
    path=Path(name);value=path.read_text(encoding='utf-8').replace('0.4.4','0.4.5').replace('versionCode 19','versionCode 20').replace('versionCode=19','versionCode=20')
    path.write_text(value,encoding='utf-8')

readme=Path('README.md');value=readme.read_text(encoding='utf-8')
marker="Instagram takipçi ve takip listelerine oturumun izin verdiği ölçüde erişip yerel geçmiş tutan bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.\n"
section='''\n## 0.4.5: takip/takipçi isteklerinin baştan sona düzeltilmesi\n\n- `count=1000` kaldırıldı. Normal liste sayfaları güncel istemcilerdeki gibi 200 kişi ister; ilişki içi arama ise `count`, `rank_token` ve `max_id` göndermeyen ayrı kanonik istek biçimini kullanır.\n- Arama tek yanıttır; geçersiz cursor sayfalaması kaldırıldı. HTTP 400/404 gibi arama-yüzeyi uyumsuzlukları artık bütün kontrolü bozmaz, yalnızca isteğe bağlı kurtarma aşamasını kapatır.\n- Takipçi/takip edilenler aramasından dönen her kullanıcı hedef ilişkinin üyesidir. Sonuç artık sadece kullanıcı adı aranan önekle başladığında değil, sayısal kimliği geçerliyse birleşik listeye alınır; önek eşleşmesi yalnızca alt dalları planlamak için kullanılır.\n- `ç`, `ğ`, `ı`, `ö`, `ş`, `ü` sorguları korunur ve görünen ad üzerinden ek üyeler bulabilir; kullanıcı adı için imkânsız alt dallar oluşturmaz.\n- Aynı profil toplamına ait farklı yarım taramalar güvenli aday önizlemesinde birleştirilir. Adaylar geçmişe doğrudan yazılmaz; tam kullanıcı adı araması ve son toplam kontrolüyle yeniden doğrulanır.\n- Liste doğrulama tavanı 10.000'den 100.000'e, cursor sayfa güvenlik sınırı 1.000'e yükseltildi. Elle tam tarama için 30 dakikalık üst sınır vardır; otomatik tarama 7 dakikada güvenli biçimde durur.\n- İki listenin benzersiz kimlik sayısı profil toplamıyla tam eşleşmeden geçmiş, takipten çıkma olayı veya bildirim üretilmez.\n'''
if section.strip() not in value:value=replace_once(value,marker,marker+section,'README audit section')
readme.write_text(value,encoding='utf-8')

results=Path('TEST-RESULTS.md');value=results.read_text(encoding='utf-8')
extra='''\n## 0.4.5 ilişki taraması denetimi\n\n- Normal cursor isteğinde `count=200`, `rank_token` ve `max_id`; arama isteğinde yalnızca kanonik arama parametreleri bulunduğu saf birim testleriyle doğrulanır.\n- Arama URL'sinde `count`, `rank_token` veya `max_id` bulunması regresyon hatasıdır. Türkçe sorguların UTF-8 kodlanması ve takip edilen aramasında `includes_hashtags=false` kontrol edilir.\n- Farklı yarım taramaların beklenen toplamı aşmadan birleşmesi; daha kötü yeni taramanın daha zengin önizlemeyi silememesi test edilir.\n- Arama sonucunun eşleşme sebebi üyelik doğrulaması olarak kullanılmaz; endpoint kapsamındaki tüm geçerli sayısal kimlikler birleştirilir.\n- Opsiyonel arama HTTP 400/404 ile reddedilirse çekirdek cursor sonucu korunur ve işlem `BF_LIST_PARTIAL` olarak sonlanır; generic kontrol hatasına dönüşmez.\n- 100.000 kişilik doğrulama sınırı ve 1.000 sayfalık cursor güvenlik sınırı kaynakta sabitlenmiştir.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
