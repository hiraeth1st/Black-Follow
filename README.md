# Black Follow — Android 0.3.3

Instagram takipçi / takip listelerinin erişilebildiği durumlarda yerel geçmişini tutan, bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.

## 0.3.3: eksik listede ikinci sayfalama yöntemi

İlk liste yöntemi 178/200 takipçi veya 787/794 takip gibi eksik sonuç verdiğinde yalnızca eksik kalan tür için bağımsız GraphQL sayfalaması başlar. Önce iki ilk liste okunur; sonra gerekirse takipçi ve takip edilenler ayrı ayrı yeniden alınır. Her yöntem kendi imlecini kullanır. İki eksik tarama birleştirilmez; bir taramanın benzersiz kimlik sayısı profil toplamına ulaşmalıdır. Sonunda profil toplamları tekrar kontrol edilir.

- Normal yöntem tam sonuç döndürürse ek istek yapılmaz. İkinci yöntemde ilerleme ayrı etiketlenir.
- `has_next_page` ve `end_cursor` birlikte değerlendirilir; son sayfada imleç bulunması tek başına yeni istek başlatmaz. Sayfa tekrarları kimlikle ayıklanır; imleç döngüsü veya değişen toplam taramayı durdurur.
- HTTP 429, gerçek Retry-After, oturum veya güvenlik doğrulaması yanıtlarında işlem durur. Erişim reddini aşmak için başka yöntem denenmez.
- İki yöntem de eksik kalırsa en geniş tek tarama önizlemesi saklanır. Eksik kişiler takipten çıkmış kabul edilmez. Önceki tam listeler ve olaylar korunur.

