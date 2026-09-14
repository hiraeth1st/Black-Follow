"""Run the actual CREATE/ALTER SQL from Store.java on a seeded v1 SQLite DB."""
from pathlib import Path
import json,re,sqlite3

source=(Path(__file__).resolve().parents[1]/'app/src/main/java/com/blackapps/follow/Store.java').read_text()
create=source.split('public void onCreate(SQLiteDatabase db) {',1)[1].split('private void addProfileColumns',1)[0]
migrate=source.split('private void addProfileColumns(SQLiteDatabase db) {',1)[1].split('private void addHistoryColumns',1)[0]
def statements(block):
    return [json.loads('"'+s+'"') for s in re.findall(r'db\.execSQL\("((?:[^"\\]|\\.)*)"\)',block)]
db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
for sql in statements(create):db.execute(sql)
db.execute("INSERT INTO accounts(id,owner,remote,username,followers,following,last_success,last_started) VALUES(1,'123','456','target',1,2,1000,900)")
db.execute("INSERT INTO edges VALUES(1,'followers','789','person','Person',0,0)")
db.execute("INSERT INTO events VALUES(1,1,'following','added','999','other','Other',800,1000)")
before_edges=db.execute('SELECT account,kind,person,username,name,since,lower_bound FROM edges').fetchall()
before_events=db.execute('SELECT * FROM events').fetchall()
db.commit()
with db:
    for sql in statements(migrate):db.execute(sql)
assert db.execute('SELECT profile_at,profile_attempt,followers,following FROM accounts').fetchone()==(1000,0,1,2)
assert db.execute('SELECT account,kind,person,username,name,since,lower_bound FROM edges').fetchall()==before_edges
assert db.execute('SELECT * FROM events').fetchall()==before_events
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
# Profile counts and snapshots have separate timestamps. A later failed list transaction
# must not roll back a profile already persisted in its own transaction.
with db:db.execute('UPDATE accounts SET followers=3,following=4,profile_at=2000 WHERE id=1')
try:
    with db:
        db.execute('DELETE FROM edges WHERE account=1')
        raise RuntimeError('list request failed')
except RuntimeError:pass
assert db.execute('SELECT followers,following,profile_at,last_success FROM accounts').fetchone()==(3,4,2000,1000)
assert db.execute('SELECT account,kind,person,username,name,since,lower_bound FROM edges').fetchall()==before_edges
assert db.execute('SELECT * FROM events').fetchall()==before_events
history=source.split('private void addHistoryColumns(SQLiteDatabase db) {',1)[1].split('private void addPreviewTables',1)[0]
with db:
    for sql in statements(history):db.execute(sql)
assert db.execute('SELECT avatar FROM edges').fetchone()==('',)
assert db.execute('SELECT observed,followers,following,source FROM observations').fetchone()==(2000,3,4,'migrated')
assert db.execute('SELECT account,kind,person,username,name,since,lower_bound FROM edges').fetchall()==before_edges
assert db.execute('SELECT * FROM events').fetchall()==before_events
# A second owner watching the same remote profile must have entirely independent history.
db.execute("INSERT INTO accounts(id,owner,remote,username) VALUES(2,'other-owner','456','target')")
db.execute("INSERT INTO events(account,kind,action,person,username,name,lower_bound,detected) VALUES(2,'following','added','999','private_other_owner','Other',0,1500)")
# Execute the production SQL strings, including its correlated recurrence predicates.
constants={}
for name in ('REPEATS','REMOVED'):
    constants[name]=json.loads('"'+re.search(r'private static final String '+name+r'="((?:[^"\\]|\\.)*)";',source).group(1)+'"')
def java_sql(expression):
    return ''.join(json.loads(token) if token.startswith('"') else constants[token] for token in re.findall(r'"(?:[^"\\]|\\.)*"|REPEATS|REMOVED',expression))
export_body=source.split('public void export(',1)[1]
export_expr=re.search(r'String sql=(.*?);',export_body).group(1)
export_sql=java_sql(export_expr)
rows=db.execute(export_sql,('1','123','1','123')).fetchall()
assert all(row[8]!='private_other_owner' for row in rows)
assert db.execute(export_sql,('1','other-owner','1','other-owner')).fetchall()==[]
# Same numeric identity under a new username remains the same recurrence series.
for action,username,t in [('removed','other',2100),('added','other_renamed',2200),('removed','other_renamed',2300),('added','other_latest',2400)]:
    db.execute("INSERT INTO events(account,kind,action,person,username,name,lower_bound,detected) VALUES(1,'following',?,'999',?,'Other',2000,?)",(action,username,t))
