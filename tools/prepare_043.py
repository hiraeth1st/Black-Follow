from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


prefix_logic = r'''package com.blackapps.follow;

import java.util.*;

/** Pure rules for completing a relationship list with bounded username-prefix searches. */
public final class PrefixSearchLogic {
    public static final String ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final int SPLIT_AT=40;
    public static final int ROWS_AT=40;
    public static final int MAX_DEPTH=4;
    public static final int MAX_QUERIES=260;
    public static final int ROOT_ROUNDS=2;
    public static final int MAX_TARGETED=64;
    public static final int EXTRA_REST_PASSES=2;

    private PrefixSearchLogic() {}

    public static List<String> roots() {
        ArrayList<String> result=new ArrayList<>(ALPHABET.length());
        for(int i=0;i<ALPHABET.length();i++)result.add(String.valueOf(ALPHABET.charAt(i)));
        return result;
    }

    public static List<String> children(String prefix) {
        if(prefix==null || prefix.length()>=MAX_DEPTH)return Collections.emptyList();
        ArrayList<String> result=new ArrayList<>(ALPHABET.length());
        for(int i=0;i<ALPHABET.length();i++)result.add(prefix+ALPHABET.charAt(i));
        return result;
    }

    /** Dense, already observed username groups are queried first; zero-population groups remain as fallbacks. */
    public static List<String> prioritizedChildren(String prefix,Collection<String> usernames) {
        ArrayList<String> result=new ArrayList<>(children(prefix));
        HashMap<String,Integer> counts=new HashMap<>();
        String normalized=prefix==null?"":prefix.toLowerCase(Locale.ROOT);
        if(usernames!=null)for(String username:usernames) {
            if(username==null)continue;
            String lower=username.toLowerCase(Locale.ROOT);
            if(!lower.startsWith(normalized) || lower.length()<=normalized.length())continue;
            char next=lower.charAt(normalized.length());
            if(ALPHABET.indexOf(next)<0)continue;
            String child=normalized+next;
            counts.put(child,counts.getOrDefault(child,0)+1);
        }
        result.sort((a,b)->{
            int byCount=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0));
            if(byCount!=0)return byCount;
            return Integer.compare(ALPHABET.indexOf(a.charAt(a.length()-1)),ALPHABET.indexOf(b.charAt(b.length()-1)));
        });
        return result;
    }

    public static int population(String prefix,Collection<String> usernames) {
        int count=0;if(prefix==null||usernames==null)return 0;
        for(String username:usernames)if(matches(username,prefix))count++;
        return count;
    }

    public static boolean matches(String username,String prefix) {
        if(username==null || prefix==null || prefix.isEmpty())return false;
        return username.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }

    public static int targetedLimit(int missing) {
        if(missing<=0)return 0;
        return Math.min(MAX_TARGETED,Math.max(12,missing*4));
    }

    /** Split when Instagram omitted an already-known username, declared a limit, or returned a dense/capped result. */
    public static boolean shouldSplit(String prefix,int matchingRows,int totalRows,int knownPopulation,boolean incomplete) {
        return prefix!=null && prefix.length()<MAX_DEPTH &&
            (incomplete || matchingRows<knownPopulation || matchingRows>=SPLIT_AT ||
                (matchingRows>0 && totalRows>=ROWS_AT));
    }
}
'''
Path('app/src/main/java/com/blackapps/follow/PrefixSearchLogic.java').write_text(prefix_logic, encoding='utf-8')

