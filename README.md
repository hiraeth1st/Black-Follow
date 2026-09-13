# Black Follow — Android 0.2.1 erişim testi

Instagram takipçi / takip listelerinin erişilebildiği durumlarda yerel geçmişini tutan, bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.

## Bu sürümün durumu

### 0.2.1 ekran görüntüsü, bildirim ve erişim tanısı

- Ekran görüntüsü engeli ana ekrandan ve Instagram giriş ekranından kaldırıldı.
- **☰ → Bildirimleri aç**: Android bildirim izni/kanal ayarları açılır. İlk tam liste başlangıç kaydıdır ve bildirim üretmez. Sonraki başarılı tam taramalarda yeni takipçiler veya takip edilen kişiler bulunduğunda hesap başına bir bildirim gönderilir. Yalnızca çıkışlar veya sayım değişikliği bildirim üretmez. Bildirime dokununca ilgili hesabın Hareketler sekmesi açılır. Ekranda açıkken yapılan manuel taramalar da aynı şekilde bildirebilir.
- Bildirimler telefondaki taramanın sonucudur; Instagram'dan anlık push bağlantısı yoktur. Varsayılan arka plan kontrolü 6 saattir. İlk liste alınamıyorsa veya Instagram taramayı engelliyorsa yeni kişi bildirimi üretilemez. Android izni ve kanal açık olmalıdır; sistem bildirim ayarları/sessiz mod geçerlidir.
- Bildirim sorguları oturum sahibine göre ayrılır. Oturum değiştirildiğinde/çıkışta eski bildirimler temizlenir; bildirim bağlantısı da oturum sahibini kontrol eder. İlk ekran kilidinde genel bildirim metni kullanılır; ayrıntı görünürlüğünü telefon ayarları belirler.
- `feedback_required` / feedback uyarısı artık HTTP 429 gibi otomatik 15 dakika bekleme diye sunulmaz; işlem kısıtlaması olarak gösterilir ve otomatik kontroller durdurulur. Gerçek 429 ve “birkaç dakika bekle” yanıtlarında bekleme korunur. JSON içindeki null uyarı alanları engel sayılmaz. HTTP 403 yanıtının gövdesindeki doğrulama isteği de ayırt edilir.
- Yeni hatalar güvenli şekilde istek aşamasını (profil, takipçi, takip edilenler, oturum) ve HTTP durumunu içerir. **☰ → Son hata bilgisini kopyala** bu bilgiyi kopyalar. Parola, çerez, ham yanıt ve istek URL'si kayda alınmaz. Eski bekleme kayıtlarının istek ayrıntısı geriye dönük üretilemez.
- Instagram'ın yanıt çerezi başlıkları büyük/küçük harften bağımsız okunur.

Bu sürüm, kullanıcının hesabındaki canlı liste erişimi sorununu çözdüğü doğrulanmış bir sürüm değildir. Önceki `BF_RATE_WAIT` mesajı hangi isteğin hangi HTTP yanıtıyla engellendiğini içermiyordu. Uygulamayı silip yüklemek platformun erişim kısıtını çözdüğü anlamına gelmez ve yerel geçmişi siler. Bu APK mevcut sürümün üzerine kurulmalıdır. Paket sürüm kodu 5, veritabanı şeması 3; eski bekleme ve geçmiş korunur.

### 0.2.0 tarihçe, dışa aktarma ve kişi listeleri

Aynı APK farklı telefonlarda kullanılabilir. Her kişi kendi Instagram hesabıyla giriş yapar; APK içinde geliştiricinin veya başka kullanıcının hesabı, çerezi ya da geçmişi bulunmaz. Her kurulum kendi yerel verisini tutar. Aynı telefonda farklı oturumlara ait kayıtlar, liste sorguları ve dışa aktarma hesap sahibine göre ayrılır. Arkadaşına yalnızca APK dosyasını göndermen yeterlidir; özel imza yedeği dağıtım dosyası değildir.

- **Listeyi şimdi yenile**: Uygulamanın eski 5 dakikalık manuel beklemesi kaldırıldı. Başarılı tam taramada yeni eklenen ve listeden çıkan kullanıcılar isimleriyle sonuç penceresinde ve Hareketler sekmesinde görünür. Devam eden kontrol ve Instagram kaynaklı bekleme süreleri korunur. İzlemesi durdurulmuş bir hesap elle bir kez yenilenebilir; bu işlem otomatik izlemeyi açmaz.
- **Kişi listeleri**: Kullanıcı adı, varsa tam ad ve erişilebilen Instagram profil fotoğrafı gösterilir. Kişiye dokununca Instagram profil bağlantısı açılır. Fotoğraf alınamazsa yer tutucu görünür. Eski kayıtlara fotoğraf URL'leri sonraki başarılı taramada eklenir. Sayfa başına 100 kişi vardır; sonraki sayfalarla alınan tam liste gezilir.
- **Dışa aktar (.txt)**: Hesap ekranındaki veya menüdeki düğmeye basınca o hesabın tüm kayıtlı tarihçesi hazırlanır ve Android dosya kaydetme ekranı açılır. Gün başlıkları altında takipçi/takip sayımları, eklenmeler, çıkmalar, yeniden takiplerde önceki çıkış tarihi ve izleme sırasında tespit edilen tekrar sayısı bulunur. Ekranın 100 kayıt sınırı rapora uygulanmaz. Rapor oluşturulurken Instagram'a yeni istek gönderilmez.
- Sayım tarihçesi bu sürümden itibaren birikir. Önceki sürüm yalnızca son sayımı tuttuğu için geçişte mevcut son sayım aktarılır; geçmişte tutulmamış günlerin sayıları üretilmez. Eski hareketler korunur ve tekrar hesaplamasına katılır.

