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
        check(PrefixSearchLogic.shouldSplit("a",40,false),"large result splits before a likely search cap");
        check(PrefixSearchLogic.shouldSplit("a",1,true),"server continuation or cursor problem splits");
        check(!PrefixSearchLogic.shouldSplit("abc",100,true),"bounded depth prevents unbounded requests");
        List<String> children=PrefixSearchLogic.children("a");
        check(children.size()==38&&children.get(0).equals("aa")&&children.contains("a_"),"children partition the prefix namespace");
        check(PrefixSearchLogic.children("abc").isEmpty(),"maximum depth enforced");
        check(PrefixSearchLogic.MAX_QUERIES>=roots.size(),"query budget covers every root once");
        System.out.println("PASS: "+checks+" prefix-search checks");
    }
}
