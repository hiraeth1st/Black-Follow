import com.blackapps.follow.RelationshipRequest;
import java.net.URLDecoder;

public class RelationshipRequestTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void fails(Runnable r,String name){boolean thrown=false;try{r.run();}catch(Exception e){thrown=true;}check(thrown,name);}
    public static void main(String[] args)throws Exception{
        String uuid="123e4567-e89b-12d3-a456-426614174000";
        check(RelationshipRequest.rankToken("123",uuid).equals("123_"+uuid),"rank token follows owner_UUID format");
        String page=RelationshipRequest.page("123","followers",RelationshipRequest.rankToken("123",uuid),"cursor value","");
        String decoded=URLDecoder.decode(page,"UTF-8");
        check(decoded.contains("count=200"),"normal page size is 200");
        check(decoded.contains("rank_token=123_"+uuid),"normal page keeps canonical ranking context");
        check(decoded.contains("max_id=cursor value"),"cursor is sent only on list pages");
        check(decoded.contains("query="),"normal page uses an empty query");
        String ordered=URLDecoder.decode(RelationshipRequest.page("123","followers",RelationshipRequest.rankToken("123",uuid),"","date_followed_latest"),"UTF-8");
        check(ordered.contains("order=date_followed_latest"),"optional follower order supported");
        String web=URLDecoder.decode(RelationshipRequest.searchWeb("123","followers","a"),"UTF-8");
        check(web.contains("count=100"),"web relationship search asks for a supported bounded result size");
        check(web.contains("query=a"),"web relationship search includes query");
        check(!web.contains("rank_token="),"web relationship search is not a ranked traversal");
        check(!web.contains("max_id="),"web relationship search is one response");
        String minimal=URLDecoder.decode(RelationshipRequest.searchMinimal("123","followers","a"),"UTF-8");
        check(!minimal.contains("count="),"minimal search omits count for private-client compatibility");
        check(!minimal.contains("rank_token=")&&!minimal.contains("max_id="),"minimal search omits cursor state");
        String following=URLDecoder.decode(RelationshipRequest.searchWeb("123","following","ş"),"UTF-8");
        check(following.contains("includes_hashtags=false"),"following search excludes hashtags");
        check(following.contains("query=ş"),"UTF-8 Turkish query preserved");
        check(RelationshipRequest.optionalSearchFailure("BF_HTTP_400"),"HTTP 400 can trigger the alternate search shape");
        check(!RelationshipRequest.optionalSearchFailure("BF_HTTP_429"),"rate errors are never swallowed as optional");
        check(RelationshipRequest.PAGE_SIZE==200&&RelationshipRequest.WEB_SEARCH_SIZE==100,"request sizes bounded");
        check(RelationshipRequest.MAX_PAGES==1000&&RelationshipRequest.MAX_STREAMS==10&&RelationshipRequest.MAX_ZERO_STREAMS==2,"adaptive stream limits fixed");
        fails(()->RelationshipRequest.rankToken("bad",uuid),"invalid rank owner rejected");
        fails(()->RelationshipRequest.rankToken("123","bad"),"invalid rank UUID rejected");
        fails(()->{try{RelationshipRequest.searchWeb("bad","followers","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid identity rejected");
        fails(()->{try{RelationshipRequest.searchMinimal("123","likes","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid relationship type rejected");
        System.out.println("PASS: "+checks+" relationship request checks");
    }
}