from pathlib import Path
import re

login=Path('app/src/main/java/com/blackapps/follow/LoginActivity.java')
value=login.read_text(encoding='utf-8')
old='''            if(!owner.equals(Session.owner(this))) ChangeNotifications.clear(this);
            Session.prefs(this).edit().putString("owner",owner).putString("viewer_name",name).putBoolean("paused",false)
                .putLong("verified_at",System.currentTimeMillis()).remove("pause_reason").apply();
'''
new='''            boolean changed=!owner.equals(Session.owner(this));if(changed) ChangeNotifications.clear(this);
            android.content.SharedPreferences.Editor edit=Session.prefs(this).edit().putString("owner",owner).putString("viewer_name",name).putBoolean("paused",false)
                .putLong("verified_at",System.currentTimeMillis()).remove("pause_reason");
            if(changed)edit.remove("www_claim");edit.apply();
'''
if value.count(old)!=1:raise SystemExit(f'login claim reset marker: {value.count(old)}')
login.write_text(value.replace(old,new,1),encoding='utf-8')

main=Path('app/src/main/java/com/blackapps/follow/MainActivity.java')
value=main.read_text(encoding='utf-8')
old='Session.prefs(this).edit().remove("owner").remove("viewer_name").apply();'
new='Session.prefs(this).edit().remove("owner").remove("viewer_name").remove("www_claim").apply();'
if value.count(old)!=1:raise SystemExit(f'logout claim reset marker: {value.count(old)}')
main.write_text(value.replace(old,new,1),encoding='utf-8')

script=Path('tools/apply_client_transport_v2.py')
value=script.read_text(encoding='utf-8')
value,count=re.subn(r'\n# Clear server claim whenever.*?main\.write_text\(value,encoding=\x27utf-8\x27\)\n', '\n', value, count=1, flags=re.S)
if count!=1:raise SystemExit(f'claim block removal: {count}')
script.write_text(value,encoding='utf-8')
