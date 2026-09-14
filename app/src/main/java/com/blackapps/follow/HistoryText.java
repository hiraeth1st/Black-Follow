package com.blackapps.follow;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Shared wording for the screen and the complete export, based on stable person IDs. */
public final class HistoryText {
    public static String event(String kind,String action,String username,int additions,long removed,TimeZone zone) {
        boolean following="following".equals(kind),added="added".equals(action);
        String text="@"+username+" "+(following?(added?"takip edildi":"takip edilenlerden çıktı"):(added?"takipçi olarak eklendi":"takipçi listesinden çıktı"));
        List<String> notes=new ArrayList<>();
        if(added && removed>0) notes.add(format(removed,"d MMMM yyyy HH:mm",zone)+" tarihinde "+(following?"takip edilenlerden":"takipçi listesinden")+" çıkmıştı");
        if(added && additions>1) notes.add("izleme süresinde "+additions+". "+(following?"takip":"takipçi olarak eklenme"));
        return text+(notes.isEmpty()?"":" ("+String.join("; ",notes)+")");
    }
    public static String format(long time,String pattern,TimeZone zone) {SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.forLanguageTag("tr-TR"));f.setTimeZone(zone);return f.format(new Date(time));}
    public static final class Report {
        private final Writer out;private final TimeZone zone;private String day="",countDay="";private long followers=-1,following=-1;private int events=0,observations=0;
        public Report(Writer out,String username,long generated,long profileAt,long listAt,TimeZone zone) throws IOException {
            this.out=out;this.zone=zone;
            out.write("BLACK FOLLOW — @"+username+"\nOluşturulma: "+format(generated,"dd.MM.yyyy HH:mm:ss",zone)+"\nSaat dilimi: "+zone.getID()+"\n");
            out.write("Son profil sayımı: "+(profileAt==0?"Henüz alınmadı":format(profileAt,"dd.MM.yyyy HH:mm:ss",zone))+"\nSon tam kişi listesi: "+(listAt==0?"Henüz alınmadı":format(listAt,"dd.MM.yyyy HH:mm:ss",zone))+"\n\n");
            out.write("Tarihler değişikliğin TESPİT zamanıdır; kesin takip saati değildir. İlk listedeki kişilerin takip zamanı bilinmiyor. Tekrar sayıları yalnızca izleme başladıktan sonra tespit edilen eklenmeleri kapsar. Listeden çıkma, takipten çıkma/engelleme/hesabın kapanması gibi farklı nedenlerle olabilir. Kontrol yapılmayan günler ve iki kontrol arasında kaybolan hareketler bu raporda yer almaz.\n");
        }
        private void heading(long time)throws IOException {String d=format(time,"d MMMM yyyy",zone);if(!d.equals(day)){out.write("\n"+d+"\n");day=d;}}
        public void counts(long time,int followers,int following,String source)throws IOException {
            String d=format(time,"d MMMM yyyy",zone);if(d.equals(countDay)&&this.followers==followers&&this.following==following)return;
            heading(time);out.write(format(time,"HH:mm:ss",zone)+" • "+followers+" takipçi / "+following+" takip"+("migrated".equals(source)?" (önceki sürümden kalan son sayım)":"")+"\n");
            countDay=d;this.followers=followers;this.following=following;observations++;
        }
        public void event(long time,String kind,String action,String username,int count,long removed,long lower)throws IOException {
            heading(time);out.write(format(time,"HH:mm:ss",zone)+" • "+HistoryText.event(kind,action,username,count,removed,zone)+"\n");
            if(lower>0)out.write("  Tespit aralığı: "+format(lower,"dd.MM.yyyy HH:mm:ss",zone)+" – "+format(time,"dd.MM.yyyy HH:mm:ss",zone)+"\n");events++;
        }
        public void currentLists(long time)throws IOException {
            out.write("\nMEVCUT KİŞİ LİSTELERİ\n");
            out.write(time==0?"Henüz tam liste alınmadı; bu durum sıfır kişi anlamına gelmez.\n":"Son tam tarama: "+format(time,"dd.MM.yyyy HH:mm:ss",zone)+"\nAşağıdaki kişiler bu taramada mevcut olanlardır.\n");
        }
        public void listHeading(String kind)throws IOException {out.write("\n"+("followers".equals(kind)?"TAKİPÇİLER":"TAKİP EDİLENLER")+"\n");}
        public void person(String username,String name,long since,long lower)throws IOException {
            out.write("@"+username+(name==null||name.isEmpty()?"":" • "+name.replace('\n',' ').replace('\r',' '))+"\n");
            out.write(since==0?"  Zaman bilinmiyor · ilk kayıtta mevcut\n":"  Tespit: "+format(since,"dd.MM.yyyy HH:mm:ss",zone)+"\n");
            if(since>0&&lower>0)out.write("  Tespit aralığı: "+format(lower,"dd.MM.yyyy HH:mm:ss",zone)+" – "+format(since,"dd.MM.yyyy HH:mm:ss",zone)+"\n");
            out.write("  "+ProfileLinks.profile(username)+"\n");
        }
        public void listCount(int count)throws IOException {out.write("Toplam: "+count+" kişi\n");}
        public void finish()throws IOException {if(observations==0)out.write("\nTarihçeli profil sayımı henüz yok.\n");if(events==0)out.write("\nHenüz tespit edilmiş takip değişikliği yok.\n");out.write("\nRapor sonu • "+events+" hareket\n");out.flush();}
    }
}