rows=db.execute(export_sql,('1','123','1','123')).fetchall()
last=[r for r in rows if r[8]=='other_latest'][0]
assert last[9]==3 and last[10]==2300
assert [r[0] for r in rows]==sorted(r[0] for r in rows)
for i in range(205):
    db.execute("INSERT INTO events(account,kind,action,person,username,name,lower_bound,detected) VALUES(1,'followers','added',?,?,'',0,?)",(str(10000+i),'person'+str(i),3000+i))
rows=db.execute(export_sql,('1','123','1','123')).fetchall()
assert any(r[8]=='person204' for r in rows)
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
notification_body=source.split('public NewPeople newPeople(',1)[1].split('public void export(',1)[0]
notification_sql=java_sql(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',notification_body).group(1))
latest_id=db.execute("SELECT MAX(id) FROM events WHERE account=1").fetchone()[0]
assert db.execute(notification_sql,('1','other-owner','0')).fetchall()==[]
assert db.execute(notification_sql,('1','123',str(latest_id))).fetchall()==[]
new_rows=db.execute(notification_sql,('1','123','1')).fetchall()
assert all(r[1]=='added' and r[2]!='private_other_owner' for r in new_rows)
assert len(new_rows)==207
# Production filtered-history SQL keeps recurrence across the whole history.
events_body=source.split('public Cursor events(',1)[1].split('public long lastEventId',1)[0]
events_sql=java_sql(re.search(r'rawQuery\((.*?),new String',events_body).group(1))
def filtered(owner='123',kind='',action='',q='%',offset=0):
    return db.execute(events_sql,('1',owner,kind,kind,action,action,q,q,str(offset))).fetchall()
assert len(filtered(kind='followers',action='added'))==101
assert len(filtered(kind='followers',action='added',offset=100))==101
assert len(filtered(kind='followers',action='added',offset=200))==5
assert filtered(owner='other-owner')==[]
assert all(r[0]=='following' and r[1]=='removed' for r in filtered(kind='following',action='removed'))
assert len(filtered(q='%other_latest%'))==1 and filtered(q='%other_latest%')[0][6]==3
assert len(filtered(action='removed'))==2
# Literal wildcard searches must use bound, escaped patterns.
db.execute("INSERT INTO events(account,kind,action,person,username,name,lower_bound,detected) VALUES(1,'followers','added','888','literal_name','100% Real',0,5000)")
assert len(filtered(q='%literal\\_name%'))==1
assert len(filtered(q='%100\\%%'))==1
# Exported current lists use the same owner guard and no 100-row page limit.
current_body=source.split('report.currentLists(',1)[1]
current_sql=java_sql(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',current_body).group(1))
for i in range(205):
    db.execute("INSERT INTO edges(account,kind,person,username,name,since,lower_bound,avatar) VALUES(1,'following',?,?,'',0,0,'')",(str(20000+i),'current'+str(i)))
assert len(db.execute(current_sql,('1','123','following')).fetchall())==205
assert db.execute(current_sql,('1','other-owner','following')).fetchall()==[]
assert db.execute(current_sql,('1','123','followers')).fetchone()[0]=='person'
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
# Self profile identity is resolved by the signed-in numeric ID, never username alone.
self_body=source.split('public Account self(',1)[1].split('public long ensureSelf',1)[0]
self_sql=java_sql(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',self_body).group(1))
assert db.execute(self_sql,('123','123')).fetchall()==[]
db.execute("INSERT INTO accounts(id,owner,remote,username) VALUES(3,'123','123','viewer_before_rename')")
assert db.execute(self_sql,('123','123')).fetchone()[0]==3
assert db.execute(self_sql,('other-owner','other-owner')).fetchall()==[]
db.execute("UPDATE accounts SET username='viewer_after_rename' WHERE id=3")
assert db.execute(self_sql,('123','123')).fetchone()[0]==3
# The same remote profile observed under another login must not produce self alerts.
db.execute("INSERT INTO accounts(id,owner,remote,username) VALUES(4,'other-owner','123','viewer_after_rename')")
for account in [3,4]:
    for kind,action,person in [('followers','removed','departed'),('following','removed','unfollowed'),('followers','added','arrived'),('following','added','followed')]:
        db.execute("INSERT INTO events(account,kind,action,person,username,name,lower_bound,detected) VALUES(?,?,?,?,?,'',0,6000)",(account,kind,action,person,person))
self_alerts=db.execute(notification_sql,('3','123','0')).fetchall()
assert len(self_alerts)==3
assert ('followers','removed','departed') in self_alerts
assert all(not(r[0]=='following' and r[1]=='removed') for r in self_alerts)
assert db.execute(notification_sql,('3','other-owner','0')).fetchall()==[]
assert all(r[1]=='added' for r in db.execute(notification_sql,('4','other-owner','0')).fetchall())
last_self=db.execute('SELECT MAX(id) FROM events WHERE account=3').fetchone()[0]
assert db.execute(notification_sql,('3','123',str(last_self))).fetchall()==[]
self_departures=db.execute(events_sql,('3','123','followers','followers','removed','removed','%','%','0')).fetchall()
assert len(self_departures)==1 and self_departures[0][2]=='departed'
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
# Execute v3 -> v4 migration without touching confirmed lists or events.
preview_migration=source.split('private void addPreviewTables(SQLiteDatabase db) {',1)[1].split('public void onUpgrade',1)[0]
prior_edges=db.execute('SELECT * FROM edges ORDER BY account,kind,person').fetchall()
prior_events=db.execute('SELECT * FROM events ORDER BY id').fetchall()
prior_success=db.execute('SELECT id,last_success FROM accounts ORDER BY id').fetchall()
for sql in statements(preview_migration):db.execute(sql)
assert db.execute('SELECT * FROM edges ORDER BY account,kind,person').fetchall()==prior_edges
assert db.execute('SELECT * FROM events ORDER BY id').fetchall()==prior_events
assert db.execute('SELECT id,last_success FROM accounts ORDER BY id').fetchall()==prior_success
# Preview publication is independent of confirmed rows and supports paginated reads.
db.execute("INSERT INTO previews VALUES(1,'followers',200,182,9000)")
for i in range(182):db.execute("INSERT INTO preview_edges VALUES(1,'followers',?,?,?,'')",(str(90000+i),'preview'+str(i),'Preview'))
meta_body=source.split('public Preview preview(',1)[1].split('public void savePreview',1)[0]
meta_sql=java_sql(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',meta_body).group(1))
assert db.execute(meta_sql,('1','123','followers')).fetchone()==(200,182,9000)
assert db.execute(meta_sql,('1','other-owner','followers')).fetchall()==[]
preview_body=source.split('public Cursor previewEdges(',1)[1].split('private LinkedHashMap',1)[0]
preview_sql=java_sql(re.search(r'rawQuery\(("(?:[^"\\]|\\.)*")',preview_body).group(1))
assert len(db.execute(preview_sql,('1','123','followers','%','%','0')).fetchall())==101
assert len(db.execute(preview_sql,('1','123','followers','%','%','100')).fetchall())==82
assert db.execute(preview_sql,('1','other-owner','followers','%','%','0')).fetchall()==[]
assert db.execute('SELECT * FROM edges ORDER BY account,kind,person').fetchall()==prior_edges
assert db.execute('SELECT * FROM events ORDER BY id').fetchall()==prior_events
assert db.execute('SELECT id,last_success FROM accounts ORDER BY id').fetchall()==prior_success
# Successful full commit deletes previews transactionally; failed commit restores them.
db.commit()
try:
    with db:
        db.execute('DELETE FROM previews WHERE account=1')
        raise RuntimeError('rollback full commit')
except RuntimeError:pass
assert db.execute('SELECT COUNT(*) FROM preview_edges').fetchone()[0]==182
with db:db.execute('DELETE FROM previews WHERE account=1')
assert db.execute('SELECT COUNT(*) FROM preview_edges').fetchone()[0]==0
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
print('PASS: 60 SQLite migration, preview isolation, filters and notification checks')
