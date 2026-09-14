package com.blackapps.follow;
import android.content.Context;
import java.util.*;
public class Store implements AutoCloseable {
    public static int profiles,commits;public static final Account ACCOUNT=new Account();
    public static class Account {public String owner="123",username="target",remote="456";public long id=1,lastSuccess=0,nextDue=Long.MAX_VALUE;public boolean enabled=true;public int failures;}
    public Store(Context c) {}
    public List<Account> accounts(String owner){return Arrays.asList(ACCOUNT);}
    public void profileAttempt(Account a){}
    public void status(long id,String owner,String status,boolean error,long due){}
    public void saveProfile(Account a,InstagramClient.Profile p){profiles++;}
    public long lastEventId(long id,String owner){return 0;}
    public boolean commit(Account a,InstagramClient.Snapshot s,long interval,boolean force){commits++;return true;}
    public String changes(long id,String owner,long before){return "fixture changes";}
    public void delete(long id,String owner){}
    public void profileError(Account a,String message){}
    public void close(){}
}