prefix_test = r'''import com.blackapps.follow.PrefixSearchLogic;
import java.util.*;

public class PrefixSearchLogicTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        List<String> roots=PrefixSearchLogic.roots();
        check(roots.size()==38,"all legal Instagram username starters covered");
        check(roots.get(0).equals("a")&&roots.contains("0")&&roots.contains(".")&&roots.contains("_"),"letters digits and punctuation covered");
        check(new HashSet<>(roots).size()==roots.size(),"root prefixes unique");
        check(PrefixSearchLogic.matches("Abc.Def","ab"),"prefix match is case insensitive");
        check(!PrefixSearchLogic.matches("name_ab","ab"),"substring-only search result rejected");
        check(PrefixSearchLogic.shouldSplit("a",40,40,40,false),"dense matching result splits");
        check(PrefixSearchLogic.shouldSplit("a",9,9,10,false),"omission of an already-known username splits");
        check(PrefixSearchLogic.shouldSplit("a",1,1,1,true),"server-limited result splits");
        check(!PrefixSearchLogic.shouldSplit("a",0,50,0,false),"full-name noise alone does not explode the queue");
        check(!PrefixSearchLogic.shouldSplit("abcd",99,99,100,true),"bounded depth prevents unbounded requests");
        List<String> names=Arrays.asList("ab1","ab2","ab3","ac1","a9x","other");
        List<String> children=PrefixSearchLogic.prioritizedChildren("a",names);
        check(children.size()==38&&children.get(0).equals("ab"),"densest observed child is first");
        check(children.get(1).equals("ac")&&children.get(2).equals("a9"),"equal populations retain alphabet priority");
        check(PrefixSearchLogic.population("ab",names)==3,"known prefix population counted");
        check(PrefixSearchLogic.children("abcd").isEmpty(),"maximum depth enforced");
        check(PrefixSearchLogic.targetedLimit(1)==12,"small gaps receive a useful targeted allowance");
        check(PrefixSearchLogic.targetedLimit(6)==24,"target allowance scales with the missing count");
        check(PrefixSearchLogic.targetedLimit(100)==PrefixSearchLogic.MAX_TARGETED,"target allowance is capped");
        check(PrefixSearchLogic.ROOT_ROUNDS==2,"two independent root sweeps are enabled");
        check(PrefixSearchLogic.MAX_QUERIES>=PrefixSearchLogic.roots().size()*PrefixSearchLogic.ROOT_ROUNDS+PrefixSearchLogic.MAX_TARGETED,"budget covers targets and two root sweeps");
        System.out.println("PASS: "+checks+" adaptive prefix-search checks");
    }
}
'''
Path('tests/PrefixSearchLogicTest.java').write_text(prefix_test, encoding='utf-8')

store_path = Path('app/src/main/java/com/blackapps/follow/Store.java')
store = store_path.read_text(encoding='utf-8')
old_baseline = '''    /** Last fully verified identities, used only to target recovery queries after a short scan. */
    public LinkedHashMap<String,Edge> baseline(long account,String owner,String kind) {
        if(!(kind.equals("followers")||kind.equals("following")))throw new IllegalArgumentException("Geçersiz liste türü.");
        LinkedHashMap<String,Edge> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.person,e.username,e.name,e.since,e.lower_bound,e.avatar FROM edges e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.kind=?",new String[]{""+account,owner,kind})) {
            while(c.moveToNext()) { Edge e=new Edge(c.getString(0),c.getString(1),c.getString(2));e.since=c.getLong(3);e.lower=c.getLong(4);e.avatar=c.getString(5);out.put(e.id,e); }
        }
        return out;
    }
'''
new_baseline = '''    /** Confirmed rows plus the previous partial preview; candidates are revalidated by an exact list search before use. */
    public LinkedHashMap<String,Edge> baseline(long account,String owner,String kind) {
        if(!(kind.equals("followers")||kind.equals("following")))throw new IllegalArgumentException("Geçersiz liste türü.");
        LinkedHashMap<String,Edge> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.person,e.username,e.name,e.since,e.lower_bound,e.avatar FROM edges e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.kind=?",new String[]{""+account,owner,kind})) {
            while(c.moveToNext()) { Edge e=new Edge(c.getString(0),c.getString(1),c.getString(2));e.since=c.getLong(3);e.lower=c.getLong(4);e.avatar=c.getString(5);out.put(e.id,e); }
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.person,e.username,e.name,e.avatar FROM preview_edges e JOIN accounts a ON a.id=e.account JOIN previews p ON p.account=e.account AND p.kind=e.kind WHERE e.account=? AND a.owner=? AND e.kind=? AND p.observed>=a.last_success",new String[]{""+account,owner,kind})) {
            while(c.moveToNext()) if(!out.containsKey(c.getString(0))) { Edge e=new Edge(c.getString(0),c.getString(1),c.getString(2));e.avatar=c.getString(3);out.put(e.id,e); }
        }
        return out;
    }
'''
store = replace_once(store, old_baseline, new_baseline, 'preview recovery baseline')
store_path.write_text(store, encoding='utf-8')

