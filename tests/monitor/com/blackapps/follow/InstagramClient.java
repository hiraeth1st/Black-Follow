package com.blackapps.follow;
import android.content.Context;
public class InstagramClient {
    public static boolean partial;private ListObserver observer;
    public static int requests;public static AccessError profileFailure,snapshotFailure;
    public static class AccessError extends java.io.IOException {
        public final boolean auth,rate;public final long retryAfterMs;
        public AccessError(String msg,boolean auth,boolean rate){this(msg,auth,rate,-1);}
        public AccessError(String msg,boolean auth,boolean rate,long delay){super(msg);this.auth=auth;this.rate=rate;retryAfterMs=delay;}
    }
    public interface ListObserver {void received(String kind,java.util.LinkedHashMap<String,Store.Edge> people,int expected)throws Exception;}
    public void observeLists(ListObserver observer){this.observer=observer;}
    public static class PartialLists extends java.io.IOException {public PartialLists(String message){super(message);}}
    public static class Profile {}
    public static class Snapshot {}
    public interface Progress {void page(String kind,int page,int received,int expected);}
    public InstagramClient(Context c,String owner,long deadline) {}
    public InstagramClient(Context c,String owner,long deadline,Progress progress) {}
    public Profile readProfile(Store.Account a)throws AccessError {requests++;if(profileFailure!=null)throw profileFailure;return new Profile();}
    public Snapshot snapshot(Store.Account a,Profile p,long start)throws Exception {requests++;if(partial){observer.received("followers",new java.util.LinkedHashMap<>(),200);throw new PartialLists("preview [BF_LIST_PARTIAL]");}if(snapshotFailure!=null)throw snapshotFailure;return new Snapshot();}
}
