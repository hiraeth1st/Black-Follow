import com.blackapps.follow.RelationLogic;
import java.util.*;

public class RelationLogicTest {
    private static int checks;
    static Set<String> set(String...values){return new LinkedHashSet<>(Arrays.asList(values));}
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void fails(Runnable r,String name){boolean thrown=false;try{r.run();}catch(IllegalArgumentException e){thrown=true;}check(thrown,name);}
    public static void main(String[] args){
        RelationLogic.Change first=RelationLogic.compare(null,set("1","2"));check(first.added.isEmpty()&&first.removed.isEmpty(),"baseline must not invent times");
        RelationLogic.Change addition=RelationLogic.compare(set(),set("1"));check(addition.added.equals(set("1")),"empty established baseline is not initial baseline");
        RelationLogic.Change change=RelationLogic.compare(set("1","2"),set("2","3"));check(change.added.equals(set("3")),"new follower");check(change.removed.equals(set("1")),"removed follower");
        RelationLogic.Change rename=RelationLogic.compare(set("123"),set("123"));check(rename.added.isEmpty()&&rename.removed.isEmpty(),"rename keeps stable identity");
        check(RelationLogic.compare(set("1"),set()).removed.equals(set("1")),"complete empty list removes last person");
        check(RelationLogic.compare(set(),set("1")).added.equals(set("1")),"re-follow is a new event");
        RelationLogic.Pages pages=new RelationLogic.Pages(3);pages.add(set("1","2"),"page2",true);pages.add(set("3"),"",false);pages.finish();check(true,"complete pagination");
        RelationLogic.Pages empty=new RelationLogic.Pages(0);empty.add(set(),"",false);empty.finish();check(true,"complete empty list");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"",false);p.finish();},"partial result cannot erase history");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(2);p.add(set("1"),"x",true);p.finish();},"unfinished pages cannot commit");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(2);p.add(set("1"),"x",true);p.add(set("1"),"",false);p.finish();},"duplicates cannot hide a missing unique identity");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"x",true);p.add(set("2"),"x",true);},"cursor loop");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(set(),"x",true);},"empty intermediate page");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(set("1","2"),"",false);},"count changed during scan");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(set("not-an-id"),"",false);},"invalid identity");
        fails(()->new RelationLogic.Pages(RelationLogic.MAX_EXPECTED+1),"explicit size cap");check(new RelationLogic.Pages(10001).count()==0,"lists above the former 10k cap are accepted");
        fails(()->new RelationLogic.Pages(-1),"missing count not interpreted as zero");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(0);p.add(set(),"",false);p.add(set(),"",false);},"no data accepted after final page");
        RelationLogic.Pages overlap=new RelationLogic.Pages(3);overlap.add(Arrays.asList("1","1","2"),"next",true);overlap.add(Arrays.asList("2","3"),"",false);overlap.finish();check(overlap.count()==3,"same-page and cross-page duplicates merge by identity");
        RelationLogic.Pages duplicateOnly=new RelationLogic.Pages(2);duplicateOnly.add(set("1","2"),"next",true);duplicateOnly.add(set("2"),"",false);duplicateOnly.finish();check(duplicateOnly.count()==2,"terminal repeated boundary can complete exact unique count");
        RelationLogic.Pages delayed=new RelationLogic.Pages(3);delayed.add(set("1","2"),"p2",true);delayed.add(set("2"),"p3",true);delayed.add(set("3"),"",false);delayed.finish();check(delayed.count()==3,"one duplicate-only intermediate page can advance to new data");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"p2",true);p.add(set("1"),"p3",true);p.add(set("1"),"p4",true);p.add(set("1"),"p5",true);},"repeated non-progress stops even with changing cursors");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(1);p.add(Arrays.asList((String)null),"",false);},"null identity is rejected cleanly");
        fails(()->{RelationLogic.Pages p=new RelationLogic.Pages(3);p.add(set("1"),"p2",true);p.add(set("1"),"p2",true);},"duplicate-only cursor loop still rejected");
        System.out.println("PASS: "+checks+" data-integrity checks");
    }
}