client_path = Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
client = client_path.read_text(encoding='utf-8')

old_trace = '''    private void traceTarget(String kind,int requests,int candidates,int recovered,int unique,int expected) {
        listTrace.put("Target"+kind,"Önceki listeden hedefli arama "+("followers".equals(kind)?"takipçi":"takip")+": istek="+requests+", aday="+candidates+", bulunan="+recovered+", kişi="+unique+"/"+expected);
        saveListTrace();
    }
'''
new_trace = old_trace + '''    private void traceRestRecovery(String kind,String label,int pass,int pages,int rows,int added,int unique,int expected,boolean terminal,boolean limited,boolean unsupported) {
        listTrace.put("RestRecovery"+kind+label+pass,"REST kurtarma "+("followers".equals(kind)?"takipçi":"takip")+": yöntem="+label+", tur="+pass+", sayfa="+pages+", satır="+rows+", yeni="+added+", kişi="+unique+"/"+expected+", terminal="+terminal+", sınırlı="+limited+", destek="+(!unsupported));
        saveListTrace();
    }
'''
client = replace_once(client, old_trace, new_trace, 'REST recovery trace')

old_path = '''    private String listPath(String id,String kind,String query,String rankToken,String cursor) throws Exception {
        return "/api/v1/friendships/"+id+"/"+kind+"/?count=200&search_surface=follow_list_page&query="+URLEncoder.encode(query,"UTF-8")+"&enable_groups=true&rank_token="+URLEncoder.encode(rankToken,"UTF-8")+(cursor.isEmpty()?"":"&max_id="+URLEncoder.encode(cursor,"UTF-8"));
    }
'''
new_path = '''    private String listPath(String id,String kind,String query,String rankToken,String cursor,String order) throws Exception {
        return "/api/v1/friendships/"+id+"/"+kind+"/?count=200&search_surface=follow_list_page&query="+URLEncoder.encode(query,"UTF-8")+"&enable_groups=true"+
            ("following".equals(kind)?"&includes_hashtags=false":"")+"&rank_token="+URLEncoder.encode(rankToken,"UTF-8")+
            (order.isEmpty()?"":"&order="+URLEncoder.encode(order,"UTF-8"))+(cursor.isEmpty()?"":"&max_id="+URLEncoder.encode(cursor,"UTF-8"));
    }
'''
client = replace_once(client, old_path, new_path, 'canonical following parameters')
client = client.replace('listPath(id,kind,"",rankToken,cursor)', 'listPath(id,kind,"",rankToken,cursor,"")')
client = client.replace('listPath(id,kind,prefix,rankToken,cursor)', 'listPath(id,kind,prefix,rankToken,cursor,"")')
client = client.replace('listPath(id,kind,candidate.username,rankToken,"")', 'listPath(id,kind,candidate.username,rankToken,"","")')

