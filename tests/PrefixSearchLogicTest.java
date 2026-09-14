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
