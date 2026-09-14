import com.blackapps.follow.PrefixSearchLogic;
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
        check(PrefixSearchLogic.shouldSplit("a",40,40,false),"dense matching result splits");
        check(PrefixSearchLogic.shouldSplit("a",1,1,true),"server-limited result splits");
        check(!PrefixSearchLogic.shouldSplit("a",0,50,false),"full-name noise alone does not explode the queue");
        check(!PrefixSearchLogic.shouldSplit("abcd",100,100,true),"bounded depth prevents unbounded requests");
        List<String> names=Arrays.asList("ab1","ab2","ab3","ac1","a9x","other");
        List<String> children=PrefixSearchLogic.prioritizedChildren("a",names);
        check(children.size()==38&&children.get(0).equals("ab"),"densest observed child is first");
        check(children.get(1).equals("ac")&&children.get(2).equals("a9"),"equal populations retain alphabet priority");
        check(PrefixSearchLogic.population("ab",names)==3,"known prefix population counted");
        check(PrefixSearchLogic.children("abcd").isEmpty(),"maximum depth enforced");
        check(PrefixSearchLogic.targetedLimit(1)==12,"small gaps receive a useful targeted allowance");
        check(PrefixSearchLogic.targetedLimit(6)==24,"target allowance scales with the missing count");
        check(PrefixSearchLogic.targetedLimit(100)==PrefixSearchLogic.MAX_TARGETED,"target allowance is capped");
        check(PrefixSearchLogic.MAX_QUERIES>=roots.size()+PrefixSearchLogic.MAX_TARGETED,"budget covers targets and every root");
        System.out.println("PASS: "+checks+" adaptive prefix-search checks");
    }
}