marker = '    private static final class PrefixResult {int requests,rows,matches;boolean incomplete;}\n'
rest_code = r'''    private static final class RestPassResult {int pages,rows,added;boolean terminal,limited,unsupported;}
    private RestPassResult mergeRestPass(String id,String kind,int expected,String order,int pass,LinkedHashMap<String,Store.Edge> found) throws Exception {
        RestPassResult result=new RestPassResult();int beforeAll=found.size(),stagnant=0;String cursor="";
        HashSet<String> cursors=new HashSet<>();String token=owner+"_"+kind+"_recovery_"+pass+"_"+UUID.randomUUID().toString();
        for(int page=0;page<200;page++) {
            JSONObject j;
            try {j=get(listPath(id,kind,"",token,cursor,order));}
            catch(ViewerVerifier.Failure failure) {
                if(page==0&&!order.isEmpty()&&(failure.code.equals("BF_HTTP_400")||failure.code.equals("BF_HTTP_404")||failure.code.equals("BF_QUERY")||failure.code.equals("BF_REJECTED"))) {
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
        result.added=found.size()-beforeAll;traceRestRecovery(kind,order.isEmpty()?"yeni-rank":order,pass,result.pages,result.rows,result.added,found.size(),expected,result.terminal,result.limited,false);
        return result;
    }
    private void recoverRestPasses(String id,String kind,int expected,LinkedHashMap<String,Store.Edge> found) throws Exception {
        for(int pass=1;pass<=PrefixSearchLogic.EXTRA_REST_PASSES&&found.size()<expected;pass++)mergeRestPass(id,kind,expected,"",pass,found);
        if("followers".equals(kind)&&found.size()<expected) {
            mergeRestPass(id,kind,expected,"date_followed_latest",1,found);
            if(found.size()<expected)mergeRestPass(id,kind,expected,"date_followed_earliest",1,found);
        }
    }
'''
if marker not in client:
    raise SystemExit('PrefixResult marker missing')
client = client.replace(marker, rest_code + marker, 1)

old_complete_pattern = r'''    private LinkedHashMap<String,Store\.Edge> completeBySearch\(String id,String kind,int expected,String rankToken,LinkedHashMap<String,Store\.Edge> baseline,LinkedHashMap<String,Store\.Edge> found\) throws Exception \{.*?\n    \}\n    public Profile readProfile'''
new_complete = r'''    private LinkedHashMap<String,Store.Edge> completeBySearch(String id,String kind,int expected,String rankToken,LinkedHashMap<String,Store.Edge> baseline,LinkedHashMap<String,Store.Edge> found) throws Exception {
        if(found.size()==expected)return found;
        int requests=0,rows=0;
        PriorityQueue<PrefixTask> queue=new PriorityQueue<>(PREFIX_ORDER);HashSet<String> scheduled=new HashSet<>();
        try {
            requests+=recoverBaseline(id,kind,expected,rankToken,baseline,found,PrefixSearchLogic.MAX_QUERIES-requests);
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            recoverRestPasses(id,kind,expected,found);
            if(found.size()==expected){guard();observer.received(kind,found,expected);return found;}
            for(int round=1;round<=PrefixSearchLogic.ROOT_ROUNDS&&found.size()<expected&&requests<PrefixSearchLogic.MAX_QUERIES;round++) {
                for(String prefix:PrefixSearchLogic.roots()) {
                    if(found.size()>=expected||requests>=PrefixSearchLogic.MAX_QUERIES)break;
                    guard();int knownBefore=PrefixSearchLogic.population(prefix,usernames(found));
                    String queryToken=rankToken+"_root_"+round+"_"+prefix+"_"+UUID.randomUUID().toString();
                    PrefixResult part=searchPrefix(id,kind,prefix,queryToken,found,PrefixSearchLogic.MAX_QUERIES-requests);
                    requests+=part.requests;rows+=part.rows;
                    if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
                    boolean split=PrefixSearchLogic.shouldSplit(prefix,part.matches,part.rows,knownBefore,part.incomplete);
                    if(split)scheduleChildren(queue,scheduled,prefix,found);
                    traceSearch(kind,requests,rows,found.size(),expected,queue.size(),prefix.length(),split);
                    progress.page(kind+"_search",requests,found.size(),expected);
                }
            }
            while(!queue.isEmpty()&&found.size()<expected&&requests<PrefixSearchLogic.MAX_QUERIES) {
                guard();PrefixTask task=queue.poll();int knownBefore=PrefixSearchLogic.population(task.prefix,usernames(found));
                String queryToken=rankToken+"_child_"+task.prefix+"_"+UUID.randomUUID().toString();
                PrefixResult part=searchPrefix(id,kind,task.prefix,queryToken,found,PrefixSearchLogic.MAX_QUERIES-requests);
                requests+=part.requests;rows+=part.rows;
                if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
                boolean split=PrefixSearchLogic.shouldSplit(task.prefix,part.matches,part.rows,knownBefore,part.incomplete);
                if(split)scheduleChildren(queue,scheduled,task.prefix,found);
                traceSearch(kind,requests,rows,found.size(),expected,queue.size(),task.prefix.length(),split);
                progress.page(kind+"_search",requests,found.size(),expected);
            }
            guard();observer.received(kind,found,expected);return found;
        } catch(Exception e) {
            observer.received(kind,found,expected);throw e;
        }
    }
    public Profile readProfile'''
