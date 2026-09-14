import com.blackapps.follow.PreviewLogic;
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