Sürüm kodu 4, veritabanı şeması 3'tür. Önceki APK'nın üzerine uygulamayı silmeden kurulabilir. Tarihler tespit zamanıdır; kesin takip saati veya izleme öncesindeki toplam takip sayısı değildir.

### 0.1.2 profil sayıları ve bekleme düzeltmesi

Hesap eklenince önce **yalnızca profil bilgileri** alınır. Takipçi / takip edilen sayıları ayrı işlemde, zaman damgasıyla saklanır. **Sayıları yenile** profil bilgilerini; **Listeyi şimdi yenile** kişi listelerini günceller. Kişi listesi isteği başarısız olsa bile önceden alınan sayılar ve tarihleri kalır. Profilin sayıları ile kişi listelerinin tarihleri ayrı gösterilir; hiç alınmayan veri sıfır diye gösterilmez. Otomatik kontroller kayıtlı aktif hesapları izlemeye devam eder.

Eski sürüm tüm sınır yanıtları için yerel olarak 24 saat bekliyordu; bu, sunucunun bildirdiği bir süre değildi. Artık sunucu `Retry-After` gönderirse bu süreye uyulur; süre yoksa ilk sınır için 15 dakika ve tekrarlayan sınırlarda ikiye katlanan (en fazla 24 saat) yerel bekleme kullanılır. Sunucunun daha uzun süresi kısaltılmaz. **Eski sürümde başlamış ve henüz bitmemiş bekleme güncellemeyle silinmez.** Ekranda kalan süre, tekrar deneme tarihi ve sürenin kaynağı görünür. Beklemenin bitmesi Instagram erişiminin açıldığı anlamına gelmez. Bekleme sırasında başka uç noktadan veri çekilmeye çalışılmaz.

Yeni girişin ardından 10 dakika içinde aynı oturum için gereksiz tekrar oturum sorgusu atılmaz; her veri isteğinde oturum kimliği kontrolü ve Instagram'ın erişim denetimi devam eder. Sürüm kodu 3, veritabanı şeması 2'dir. Şema güncellemesi mevcut listeleri, olayları ve sayıları korur. 0.1.0/0.1.1 üzerine aynı imzayla kurulabilir.

### 0.1.1 oturum düzeltmesi

Web oturumunu mobil API'nin `accounts/current_user` çağrısıyla doğrulama kaldırıldı. Oturum sahibini sorgulayan web GraphQL isteği kullanılır; dönen kimlik oturum kimliğiyle karşılaştırılır. Yanıtta kimlik yoksa yalnızca doğrulanmış oturum sorgusunun döndürdüğü kullanıcı adı profil kimliğine çözümlenir. Sadece çerez bulunması veya herkese açık profil okunması giriş başarısı sayılmaz. Instagram güvenlik/erişim engeli verirse farklı yollar deneyerek sürdürülmez.

Hatalar artık `BF_HTTP_400`, `BF_SIGN_IN`, `BF_CHALLENGE`, `BF_TIMEOUT`, `BF_VIEWER_SCHEMA` gibi sabit kodlarla ayrılır. Oturum hatası penceresindeki **Hata bilgisini kopyala** düğmesi yalnızca sürüm ve güvenli hata metnini kopyalar; çerez, parola, ham sunucu yanıtı veya hesap kimliği içermez. Tekrar doğrulama denemeleri arasında 30 saniye beklenir.

0.1.0 üzerine **uygulamayı silmeden** kurulur (aynı imza, `versionCode=2`). Kullanıcının bildirdiği hata cihazda yeniden üretilmediği için bu değişikliğin canlı hesapta sorunu çözdüğü henüz doğrulanmış değildir.

Bu bir **erişim testi sürümüdür**. APK derleme ve kayıt mantığı kontrolleri teslim notunda belirtilir. Gerçek Instagram hesabıyla giriş, gizli hesap listesi okuma ve cihazda uzun süreli arka plan çalışması burada doğrulanmamıştır. Web uç noktaları belgelenmiş bir entegrasyon garantisi sunmaz; Instagram'ın değişiklikleri veya erişim kısıtları nedeniyle çalışmayabilir. Giriş yapmak resmî API'ye takip listesi izni kazandırmaz. Uygulama Instagram'ın kendi web oturumunun erişebildiği uç noktaları okur.

## Telefonda ilk kullanım

1. `Black-Follow-0.2.1-test.apk` dosyasını Android 8.0 veya üzeri telefona kur.
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
- Manuel kontrolde uygulamanın 5 dakika bekleme şartı yoktur; aynı anda tek kontrol yapılabilir. Arka plan hatalarında bekleme süresi kademeli artar. HTTP 429 yanıtında `Retry-After` veya yukarıdaki kademeli yerel bekleme uygulanır. Giriş/izin doğrulaması istenirse kullanıcı yeniden doğrulayana kadar durur.
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

İmzalı çıktı: `out/Black-Follow-0.2.1-test.apk`. İmza değişkenleri yoksa yalnızca kurulamaz durumdaki `out/aligned.apk` üretilir. İmzalama yedeği kaynak kod arşivine dahil değildir; ayrı özel teslim dosyasıdır. Güncellemeleri silmeden kurabilmek için aynı anahtar ve daha yüksek `versionCode` kullanılmalıdır.

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