client, count = re.subn(old_complete_pattern, new_complete, client, count=1, flags=re.S)
if count != 1:
    raise SystemExit(f'completeBySearch replacement failed: {count}')
client_path.write_text(client, encoding='utf-8')

# Version and copy updates.
main_path = Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
main_path.write_text(main_path.read_text(encoding='utf-8').replace('0.4.2','0.4.3'), encoding='utf-8')

gradle = Path('app/build.gradle')
gradle.write_text(replace_once(gradle.read_text(encoding='utf-8'), "versionCode 17; versionName '0.4.2-test'", "versionCode 18; versionName '0.4.3-test'", 'Gradle version'), encoding='utf-8')

manifest = Path('app/src/main/AndroidManifest.xml')
manifest.write_text(replace_once(manifest.read_text(encoding='utf-8'), 'android:versionCode="17" android:versionName="0.4.2-test"', 'android:versionCode="18" android:versionName="0.4.3-test"', 'Manifest version'), encoding='utf-8')

build = Path('build-apk.sh')
build.write_text(build.read_text(encoding='utf-8').replace('Black-Follow-0.4.2-test.apk','Black-Follow-0.4.3-test.apk'), encoding='utf-8')

for name in ['README.md','TEST-RESULTS.md','GITHUB-SETUP.md']:
    path=Path(name);value=path.read_text(encoding='utf-8').replace('0.4.2','0.4.3').replace('versionCode 17','versionCode 18').replace('versionCode=17','versionCode=18')
    path.write_text(value,encoding='utf-8')

readme=Path('README.md');value=readme.read_text(encoding='utf-8')
marker="Instagram takipçi ve takip listelerine oturumun izin verdiği ölçüde erişip yerel geçmiş tutan bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.\n"
section='''\n## 0.4.3: çoklu REST kurtarma ve eksik-dal tespiti\n\n- Takip edilenler isteklerine Instagram'ın kanonik `includes_hashtags=false` parametresi eklendi.\n- Normal taramadan sonra iki bağımsız rank-token REST geçişi birleştirilir; takipçilerde ayrıca en yeni/en eski sıralı geçişler denenir.\n- Önceki tam listeye ek olarak son yarım önizleme de yalnızca tam kullanıcı adıyla yeniden doğrulanacak aday kaynağıdır.\n- Aynı önek altında zaten bilinen kullanıcı sayısından daha az sonuç dönerse o dal eksik kabul edilip alt öneklere ayrılır.\n- Kök önekler iki bağımsız rank bağlamıyla taranır; toplam doğrulanmadan geçmiş yine değişmez.\n'''
if section.strip() not in value:value=replace_once(value,marker,marker+section,'README 0.4.3 section')
readme.write_text(value,encoding='utf-8')

results=Path('TEST-RESULTS.md');value=results.read_text(encoding='utf-8')
extra='''\n## 0.4.3 ek kontrolleri\n\n- Bilinen önek nüfusundan daha az arama sonucu gelmesi alt-dal bölme sebebidir.\n- Takip edilenler URL'sinde `includes_hashtags=false`; iki bağımsız kök turu ve ek REST geçişleri kaynak doğrulamasına dahildir.\n- Önceki önizleme doğrudan geçmişe eklenmez; yalnızca aynı sayısal kimlik veya kullanıcı adı arama sonucunda yeniden görünürse kurtarılır.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
