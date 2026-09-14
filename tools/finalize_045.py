from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count=text.count(old)
    if count!=1:raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old,new,1)

path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
text=path.read_text(encoding='utf-8')
text=replace_once(text,
    '    private static final class TargetResult {int requests,recovered;boolean unsupported;}\n',
    '    private static final class TargetResult {int requests,recovered,unsupported;}\n',
    'target result shape')
text=replace_once(text,
    '            if(part.unsupported){result.unsupported=true;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);break;}\n',
    '            if(part.unsupported){result.unsupported++;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);continue;}\n',
    'target optional failure')
text=replace_once(text,
    '        int requests=0,rows=0,added=0;boolean searchAvailable=true;\n',
    '        int requests=0,rows=0,added=0;boolean searchAvailable=true,searchProbed=false;\n',
    'search state')
text=replace_once(text,
    '            requests+=target.requests;searchAvailable=!target.unsupported;\n',
    '            requests+=target.requests;searchAvailable=true;\n',
    'target does not disable canonical probe')
old_root='''                    if(part.unsupported) {
                        traceSearchUnsupported(kind,PrefixSearchLogic.isTurkishDisplayQuery(prefix)?"Türkçe":"ASCII",part.failureCode,found.size(),expected);
                        if(PrefixSearchLogic.isTurkishDisplayQuery(prefix))continue;
                        searchAvailable=false;break;
                    }
                    if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
'''
new_root='''                    if(part.unsupported) {
                        boolean turkish=PrefixSearchLogic.isTurkishDisplayQuery(prefix);
                        traceSearchUnsupported(kind,turkish?"Türkçe":"ASCII",part.failureCode,found.size(),expected);
                        // The first ASCII root (a) is the capability probe. Later failures can be query-specific.
                        if(!turkish&&!searchProbed){searchAvailable=false;break;}
                        continue;
                    }
                    if(!PrefixSearchLogic.isTurkishDisplayQuery(prefix))searchProbed=true;
                    if(found.size()>expected)throw new IOException("Liste kontrol sırasında değişti veya arama beklenmeyen kişi döndürdü; geçmiş korunuyor. [BF_LIST_CHANGED]");
'''
text=replace_once(text,old_root,new_root,'root optional failure behavior')
text=replace_once(text,
    '                if(part.unsupported){traceSearchUnsupported(kind,"alt-önek",part.failureCode,found.size(),expected);searchAvailable=false;break;}\n',
    '                if(part.unsupported){traceSearchUnsupported(kind,"alt-önek",part.failureCode,found.size(),expected);continue;}\n',
    'child optional failure behavior')
text=text.replace('Normal liste ve önek aramasıyla tam sonuç doğrulanamadı: ','Normal liste, REST kurtarma ve liste aramalarıyla tam sonuç doğrulanamadı: ')
path.write_text(text,encoding='utf-8')

results=Path('TEST-RESULTS.md')
value=results.read_text(encoding='utf-8')
extra='''\n- Tam kullanıcı adı veya özel karakter sorgularından biri HTTP 400 döndürürse yalnızca o sorgu atlanır. Güvenli ilk ASCII kök sorgusu başarısızsa arama yüzeyi kapatılır; çekirdek cursor sonuçları korunur.\n'''
if extra.strip() not in value:value+=extra
results.write_text(value,encoding='utf-8')
