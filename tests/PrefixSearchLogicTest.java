import com.blackapps.follow.PrefixSearchLogic;
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
