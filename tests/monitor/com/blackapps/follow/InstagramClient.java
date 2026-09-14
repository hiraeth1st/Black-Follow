package com.blackapps.follow;
import android.content.Context;
public class InstagramClient {
    public static int requests;public static AccessError profileFailure,snapshotFailure;
    public static class AccessError extends java.io.IOException {
        public final boolean auth,rate;public final long retryAfterMs;
        public AccessError(String msg,boolean auth,boolean rate){this(msg,auth,rate,-1);}
        public AccessError(String msg,boolean auth,boolean rate,long delay){super(msg);this.auth=auth;this.rate=rate;retryAfterMs=delay;}
    }
    public static class Profile {}
    public static class Snapshot {}
    public InstagramClient(Context c,String owner,long deadline) {}
    public Profile readProfile(Store.Account a)throws AccessError {requests++;if(profileFailure!=null)throw profileFailure;return new Profile();}
    public Snapshot snapshot(Store.Account a,Profile p,long start)throws AccessError {requests++;if(snapshotFailure!=null)throw snapshotFailure;return new Snapshot();}
}
