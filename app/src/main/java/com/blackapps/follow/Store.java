package com.blackapps.follow;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public Store(Context c) { super(c, "black-follow.db", null, 3); }
    public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, owner TEXT NOT NULL, remote TEXT, username TEXT NOT NULL, title TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, followers INTEGER, following INTEGER, last_started INTEGER NOT NULL DEFAULT 0, last_success INTEGER NOT NULL DEFAULT 0, last_attempt INTEGER NOT NULL DEFAULT 0, next_due INTEGER NOT NULL DEFAULT 0, failures INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'İlk kontrol bekleniyor', UNIQUE(owner,username), UNIQUE(owner,remote))");
        db.execSQL("CREATE TABLE edges (account INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, kind TEXT NOT NULL, person TEXT NOT NULL, username TEXT NOT NULL, name TEXT NOT NULL, since INTEGER NOT NULL, lower_bound INTEGER NOT NULL, PRIMARY KEY(account,kind,person))");
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY, account INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, kind TEXT NOT NULL, action TEXT NOT NULL, person TEXT NOT NULL, username TEXT NOT NULL, name TEXT NOT NULL, lower_bound INTEGER NOT NULL, detected INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX events_account_time ON events(account, detected DESC, id DESC)");
        addProfileColumns(db);
        addHistoryColumns(db);
    }
    private void addProfileColumns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE accounts ADD COLUMN profile_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE accounts ADD COLUMN profile_attempt INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE accounts SET profile_at=last_success");
    }
    private void addHistoryColumns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE edges ADD COLUMN avatar TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE TABLE observations (id INTEGER PRIMARY KEY, account INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE, observed INTEGER NOT NULL, followers INTEGER NOT NULL, following INTEGER NOT NULL, source TEXT NOT NULL)");
        db.execSQL("CREATE INDEX observations_account_time ON observations(account,observed,id)");
        db.execSQL("CREATE INDEX events_person_history ON events(account,kind,person,id)");
        db.execSQL("INSERT INTO observations(account,observed,followers,following,source) SELECT id,profile_at,followers,following,'migrated' FROM accounts WHERE profile_at>0 AND followers IS NOT NULL AND following IS NOT NULL");
    }
    public void onUpgrade(SQLiteDatabase db,int a,int b) {if(a<2) addProfileColumns(db);if(a<3)addHistoryColumns(db);}
    public static final class Account {
        public long id,lastStarted,lastSuccess,lastAttempt,nextDue,profileAt,profileAttempt;
        public int followers,following,failures;
        public boolean enabled;
        public String owner,remote,username,title,status;
    }
    private static Account account(Cursor c) {
        Account a=new Account();
        a.id=c.getLong(c.getColumnIndexOrThrow("id")); a.owner=str(c,"owner"); a.remote=str(c,"remote");
        a.username=str(c,"username"); a.title=str(c,"title"); a.status=str(c,"status");
        a.enabled=c.getInt(c.getColumnIndexOrThrow("enabled"))==1;
        a.followers=c.isNull(c.getColumnIndexOrThrow("followers"))?-1:c.getInt(c.getColumnIndexOrThrow("followers"));
        a.following=c.isNull(c.getColumnIndexOrThrow("following"))?-1:c.getInt(c.getColumnIndexOrThrow("following"));
        a.lastStarted=c.getLong(c.getColumnIndexOrThrow("last_started")); a.lastSuccess=c.getLong(c.getColumnIndexOrThrow("last_success"));
        a.lastAttempt=c.getLong(c.getColumnIndexOrThrow("last_attempt")); a.nextDue=c.getLong(c.getColumnIndexOrThrow("next_due")); a.failures=c.getInt(c.getColumnIndexOrThrow("failures"));
        a.profileAt=c.getLong(c.getColumnIndexOrThrow("profile_at"));a.profileAttempt=c.getLong(c.getColumnIndexOrThrow("profile_attempt"));
        return a;
    }
    private static String str(Cursor c,String col) { String s=c.getString(c.getColumnIndexOrThrow(col)); return s==null?"":s; }
    public Account get(long id,String owner) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM accounts WHERE id=? AND owner=?",new String[]{""+id,owner})) { return c.moveToFirst()?account(c):null; }
    }
    public List<Account> accounts(String owner) {
        List<Account> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM accounts WHERE owner=? ORDER BY last_attempt ASC,id ASC",new String[]{owner})) { while(c.moveToNext()) result.add(account(c)); }
        return result;
    }
    public long add(String owner,String username) {
        if(!owner.matches("[0-9]+") || !username.matches("[a-z0-9._]{1,30}")) throw new IllegalArgumentException("Geçerli bir Instagram kullanıcı adı gir.");
        ContentValues v=new ContentValues(); v.put("owner",owner);v.put("username",username);
        getWritableDatabase().insertWithOnConflict("accounts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM accounts WHERE owner=? AND username=?",new String[]{owner,username})) {
            if(!c.moveToFirst()) throw new IllegalStateException("Hesap kaydedilemedi."); return c.getLong(0);
        }
    }
    public void enabled(long id,String owner,boolean enabled) {
        ContentValues v=new ContentValues();v.put("enabled",enabled?1:0);
        getWritableDatabase().update("accounts",v,"id=? AND owner=?",new String[]{""+id,owner});
    }
    public void delete(long id,String owner) { getWritableDatabase().delete("accounts","id=? AND owner=?",new String[]{""+id,owner}); }
    public void profileAttempt(Account a) {
        ContentValues v=new ContentValues();v.put("profile_attempt",System.currentTimeMillis());
        getWritableDatabase().update("accounts",v,"id=? AND owner=?",new String[]{""+a.id,a.owner});
    }
    public void saveProfile(Account a,InstagramClient.Profile p) {
        if(!a.remote.isEmpty() && !a.remote.equals(p.id)) throw new IllegalArgumentException("Hesap kimliği uyuşmuyor.");
        ContentValues v=new ContentValues();v.put("remote",p.id);v.put("username",p.username);v.put("title",p.name);v.put("followers",p.followers);v.put("following",p.following);v.put("profile_at",System.currentTimeMillis());
        v.put("status",a.lastSuccess==0?"Profil sayıları alındı • kişi listeleri henüz alınmadı":"Profil sayıları yenilendi • kişi listeleri son taramaya ait");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try {
            if(db.update("accounts",v,"id=? AND owner=?",new String[]{""+a.id,a.owner})==1) observe(a.id,System.currentTimeMillis(),p.followers,p.following,"profile");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void observe(long account,long time,int followers,int following,String source) {
        ContentValues v=new ContentValues();v.put("account",account);v.put("observed",time);v.put("followers",followers);v.put("following",following);v.put("source",source);getWritableDatabase().insertOrThrow("observations",null,v);
    }
    public void profileError(Account a,String error) {
        ContentValues v=new ContentValues();v.put("status",error);
        getWritableDatabase().update("accounts",v,"id=? AND owner=?",new String[]{""+a.id,a.owner});
    }
    public void status(long id,String owner,String status,boolean failed,long nextDue) {
        ContentValues v=new ContentValues();v.put("status",status); v.put("last_attempt",System.currentTimeMillis());
        if(failed) { Account a=get(id,owner);v.put("failures",a==null?1:a.failures+1);v.put("next_due",nextDue); }
        getWritableDatabase().update("accounts",v,"id=? AND owner=?",new String[]{""+id,owner});
    }
    public static class Edge {
        public String id,username,name,avatar="";
        public long since,lower;
        public Edge(String i,String u,String n) { id=i;username=u;name=n; }
    }
    private LinkedHashMap<String,Edge> previous(long account,String kind) {
        LinkedHashMap<String,Edge> out=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT person,username,name,since,lower_bound,avatar FROM edges WHERE account=? AND kind=?",new String[]{""+account,kind})) {
            while(c.moveToNext()) { Edge e=new Edge(c.getString(0),c.getString(1),c.getString(2));e.since=c.getLong(3);e.lower=c.getLong(4);e.avatar=c.getString(5);out.put(e.id,e); }
        }
        return out;
    }
    private void event(Account a,String kind,String action,Edge e,long end) {
        ContentValues v=new ContentValues();v.put("account",a.id);v.put("kind",kind);v.put("action",action);v.put("person",e.id);v.put("username",e.username);v.put("name",e.name);v.put("lower_bound",a.lastStarted);v.put("detected",end);
        getWritableDatabase().insertOrThrow("events",null,v);
    }
    private void apply(Account a,String kind,LinkedHashMap<String,Edge> fresh,long end) {
        LinkedHashMap<String,Edge> old=previous(a.id,kind);
        RelationLogic.Change diff=RelationLogic.compare(a.lastSuccess==0?null:old.keySet(),fresh.keySet());
        for(String id:diff.added) event(a,kind,"added",fresh.get(id),end);
        for(String id:diff.removed) event(a,kind,"removed",old.get(id),end);
        SQLiteDatabase db=getWritableDatabase(); db.delete("edges","account=? AND kind=?",new String[]{""+a.id,kind});
        for(Edge e:fresh.values()) {
            Edge p=old.get(e.id);long since=p!=null?p.since:(a.lastSuccess==0?0:end), lower=p!=null?p.lower:(a.lastSuccess==0?0:a.lastStarted);
            ContentValues v=new ContentValues();v.put("account",a.id);v.put("kind",kind);v.put("person",e.id);v.put("username",e.username);v.put("name",e.name);v.put("since",since);v.put("lower_bound",lower);
            v.put("avatar",e.avatar.isEmpty()&&p!=null?p.avatar:e.avatar);
            db.insertOrThrow("edges",null,v);
        }
    }
    public boolean commit(Account a,InstagramClient.Snapshot s,long interval,boolean manual) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Account latest=get(a.id,a.owner); if(latest==null || (!latest.enabled&&!manual)) return false;
            if(!a.remote.isEmpty() && !a.remote.equals(s.profile.id)) throw new IllegalStateException("Hesap kimliği değişti; kayıt yapılmadı.");
            apply(a,"followers",s.followers,s.end);apply(a,"following",s.following,s.end);
            ContentValues v=new ContentValues();v.put("remote",s.profile.id);v.put("username",s.profile.username);v.put("title",s.profile.name);v.put("followers",s.followers.size());v.put("following",s.following.size());v.put("last_started",s.start);v.put("last_success",s.end);v.put("last_attempt",s.end);v.put("next_due",s.end+interval);v.put("failures",0);v.put("status","İki liste alındı • "+(a.lastSuccess==0?"ilk kayıt":"geçmiş güncellendi"));
            v.put("profile_at",s.end);
            observe(a.id,s.end,s.followers.size(),s.following.size(),"full");
            db.update("accounts",v,"id=? AND owner=?",new String[]{""+a.id,a.owner}); db.setTransactionSuccessful();return true;
        } finally { db.endTransaction(); }
    }
    public Cursor edges(long account,String owner,String kind,String search,int offset) {
        String q="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        return getReadableDatabase().rawQuery("SELECT e.username,e.name,e.since,e.lower_bound,e.avatar,e.person FROM edges e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.kind=? AND (e.username LIKE ? ESCAPE '\\' OR e.name LIKE ? ESCAPE '\\') ORDER BY e.since DESC,e.username COLLATE NOCASE LIMIT 101 OFFSET ?",new String[]{""+account,owner,kind,q,q,""+offset});
    }
    private static final String REPEATS="(SELECT COUNT(*) FROM events x WHERE x.account=e.account AND x.kind=e.kind AND x.person=e.person AND x.action='added' AND x.id<=e.id)";
    private static final String REMOVED="(SELECT COALESCE(MAX(x.detected),0) FROM events x WHERE x.account=e.account AND x.kind=e.kind AND x.person=e.person AND x.action='removed' AND x.id<e.id)";
    public Cursor events(long account,String owner,String search,String kind,String action,int offset) { String q="%"+search.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"; return getReadableDatabase().rawQuery("SELECT e.kind,e.action,e.username,e.name,e.lower_bound,e.detected,"+REPEATS+","+REMOVED+" FROM events e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND (?='' OR e.kind=?) AND (?='' OR e.action=?) AND (e.username LIKE ? ESCAPE '\\' OR e.name LIKE ? ESCAPE '\\') ORDER BY e.detected DESC,e.id DESC LIMIT 101 OFFSET ?",new String[]{""+account,owner,kind,kind,action,action,q,q,""+offset}); }
    public long lastEventId(long account,String owner) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(MAX(e.id),0) FROM events e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=?",new String[]{""+account,owner})){c.moveToFirst();return c.getLong(0);}
    }
    public String changes(long account,String owner,long after) {
        int total=0;StringBuilder b=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.kind,e.action,e.username,"+REPEATS+","+REMOVED+" FROM events e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.id>? ORDER BY e.id",new String[]{""+account,owner,""+after})) {
            while(c.moveToNext()){total++;if(total<=20)b.append("\n• ").append(HistoryText.event(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getLong(4),TimeZone.getDefault()));}
        }
        return total==0?"\nBu kontrolde yeni hareket yok.":"\n"+total+" hareket tespit edildi:"+b+(total>20?"\nDiğer hareketler Hareketler sekmesinde ve dışa aktarmada.":"");
    }
    public NewPeople newPeople(long account,String owner,long after) {
        NewPeople result=new NewPeople();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.kind,e.action,e.username FROM events e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.id>? AND e.action='added' ORDER BY e.id",new String[]{""+account,owner,""+after})) {
            while(c.moveToNext())result.add(false,c.getString(0),c.getString(1),c.getString(2));
        }
        return result;
    }
    public void export(long account,String owner,java.io.Writer out) throws java.io.IOException {
        SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();
        try {
            Account a=get(account,owner);if(a==null)throw new java.io.IOException("Bu oturumda hesap kaydı bulunamadı.");
            HistoryText.Report report=new HistoryText.Report(out,a.username,System.currentTimeMillis(),a.profileAt,a.lastSuccess,TimeZone.getDefault());
            String sql="SELECT o.observed AS time,0 AS type,o.id AS ordering,o.followers,o.following,o.source,'' AS kind,'' AS action,'' AS username,0 AS repeats,0 AS removed,0 AS lower_bound FROM observations o JOIN accounts a ON a.id=o.account WHERE o.account=? AND a.owner=? UNION ALL SELECT e.detected,1,e.id,0,0,'',e.kind,e.action,e.username,"+REPEATS+","+REMOVED+",e.lower_bound FROM events e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? ORDER BY time,type,ordering";
            try(Cursor c=db.rawQuery(sql,new String[]{""+account,owner,""+account,owner})) {while(c.moveToNext()) {
                if(c.getInt(1)==0)report.counts(c.getLong(0),c.getInt(3),c.getInt(4),c.getString(5));
                else report.event(c.getLong(0),c.getString(6),c.getString(7),c.getString(8),c.getInt(9),c.getLong(10),c.getLong(11));
            }}
            report.currentLists(a.lastSuccess);
            if(a.lastSuccess>0) for(String kind:new String[]{"followers","following"}) {
                report.listHeading(kind);
                int count=0;
                try(Cursor c=db.rawQuery("SELECT e.username,e.name,e.since,e.lower_bound FROM edges e JOIN accounts a ON a.id=e.account WHERE e.account=? AND a.owner=? AND e.kind=? ORDER BY e.username COLLATE NOCASE",new String[]{""+account,owner,kind})) {
                    while(c.moveToNext()){report.person(c.getString(0),c.getString(1),c.getLong(2),c.getLong(3));count++;}
                }
                report.listCount(count);
            }
            report.finish();db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
