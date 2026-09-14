from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


prefix_logic = r'''package com.blackapps.follow;

import java.util.*;

/** Pure rules for completing a relationship list with bounded username and display-name searches. */
public final class PrefixSearchLogic {
    public static final String USERNAME_ALPHABET="abcdefghijklmnopqrstuvwxyz0123456789._";
    public static final String TURKISH_SEARCH_ALPHABET="çğıöşü";
    public static final String SEARCH_ALPHABET=USERNAME_ALPHABET+TURKISH_SEARCH_ALPHABET;
    /** Compatibility alias: child username partitions remain limited to legal username characters. */
    public static final String ALPHABET=USERNAME_ALPHABET;
    public static final int SEARCH_COUNT=1000;
    public static final int SPLIT_AT=40;
    public static final int ROWS_AT=40;
    public static final int MAX_DEPTH=4;
    public static final int MAX_QUERIES=260;
    public static final int ROOT_ROUNDS=2;
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
        // Turkish letters cannot occur in Instagram usernames. They are one-shot display-name searches.
        if(prefix==null || prefix.length()>=MAX_DEPTH || isTurkishDisplayQuery(prefix))return Collections.emptyList();
        ArrayList<String> result=new ArrayList<>(USERNAME_ALPHABET.length());
        for(int i=0;i<USERNAME_ALPHABET.length();i++)result.add(prefix+USERNAME_ALPHABET.charAt(i));
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

    /** Turkish root searches recover relationship members through their display names. */
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

    /** Split username prefixes only; Turkish display-name roots intentionally remain one-shot searches. */
    public static boolean shouldSplit(String prefix,int matchingRows,int totalRows,int knownPopulation,boolean incomplete) {
        return prefix!=null && !isTurkishDisplayQuery(prefix) && prefix.length()<MAX_DEPTH &&
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
        check(roots.size()==44,"ASCII and six Turkish search roots covered");
        check(roots.containsAll(Arrays.asList("ç","ğ","ı","ö","ş","ü")),"all Turkish letters included");
        check(roots.get(0).equals("a")&&roots.contains("0")&&roots.contains(".")&&roots.contains("_"),"username roots preserved");
        check(new HashSet<>(roots).size()==roots.size(),"root prefixes unique");
        check(PrefixSearchLogic.SEARCH_COUNT==1000,"relationship search requests ask for 1000 rows");
        check(PrefixSearchLogic.matches("Abc.Def","ab"),"username prefix match is case insensitive");
        check(!PrefixSearchLogic.matches("name_ab","ab"),"substring-only username result rejected");
        check(PrefixSearchLogic.matchesSearchResult("oznur","Öznur Aksaz","ö"),"Turkish display-name result accepted");
        check(PrefixSearchLogic.matchesSearchResult("isik","Işık","ı"),"Turkish dotless-I folding works");
        check(!PrefixSearchLogic.matchesSearchResult("oznur","Meryem","ö"),"unmatched Turkish display name rejected");
        check(PrefixSearchLogic.children("a").size()==38,"username child alphabet unchanged");
        check(PrefixSearchLogic.children("ş").isEmpty(),"Turkish display search does not create impossible username branches");
        check(PrefixSearchLogic.shouldSplit("a",9,9,10,false),"omission of an already-known username splits");
        check(!PrefixSearchLogic.shouldSplit("ş",1000,1000,0,true),"Turkish display root remains one-shot");
        List<String> names=Arrays.asList("ab1","ab2","ab3","ac1","a9x","other");
        List<String> children=PrefixSearchLogic.prioritizedChildren("a",names);
        check(children.size()==38&&children.get(0).equals("ab"),"densest observed child is first");
        check(children.get(1).equals("ac")&&children.get(2).equals("a9"),"equal populations retain alphabet priority");
        check(PrefixSearchLogic.population("ab",names)==3,"known prefix population counted");
        check(PrefixSearchLogic.population("ş",names)==0,"display roots do not pretend to be username populations");
        check(PrefixSearchLogic.targetedLimit(6)==24,"target allowance scales with the missing count");
        check(PrefixSearchLogic.ROOT_ROUNDS==2,"two independent root sweeps remain enabled");
        System.out.println("PASS: "+checks+" Turkish-search checks");
    }
}
'''
Path('tests/PrefixSearchLogicTest.java').write_text(prefix_test, encoding='utf-8')

