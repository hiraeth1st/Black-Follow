from pathlib import Path

path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
text=path.read_text(encoding='utf-8')
old='            if(part.unsupported){result.unsupported++;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);continue;}\n'
new='            if(part.unsupported){result.unsupported++;traceSearchUnsupported(kind,"hedefli",part.failureCode,found.size(),expected);break;}\n'
if text.count(old)!=1:raise SystemExit(f'expected one target failure branch, found {text.count(old)}')
path.write_text(text.replace(old,new,1),encoding='utf-8')
