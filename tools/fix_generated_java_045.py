from pathlib import Path

path=Path('app/src/main/java/com/blackapps/follow/InstagramClient.java')
text=path.read_text(encoding='utf-8')
bad='benzersiz kişi\n"+issue.getMessage()'
good='benzersiz kişi\\n"+issue.getMessage()'
if text.count(bad)!=1:
    raise SystemExit(f'generated Java newline marker: expected 1, found {text.count(bad)}')
path.write_text(text.replace(bad,good,1),encoding='utf-8')