İkinci yöntem tanımları [Instaloader kaynak kodu](https://github.com/instaloader/instaloader/blob/master/instaloader/structures.py) ve [instagrapi kaynak kodundaki](https://github.com/subzeroid/instagrapi/blob/master/instagrapi/mixins/user.py) GraphQL bağlantı sayfalamasına dayanır. Bu kaynaklar Instagram'ın resmi API garantisi değildir; sorguların her hesapta çalışacağı veya tüm kişilerin döneceği garanti edilmez. Mobil API parametreleri tahminen web isteğine eklenmedi.

Sürüm **0.3.3-test**, `versionCode=12`, veritabanı şeması **4**. Giriş ve kayıt biçimi değişmedi. Sentetik HTTPS yanıtlarıyla 178/200 ve 787/794 ilk sonuçlarından sonra bağımsız 200/200 ve 794/794 sonuçlarının doğrulanması test edilir. Kullanıcının canlı hesabında veya Android cihazında bu sonuç henüz doğrulanmadı; eksik kişilerin asıl nedeni belirlenmiş değildir. Test ayrıntıları `TEST-RESULTS.md` dosyasında.

## 0.3.2: eksik liste önizlemesi

Cihaz görüntüsünde 200 toplam takipçiye karşılık 9 sayfada 182 benzersiz kişi alındı ve sayfalama sona erdi. Önceki sürüm, toplam eşleşmediğinden alınan kişileri göstermiyor ve takip edilenler listesine geçmiyordu. Eksik 18 kişinin nedeni veya kimliği bu veriden belirlenemez.

- Sunucunun devam sayfası vermediği bir listede alınan geçerli kişiler ayrı bir **tarama önizlemesine** kaydedilir. Örneğin 182/200 takipçi, açıkça “Eksik liste” olarak görünür. Profil toplamı 182'ye indirilmez.
- Takipçi sayısı eksik kalsa da takip edilenler listesi normal akışta taranır. Her liste ayrı sayı ve alınma zamanı gösterir. HTTP 429, giriş engeli, bozuk kişi kimliği veya imleç döngüsü alınırsa işlem durur; başka yoldan erişim denenmez.
- Önizlemelerde isim arama, profil fotoğrafı ve profile dokunarak gitme kullanılabilir. Eski tam liste varsa “Son doğrulanmış listeyi göster” ile görüntülenir. Liste önizlemesi ve doğrulanmış geçmiş birleştirilmez.
- TXT raporu önizlemeleri ayrı başlık altında, eksik/toplam sayısı ve alınma zamanı ile içerir. Önizlemedeki kişi için takip zamanı bilinmiyor denir; yeni takip olayı üretilmez.
- Her iki liste ve son profil sayımı doğrulanmadan geçmişe takip/çıkış kaydı veya bildirim yazılmaz. Böylece eksik 18 kişi yanlışlıkla takipten çıktı sayılmaz. Başarılı tam kayıt önizlemeleri aynı veritabanı işlemi içinde temizler.

Sürüm **0.3.2-test**, `versionCode=11`, veritabanı şeması **4**. Geçiş yalnızca ayrı önizleme tablolarını ekler; önceki hesaplar, tam listeler ve olaylar korunur. 302 otomatik kontrol başarılı; 9 sayfada 182/200 takipçi ve ardından 794/794 takip edilen kişi sentetik HTTPS yanıtlarıyla test edildi. Bu sürüm eksik kişilerin elde edildiği anlamına gelmez. Canlı hesap ve Android cihaz testi burada yapılmadı.

## 0.3.1: çok sayfalı liste düzeltmesi

Kullanıcının 200 takipçi / 794 takip bulunan hesapta paylaştığı hata, eski doğrulayıcıdaki “tekrar eden veya geçersiz kayıt” kontrolünden geliyordu. Aynı kişi bir sayfada veya iki farklı sayfada tekrar görünürse eski kod bütün taramayı durduruyordu.

- Tekrarlanan satırlar sabit kişi kimliğiyle birleştirilir. Listenin tamamlanması için **benzersiz** kişi sayısı profil toplamıyla eşleşmelidir; tekrarlar eksik kişileri tamamlanmış gibi göstermez.
- Geçersiz/eksik kişi kimliği ayrı `BF_LIST_ID` hatasıyla bildirilir. `pk` alanı null olduğunda mevcut geçerli `id` alanı kullanılabilir. Kişi kimliği üretilmez veya tahmin edilmez.
- Döngü yapan imleç, boş ara sayfa, eksik toplam ve son profil sayımı denetimleri korunur. Değişen imleçlerle üç ara sayfa boyunca yeni kişi gelmezse işlem `BF_LIST_STALLED` ile durur.
- Tarama sırasında hangi listenin kaç benzersiz kişisinin alındığı ve sayfa numarası görünür. Doğrulama hataları liste türünü ve sayfa numarasını içerir.
- Erişim kısıtlaması sonrasında ek istek/retry yapılmaz. Liste başına 10.000 kişi, 200 sayfa ve çalışma başına süre sınırları devam eder; bu sürüm sınırsız liste erişimi sağlamaz.

Sürüm **0.3.1-test**, `versionCode=10`, veritabanı şeması 3. 279 otomatik kontrol kapsamında gerçek Java HTTP istemcisi sentetik, çakışan sayfalarla 200/794 kişilik tam tarama gerçekleştirdi. Bu, kullanıcının canlı Instagram hesabında veya Android cihazında yapılmış bir test değildir. Yerel Android derlemesi başarılıdır.

## 0.3.0: Profilim ve takipçi çıkışları

Alt gezinme çubuğunda solda büyüteç simgeli **Ara**, sağda boş profil simgeli **Profilim** bulunur. Ara sekmesi mevcut hesap arama/geçmiş ekranını açar. Profilim sekmesi giriş yapılan Instagram hesabını gösterir; kullanıcı adı elle yazılmaz.

- Profilim ilk açıldığında hesap sabit oturum kimliğiyle kaydedilir ve henüz hiç denenmemişse tam liste kontrolü başlar. Önceden takip edilen aynı hesap varsa o kayıt ve geçmiş kullanılır; kullanıcı adı değişmesi ikinci kayıt oluşturmaz. İlk girişte veya kontrol sürerken gerekirse “Profilimi aç” düğmesi görünür.
- Takipçi/takip sayıları, kişi listeleri, hareketler, elle yenileme ve TXT dışa aktarma kendi hesabında da çalışır. Yeni kayıt otomatik izlemeye açıktır; daha önce durdurulmuş bir kaydın tercihi korunur.
- **Beni takipten çıkanlar** yalnızca kendi hesabının takipçi listesindeki çıkış olaylarını gösterir. Senin takip etmeyi bıraktığın kişiler bu filtreye karışmaz. İlk tam tarama başlangıçtır; daha önce kimlerin çıktığı geriye dönük çıkarılamaz.
- Başarılı tam taramada kendi takipçi listenden çıkan kişiler isimleriyle bildirilir. Bildirime dokunmak Profilim içindeki çıkış filtresini açar. İlk tarama, eksik tarama ve sadece sayı değişikliği çıkış bildirimi üretmez. Başka hesaplar için önceki yeni kişi bildirimleri devam eder.
- Çıkış; takipten çıkma, engelleme, hesap kapanması veya görünürlük değişikliği gibi nedenlerle olabilir. Tespit saati kesin olay saati değildir. Bildirimler tarama sonucuna bağlıdır: varsayılan otomatik kontrol 6 saattir, Android bildirim izni gerekir. Instagram'dan anlık bildirim bağlantısı yoktur.
- Ara ekranındaki hesap, liste sayfası ve arama filtresi Profilim'e gidip dönünce geri yüklenir. Oturum değişince görünüm sıfırlanır; kullanıcı kayıtları birbirine aktarılmaz.

Sürüm **0.3.0-test**, `versionCode=9`, veritabanı şeması 3. 263 otomatik kontrol ve yerel Android derlemesi başarılı. Giriş doğrulaması ve HTTP istek protokolü korunur. Yeni Android arayüzü ve bildirim teslimi burada cihazda çalıştırılmadı.

## 0.2.4: günlük kullanım ve rapor iyileştirmeleri

Kullanıcı 0.2.3 sürümünde girişin ve veri çekiminin sorunsuz çalıştığını bildirdi. Bu incelemede giriş ve Instagram istek protokolü değiştirilmedi.

- **TXT dışa aktarma:** Günlük sayımlar ve tüm hareketlere ek olarak son tam taramadaki takipçi ve takip edilen listeleri de yazılır. Her kişide kullanıcı adı, varsa ad, profil bağlantısı, biliniyorsa tespit aralığı bulunur. İlk kayıttaki kişiler “Zaman bilinmiyor” olarak kalır. Liste zamanı profil sayımından ayrı gösterilir. Rapor ekran filtresinden bağımsızdır ve 100 kişi sınırı yoktur.
- **Hareketlerde arama:** Kullanıcı adı veya isimle geçmiş aranabilir. Tüm hareketler, yeni takipçiler, yeni takipler ve listeden çıkanlar filtreleri eklenmiştir. Tekrar takip sayıları filtrelenmiş satır sayısına göre değil, kişinin tüm kayıtlı geçmişine göre hesaplanır.
- **Ekran güncelliği:** Arka plan kontrolü bitince açık ekran kayıtları yeniler ve kaydırma konumunu korur. Yazı alanında çalışırken ekran yeniden kurulmaz; yeni kayıt bildirimi üstte görünür ve dokunarak açılabilir. Bu yalnızca yerel ekran yenilemesidir, Instagram'a yeni istek göndermez.
- **Sayfalama:** Tam 100 kayıtta fazladan boş sayfa açılması düzeltildi; sonraki sayfa düğmesi yalnızca devam eden kayıt varsa görünür.
- **Menü:** Küçük ekranlarda veya büyük yazı ayarında tüm seçeneklere kaydırarak erişilebilir.

Sürüm **0.2.4-test**, `versionCode=8`, veritabanı şeması 3. 243 otomatik kontrol başarılı. Önceki sürümle aynı imzayı kullanır; uygulamayı silmeden güncellenir. Yeni sürümün Android cihazında görsel testi ve canlı liste testi bu ortamda yapılmadı. Aşağıdaki 0.2.3 notları önceki değişiklikleri açıklar.

## 0.2.3: giriş ile veri sorgusunun ayrılması

Önceki sürüm, profil sorgusundan kalan yerel HTTP 429 beklemesini giriş doğrulamasına da uyguluyordu. Instagram sayfasında giriş tamamlanmış olsa bile uygulamaya dönülemiyordu.

- Uygulamanın 15 dakika ile başlayıp saatlere uzayan beklemesi ve giriş düğmesinin 30 saniyelik sınırı kaldırıldı. Güncelleme eski yerel beklemeyi otomatik temizler. Geçmiş ve hesap listeleri korunur.
- Instagram açıkça `Retry-After` gönderirse yalnızca veri istekleri o süreye uyar. Süre göndermezse uygulama tarih uydurmaz: tek deneme hata ile biter, kullanıcı elle yenileyebilir. Arka plan istekleri başarılı bir manuel veri kontrolüne kadar durur; otomatik tekrar döngüsü oluşmaz. Arka plan hatalarının kontrol aralığı ayrıca katlanmaz.
- Giriş düğmesi artık ek GraphQL isteği göndermez. Instagram'ın HTTPS sayfasındaki görünür gezinme alanından oturum sahibinin profil düğmesini okur ve cihazdaki oturum kimliğinin işlem sırasında değişmediğini denetler. Ziyaret edilen profil, öneriler veya takipçi penceresindeki kişiler oturum sahibi sayılmaz. Şifre alanının değeri, sayfa içi gizli veriler veya sayfa kaynak kodu okunmaz.
- Giriş/doğrulama sayfasında, eksik oturumda, belirsiz görünümde veya sayfa/hesap değiştiğinde işlem kabul edilmez. Instagram arayüzü tanınamazsa `BF_LOGIN_PAGE` gösterilir; ana sayfa açılıp tekrar kontrol edilebilir. Bu DOM yaklaşımı Instagram'ın arayüz değişikliklerinden etkilenebilir.
- Aynı doğrulanmış oturum için her 10 dakikada bir ek oturum isteği gönderilmez. Her veri isteğinde oturum kimliği denetlenir; Instagram yeniden giriş veya güvenlik doğrulaması istediğinde veri çekme durur. Giriş ekranı açıkken arka plan kontrolü aynı oturuma müdahale etmez.
- Ekran görüntüsü alma, yeni kişi bildirimleri, kullanıcı adı/fotoğraf listeleri ve TXT dışa aktarma önceki sürümden devam eder. İlk tam liste başlangıç kaydıdır ve bildirim üretmez; sonraki başarılı tam listelerde tespit edilen yeni kişiler bildirilir.

Sürüm **0.2.3-test**, `versionCode=7`, veritabanı şeması 3. Aynı imzayla mevcut uygulamanın üzerine kurulur; kaldırıp yüklemek gerekmez. Her kullanıcı kendi Instagram oturumunu ve telefonundaki kayıtlarını kullanır. Test hesabı veya oturum verisi APK'ya eklenmez.

### Doğrulanan ve açık kalan noktalar

221 otomatik kontrol ve Android kaynak/Java/D8 derlemesi çalıştırılır. Yeni giriş kontrolünde APK'ya paketlenen **aynı JavaScript dosyası**, izin verilen canlı test hesabının açık Instagram sayfasında çalıştırıldı ve oturum sahibini doğru buldu. Tarayıcıda izin verilen hedefin 26 takipçisi ve 27 takip edilen hesabı önceki canlı kontrolde tamamen görüntülenmişti.

**Bu sonuç, Android APK'nın takipçi API'siyle canlı liste çektiğinin doğrulaması değildir.** Bu ortamda Android cihazı/emülatörü bulunmadığı için native WebView ile uçtan uca giriş, canlı liste çekimi, bildirim teslimi ve uzun süreli arka plan çalışması denenemedi. Tarayıcıdaki doğrudan API denemesi ortamın `ERR_BLOCKED_BY_CLIENT` hatasıyla engellendi; bu Instagram'ın HTTP 429 yanıtı değildir. Tarayıcı oturumu dışarı aktarılmadı. Ayrıntılar `TEST-RESULTS.md` dosyasında.

Profil akışı 0.2.2'deki gibi tam kullanıcı adı eşleşmesiyle hesap kimliği çözümleme ve kimlikle profil GraphQL isteği kullanır. Kayıtlı kimlik için tekrar arama yapılmaz. Takipçi/takip edilen listeleri Instagram'ın erişimine bağlıdır; giriş doğrulamasının başarılı olması veri erişimini garanti etmez. Eksik liste geçmişe kaydedilmez. Her ret sonrası işlem durur, alternatif uç nokta zinciri denenmez.

## Telefonda ilk kullanım

1. `Black-Follow-0.3.3-test.apk` dosyasını Android 8.0 veya üzeri telefona kur.
2. **Instagram'a giriş yap** düğmesine bas. Görünen sayfa `https://www.instagram.com` alan adındadır. Instagram kullanıcı adı/parola girişini ve varsa iki aşamalı doğrulamayı bu sayfada tamamla. Facebook üzerinden giriş desteklenmez.
3. **Giriş yaptım • oturumu doğrula** düğmesine bas. Başarılı doğrulama sonrası ana ekran açılır.
4. İlk denemeyi erişebildiğin, az takipçili bir hesapta yap. Kullanıcı adını yazıp **Hesap ekle • profil bilgilerini getir** düğmesine bas.
5. Profil erişimi başarılıysa sayılar ve alınma zamanı görünür. **Listeyi şimdi yenile** ile iki liste de tamamen alınırsa kişiler kaydedilir. İlk kayıt kişileri **Zaman bilinmiyor** olarak işaretlenir.
6. Sonraki başarılı taramalar eklenen ve listeden çıkan kişileri **Hareketler** sekmesine kaydeder.
7. **☰ → Hesap geçmişi** ile kayıtlı hesaplara dön. Hesaplar otomatik izlenir. Hesap ekranında izlemeyi durdurabilir veya menüden o hesabın tüm verilerini silebilirsin.

Gizli hesapta giriş yaptığın hesabın gerekli erişimi bulunmalıdır. Uygulama takip isteği göndermez, gizliliği aşmaz, güvenlik doğrulamasını otomatik çözmez. Instagram oturumu veya liste testi başarısız olursa hata metnini paylaş; parola, oturum çerezi veya doğrulama kodu paylaşma.

## Otomatik kontroller ve zaman

- Varsayılan aralık **6 saat**; menüde **12 / 24 saat** veya kapalı seçilebilir.
- Android JobScheduler kullanılır; internet gerekir. Uygulamanın ekranda açık olması gerekmez, ancak Android pil kısıtları ve üreticinin güç yönetimi kontrolleri geciktirebilir. Zorla durdurulan uygulama yeniden açılana kadar çalışmaz. Telefon kapalıyken kontrol yapılmaz. Ayrı bir sunucu yoktur.
- Her kayıtlı, izlemeye açık hesap sırayla değerlendirilir. Bir çalışma en fazla yaklaşık 7 dakika sürer. Sığmayan hesaplar sonraki çalışmada öncelik alır.
- Manuel kontrolde uygulamanın 5 dakika bekleme şartı yoktur; aynı anda tek kontrol yapılabilir. Arka plan hatalarında seçili kontrol aralığı kullanılır. HTTP 429 yanıtında yalnızca sunucunun `Retry-After` süresi uygulanır; süre yoksa yerel sayaç eklenmez. 429 sonrasında otomatik veri istekleri başarılı bir manuel yenilemeye kadar durur. Giriş/izin doğrulaması istenirse kullanıcı yeniden doğrulayana kadar durur.
- İlk kayıtta geçmiş takip saati üretilmez. Sonraki kayıtlarda **tespit zamanı** ve önceki tarama başlangıcı–yeni tarama bitişi aralığı gösterilir. Saat dilimi cihazın saat dilimidir.
- Saatler gerçek takip olayının kesin saati değildir. İki tarama arasındaki takip-et/çıkar hareketleri kaçırılabilir. Cihaz saatinin doğru olması gerekir.
- Listeden çıkma; takipten çıkma, engelleme, hesabın kapanması veya platform görünürlüğü gibi farklı nedenlere bağlı olabilir.

## Veri bütünlüğü

- Kişiler sabit Instagram kimliğiyle karşılaştırılır; kullanıcı adı değişikliği yeni takip sayılmaz.
- Takipçi ve takip edilen kişi listeleri **tek SQLite işlemi** ile kaydedilir. İki listeden biri başarısızsa ikisi için de önceki geçmiş korunur. Profil toplamları bu işlemden ayrı kaydedilir; liste hatası bu toplamları geri almaz.
- Sayfalar, sayılar, tekrarlanan kimlikler ve döngü yapan sayfa imleçleri denetlenir. Profil sayıları tarama öncesi ve sonrası karşılaştırılır.
- Bu kontroller Instagram'ın anlık/atomik bir liste garantisi verdiği anlamına gelmez. Eşzamanlı, toplam sayıyı değiştirmeyen hareketler belirsizlik yaratabilir.
- Bu test sürümünde liste başına en fazla **10.000 kişi / 200 sayfa** işlenir. Sınır aşılırsa eksik liste kaydedilmez.
- Kayıtlar giriş yapılan Instagram hesabının kimliğine göre ayrılır. Başka bir Instagram hesabıyla giriş yapmak önceki oturumun kayıtlarını o hesaba aktarmaz.

## Yerel veriler ve oturum

Uygulamanın sunucusu, analitiği veya reklam SDK'sı yoktur. Kullanıcının seçtiği hedefe TXT dışa aktarma yapılabilir. Profil fotoğrafları yalnızca HTTPS cdninstagram.com / fbcdn.net alanlarından, Instagram oturum çerezi eklenmeden alınır; fotoğraf önbelleği bellekte ve oturum sahibine göre tutulur. API istekleri sabit HTTPS Instagram adresine gider; yönlendirmeler otomatik izlenmez. Instagram web sayfası kendi gerekli kaynaklarını yükler. Parola uygulamanın ayrı formuna girilmez; uygulama kodu parola alanını okumaz. WebView oturum çerezleri yalnızca cihazdaki uygulama alanında tutulur; ağ istemcisi bunları aynı Instagram alan adına gönderir. Oturum çerezi de giriş yetkisi verdiği için hassas veridir.

Geçmiş SQLite ile uygulamanın özel alanında saklanır. Uygulama düzeyinde ayrıca veritabanı şifrelemesi uygulanmamıştır; Android'in uygulama izolasyonu ve cihazın disk koruması kullanılır. Bulut yedekleme kapalıdır. Ekran görüntüsü giriş ve ana ekranda açıktır. Çıkış çerezleri temizler, geçmişi korur. Uygulamayı kaldırmak veya uygulama verilerini temizlemek geçmişi siler.

## Derleme

Gerekenler: JDK 17+, Android SDK platform 35, build-tools 35.0.0, `zip`. Android Studio için Gradle projesi de sağlanır. Yerel teslim derlemesi üçüncü taraf Android kütüphanesi gerektirmez:

```bash
bash test.sh
export ANDROID_SDK_ROOT=/absolute/path/to/android-sdk
export BF_KEYSTORE=/absolute/path/to/black-follow.jks
export BF_KEY_ALIAS=blackfollow
# BF_KEY_PASSWORD değerini güvenli ortam değişkeni olarak ayarla.
bash build-apk.sh
```

İmzalı çıktı: `out/Black-Follow-0.3.3-test.apk`. İmza değişkenleri yoksa yalnızca kurulamaz durumdaki `out/aligned.apk` üretilir. İmzalama yedeği kaynak kod arşivine dahil değildir; ayrı özel teslim dosyasıdır. Güncellemeleri silmeden kurabilmek için aynı anahtar ve daha yüksek `versionCode` kullanılmalıdır.

Oturum regresyon testlerini de çalıştırmak için `BF_JSON_JAR` değişkenini `org.json:json:20240303` JAR dosyasına ayarla ve `bash test.sh` çalıştır. Beklenen SHA-256: `3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed`. Bu yalnızca masaüstü test bağımlılığıdır; APK'ya eklenmez. GitHub iş akışı bu testleri de çalıştırır.

GitHub Actions iş akışı `.github/workflows/android.yml` içindedir. İmzalı APK için repo secrets: `BF_KEYSTORE_BASE64` (JKS dosyasının base64 içeriği) ve `BF_KEY_PASSWORD`. Anahtar veya parolayı repoya commit etme. GitHub kurulumu ve mevcut bağlantının durumu için GITHUB-SETUP.md dosyasına bak. İmza secrets eksikse otomatik derleme açık hata ile durur; başarılı çıktıda yalnızca imzalı APK ve SHA-256 listesi bulunur.

## Teknik kaynaklar

- Android JobScheduler: https://developer.android.com/reference/android/app/job/JobScheduler
- Android WebView ayarları: https://developer.android.com/reference/android/webkit/WebSettings
- Resmî Instagram API kapsamı: https://www.postman.com/meta/instagram/collection/6yqw8pt/instagram-api
- Instaloader bağımsız proje dokümantasyonu (oturum ve liste okuma yaklaşımı): https://instaloader.github.io/as-module.html
- Web oturum sorgusunun birincil kod referansı (`test_login`): https://github.com/instaloader/instaloader/blob/master/instaloader/instaloadercontext.py

Kaynak kod bu proje için yazılmıştır; Instaloader/instagrapi kütüphanesi veya kodu pakete dahil değildir.

- Retry-After tanımı: https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after

- Android bildirim izni: https://developer.android.com/develop/ui/compose/notifications/notification-permission
