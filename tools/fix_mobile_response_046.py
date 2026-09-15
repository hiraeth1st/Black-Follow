from pathlib import Path

path=Path('app/src/main/java/com/blackapps/follow/MobileInstagramClient.java')
text=path.read_text(encoding='utf-8')
old='''            InputStream stream=code>=200&&code<300?cn.getInputStream():cn.getErrorStream();String text=read(stream,8*1024*1024);
            JSONObject json=parse(text);
            String message=json.optString("message","");String gate=ResponsePolicy.gate(code,message,!json.isNull("challenge"),!json.isNull("checkpoint_url"),!json.isNull("feedback_title"));
            if("BF_RATE".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste isteğini sınırladı. [BF_MOBILE_RATE]",false,true,retry);
            if("BF_CHALLENGE".equals(gate)||"BF_ACTION_BLOCK".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste doğrulaması istiyor. Instagram uygulamasındaki uyarıyı tamamla. [BF_MOBILE_CHALLENGE]",true,false);
            if(code==401||code==403||code==400||code==404)throw new Unsupported("Mobil yöntem kabul edilmedi (HTTP "+code+").");
'''
new='''            InputStream stream=code>=200&&code<300?cn.getInputStream():cn.getErrorStream();String text=read(stream,8*1024*1024);
            JSONObject json;
            try {json=parse(text);}
            catch(IOException malformed) {
                if(code==400||code==401||code==403||code==404)throw new Unsupported("Mobil yöntem kabul edilmedi (HTTP "+code+", JSON olmayan yanıt).");
                throw malformed;
            }
            String message=json.optString("message","");String gate=ResponsePolicy.gate(code,message,!json.isNull("challenge"),!json.isNull("checkpoint_url"),!json.isNull("feedback_title"));
            if("BF_RATE".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste isteğini sınırladı. [BF_MOBILE_RATE]",false,true,retry);
            if("BF_CHALLENGE".equals(gate)||"BF_ACTION_BLOCK".equals(gate))throw new InstagramClient.AccessError("Instagram mobil liste doğrulaması istiyor. Instagram uygulamasındaki uyarıyı tamamla. [BF_MOBILE_CHALLENGE]",true,false);
            if("BF_SIGN_IN".equals(gate)||"BF_FORBIDDEN".equals(gate)||"BF_REDIRECT".equals(gate))throw new InstagramClient.AccessError("Instagram mobil oturumu yeniden doğrulanmalı. [BF_MOBILE_SIGN_IN]",true,false);
            if(code==401||code==403||code==400||code==404)throw new Unsupported("Mobil yöntem kabul edilmedi (HTTP "+code+").");
'''
if text.count(old)!=1:raise SystemExit(f'mobile response block: {text.count(old)}')
path.write_text(text.replace(old,new,1),encoding='utf-8')
