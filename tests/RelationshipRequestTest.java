import com.blackapps.follow.RelationshipRequest;
import java.net.URLDecoder;

public class RelationshipRequestTest {
    static int checks;
    static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    static void fails(Runnable r,String name){boolean thrown=false;try{r.run();}catch(Exception e){thrown=true;}check(thrown,name);}
    public static void main(String[] args)throws Exception{
        String page=RelationshipRequest.page("123","followers","123_rank token","cursor value","");
        String decoded=URLDecoder.decode(page,"UTF-8");
        check(decoded.contains("count=200"),"normal page size is 200");
        check(decoded.contains("rank_token=123_rank token"),"normal page keeps one ranking context");
        check(decoded.contains("max_id=cursor value"),"cursor is sent only on list pages");
        check(decoded.contains("query="),"normal page uses an empty query");
        String ordered=URLDecoder.decode(RelationshipRequest.page("123","followers","rank","","date_followed_latest"),"UTF-8");
        check(ordered.contains("order=date_followed_latest"),"optional follower order supported");
        String search=URLDecoder.decode(RelationshipRequest.search("123","followers","a"),"UTF-8");
        check(search.contains("query=a"),"relationship search includes query");
        check(!search.contains("count="),"relationship search never sends an oversized count");
        check(!search.contains("rank_token="),"relationship search is not a ranked cursor traversal");
        check(!search.contains("max_id="),"relationship search is one response, not cursor pagination");
        String following=URLDecoder.decode(RelationshipRequest.search("123","following","ş"),"UTF-8");
        check(following.contains("includes_hashtags=false"),"following search excludes hashtags");
        check(following.contains("query=ş"),"UTF-8 Turkish query preserved");
        check(RelationshipRequest.optionalSearchFailure("BF_HTTP_400"),"HTTP 400 can disable only optional search recovery");
        check(!RelationshipRequest.optionalSearchFailure("BF_HTTP_429"),"rate errors are never swallowed as optional");
        check(RelationshipRequest.PAGE_SIZE==200&&RelationshipRequest.MAX_PAGES==1000,"bounded page constants");
        fails(()->{try{RelationshipRequest.search("bad","followers","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid identity rejected");
        fails(()->{try{RelationshipRequest.search("123","likes","a");}catch(Exception e){throw new RuntimeException(e);}},"invalid relationship type rejected");
        System.out.println("PASS: "+checks+" relationship request checks");
    }
}
