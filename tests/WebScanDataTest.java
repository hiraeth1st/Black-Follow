import com.blackapps.follow.*;
import org.json.*;
public class WebScanDataTest {
 static int checks;
 static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 interface Task{void run()throws Exception;}
 static void fails(Task task,String name)throws Exception{try{task.run();throw new AssertionError(name);}catch(java.io.IOException|IllegalArgumentException e){checks++;}}
 static JSONObject page(String cursor,String next,boolean more,String... ids)throws Exception{JSONArray people=new JSONArray();for(String id:ids)people.put(new JSONObject().put("id",id).put("username","person"+id));return new JSONObject().put("cursor",cursor).put("next",next).put("more",more).put("users",people);}
 public static void main(String[]args)throws Exception {
  WebScanData d=new WebScanData(3);d.accept(page("","p2",true,"1","2"));check(!d.complete(),"intermediate count is not complete");d.accept(page("p2","",false,"2","3"));check(d.complete()&&d.people.size()==3,"overlapping boundary retains three identities");check(d.pages()==2,"counts both network pages");
  final WebScanData done=d;fails(()->done.accept(page("","",false,"1")),"terminal traversal cannot restart");
  WebScanData shortList=new WebScanData(3);shortList.accept(page("","",false,"1","2"));check(shortList.terminal()&&!shortList.complete(),"terminal short list remains partial");
  fails(()->new WebScanData(3).accept(page("p2","",false,"1")),"missing first page rejected");
  WebScanData order=new WebScanData(3);order.accept(page("","next",true,"1"));fails(()->order.accept(page("wrong","",false,"2","3")),"out of order response rejected");
  WebScanData exactButMore=new WebScanData(2);exactButMore.accept(page("","p2",true,"1","2"));check(!exactButMore.complete(),"matching count does not override continuation");exactButMore.accept(page("p2","",false,"2"));check(exactButMore.complete(),"repeated final boundary may confirm completion");
  fails(()->new WebScanData(1).accept(page("","",false,"1","2")),"excess people rejected");
  fails(()->new WebScanData(1).accept(page("","",true,"1")),"missing continuation cursor rejected");
  fails(()->new WebScanData(1).accept(page("","",false,"bad")),"invalid identity rejected");
  WebScanData zero=new WebScanData(0);zero.accept(page("","",false));check(zero.complete(),"zero list only completes with explicit empty terminal page");
  System.out.println("PASS: "+checks+" WebView traversal integrity checks");
 }
}