client_path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
client=client_path.read_text(encoding='utf-8')
old_path='''    private String listPath(String id,String kind,String query,String rankToken,String cursor,String order) throws Exception {
        return "/api/v1/friendships/"+id+"/"+kind+"/?count=200&search_surface=follow_list_page&query="+URLEncoder.encode(query,"UTF-8")+"&enable_groups=true"+
            ("following".equals(kind)?"&includes_hashtags=false":"")+"&rank_token="+URLEncoder.encode(rankToken,"UTF-8")+
            (order.isEmpty()?"":"&order="+URLEncoder.encode(order,"UTF-8"))+(cursor.isEmpty()?"":"&max_id="+URLEncoder.encode(cursor,"UTF-8"));
    }
'''
new_path='''    private String listPath(String id,String kind,String query,String rankToken,String cursor,String order) throws Exception {
        int count=query.isEmpty()?200:PrefixSearchLogic.SEARCH_COUNT;
        return "/api/v1/friendships/"+id+"/"+kind+"/?count="+count+"&search_surface=follow_list_page&query="+URLEncoder.encode(query,"UTF-8")+"&enable_groups=true"+
            ("following".equals(kind)?"&includes_hashtags=false":"")+"&rank_token="+URLEncoder.encode(rankToken,"UTF-8")+
            (order.isEmpty()?"":"&order="+URLEncoder.encode(order,"UTF-8"))+(cursor.isEmpty()?"":"&max_id="+URLEncoder.encode(cursor,"UTF-8"));
    }
'''
client=replace_once(client,old_path,new_path,'relationship search count')
client=replace_once(client,'if(PrefixSearchLogic.matches(person.username,prefix)){matched.add(person.id);found.put(person.id,person);}','if(PrefixSearchLogic.matchesSearchResult(person.username,person.name,prefix)){matched.add(person.id);found.put(person.id,person);}','Turkish display-name acceptance')
client_path.write_text(client,encoding='utf-8')

profile_path=Path('app/src/main/java/com/blackapps/follow/ProfileLookup.java')
profile=profile_path.read_text(encoding='utf-8')
profile=replace_once(profile,'&include_reel=false&__a=1");','&include_reel=false&count=1000&__a=1");','profile top-search count')
profile_path.write_text(profile,encoding='utf-8')

main_path=Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
main_path.write_text(main_path.read_text(encoding='utf-8').replace('0.4.3','0.4.4'),encoding='utf-8')

gradle=Path('app/build.gradle')
gradle.write_text(replace_once(gradle.read_text(encoding='utf-8'),"versionCode 18; versionName '0.4.3-test'","versionCode 19; versionName '0.4.4-test'",'Gradle version'),encoding='utf-8')

manifest=Path('app/src/main/AndroidManifest.xml')
manifest.write_text(replace_once(manifest.read_text(encoding='utf-8'),'android:versionCode="18" android:versionName="0.4.3-test"','android:versionCode="19" android:versionName="0.4.4-test"','Manifest version'),encoding='utf-8')

build=Path('build-apk.sh')
build.write_text(build.read_text(encoding='utf-8').replace('Black-Follow-0.4.3-test.apk','Black-Follow-0.4.4-test.apk'),encoding='utf-8')

for name in ['README.md','TEST-RESULTS.md','GITHUB-SETUP.md']:
    path=Path(name)
    value=path.read_text(encoding='utf-8').replace('0.4.3','0.4.4').replace('versionCode 18','versionCode 19').replace('versionCode=18','versionCode=19')
    path.write_text(value,encoding='utf-8')

readme=Path('README.md');value=readme.read_text(encoding='utf-8')
marker="Instagram takipçi ve takip listelerine oturumun izin verdiği ölçüde erişip yerel geçmiş tutan bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.\n"
section='''\n## 0.4.4: 1000 sonuçlu arama ve Türkçe harfler\n\n- Takipçi/takip edilenler listesinde sorgu doluysa `count=1000` istenir; normal sayfalama `count=200` olarak kalır.\n- Hesap kullanıcı adı çözümleme aramasına da `count=1000` parametresi eklenmiştir.\n- Harf taramasına `ç`, `ğ`, `ı`, `ö`, `ş`, `ü` eklenmiştir.\n- Türkçe harfler kullanıcı adında bulunamayacağı için bu altı sorgu, ilişki listesinin döndürdüğü kişilerin görünen adında Türkçe yerel eşleşme arar.\n- Türkçe sorgular tek seferliktir; imkânsız kullanıcı adı alt dalları oluşturmaz. Sonuçlar yine sayısal Instagram kimliğiyle tekilleştirilir.\n'''
if section.strip() not in value:value=replace_once(value,marker,marker+section,'README section')
readme.write_text(value,encoding='utf-8')

results=Path('TEST-RESULTS.md');value=results.read_text(encoding='utf-8')
extra='''\n## 0.4.4 ek kontrolleri\n\n- Aramalı ilişki istekleri `count=1000`, sorgusuz normal liste istekleri `count=200` kullanır.\n- Altı Türkçe harf kök taramasına dahildir; `I/ı` ve `İ/i` Türkçe yerel dönüşümü doğrulanır.\n- Türkçe görünen-ad sonuçları ilişki endpointinden geldikten sonra sayısal kimlikle tekilleştirilir ve alt kullanıcı adı dallarına ayrılmaz.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
