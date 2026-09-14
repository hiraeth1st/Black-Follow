# Black Follow 0.3.2 doğrulaması

- 302 otomatik kontrol: önceki 279 kontrole ek 4 HTTP/snapshot, 14 SQLite migration/önizleme izolasyonu, 3 rapor metni ve 2 gerçek Monitor kontrolü.
- Üretim HTTP istemcisi 9 sayfada 182/200 takipçi sonucunu gözlemciye teslim eder, ardından 794/794 takip edilen kişi taramasını bitirir. Tam snapshot döndürmez; iki önizleme ayrı kalır. Rate yanıtında önce alınmış önizleme korunur ve ek istek yapılmaz.
- Gerçek SQLite üzerinde v3 -> v4 tablo eklemesi önceki edges/events/last_success kayıtlarını değiştirmedi. Önizleme sorgularının oturum izolasyonu, 101 satırlık sayfalama, tam kayıt temizliğinin rollback durumunda önizlemeyi geri getirmesi ve FK cascade davranışı test edildi.
- Üretim Monitor kodu önizlemeyi kaydederken tam commit veya bildirim çağırmadı. TXT başlıkları eksik toplamı ve zamanı açıkça ayırdı; önizleme kişisi takip olayı gibi yazılmadı.
- Android kaynak/Java/D8 derlemesi yerelde başarılı. GitHub Actions aynı testleri ve orijinal imzayı doğrular.
- Eksik 18 kişinin neden dönmediği belirlenmedi; veri uydurulmadı. 200 sayımını 182'ye eşitleyen veya eksik kayıtları takipten çıkma sayan bir değişiklik yok. Canlı kullanıcı hesabı, Android arayüzü ve cihazdaki SQLiteOpenHelper migration burada uçtan uca çalıştırılmadı.

## Önceki 0.3.1 doğrulaması


- 279 otomatik kontrol: önceki 263 kontrole ek olarak 6 sayfalama bütünlüğü ve 10 gerçek HTTP istemcisi/snapshot regresyon kontrolü.
- Üretim InstagramClient sınıfı sentetik HTTPS yanıtlarıyla 200 takipçi ve 794 takip edilen kişiyi sayfalar arası ve sayfa içi tekrarlarla birleştirir. Son kişi, dönen imlecin sonraki isteğe aktarılması, tek final profil kontrolü ve benzersiz ilerleme sayıları kontrol edilir.
- Tekrar eden satırlar eksik 2/3 listeyi tamamlamaz; sonraki listeye veya son sayım isteğine geçilmez. Null pk + geçerli id kabul edilir; ikisi de yoksa BF_LIST_ID oluşur. Sayfalama ortasında HTTP 429 gelirse işlem durur.
- Üç ara sayfa boyunca benzersiz kişi sayısı artmazsa, imleç değişse bile durur. İmleç döngüsü, geçersiz kimlik, boş ara sayfa, fazla/eksik toplam ve bitmiş listeye yeni sayfa ekleme kontrolleri korunur.
- Android kaynak, Java ve D8 derlemesi yerelde başarılı. CI aynı testleri ve önceki imza sertifikasını doğrular.
- Kullanıcının ekran görüntüsündeki ortak hata mesajı tekrar/bozuk kimlik nedenlerini ayırmıyordu. Canlı sayfa yanıtları bu ortamda alınmadı; hatanın yalnızca tekrarlardan kaynaklandığı iddia edilmez. Yeni mesajlar bu iki durumu ayırır. Android cihazı veya canlı kullanıcı hesabında uçtan uca doğrulama yapılmadı.
- Şema 3 ve mevcut geçmiş korunur. İki kişi listesi bütünüyle doğrulanmadan yeni takip/çıkış olayları kaydedilmez veya bildirilmez.

## Önceki 0.3.0 doğrulaması


- 263 otomatik kontrol: önceki 243 kontrole ek olarak 8 bildirim içerik/başlangıç/sınır kontrolü ve 12 gerçek SQLite sorgusuyla kendi hesap kimliği/çıkış filtresi/bildirim izolasyonu kontrolü.
- Aynı kullanıcının sabit kimlikle bulunan eski kaydı; ad değişikliği; başka oturumun aynı hesabı izlemesi; takipçi çıkışlarının takip edilen çıkışlarından ayrılması; eşit toplam sayıda bir geliş ve bir çıkış; eski çıkışların tekrar bildirilmemesi denetlendi.
- Profilim kaydı mevcut accounts tablosunda tutulur; şema değişmedi. ensureSelf işlemi transaction içinde sabit kimliği arar ve mevcut başlangıç kaydını yeniden kullanır. Bu metot cihazdaki SQLiteOpenHelper ile ayrıca çalıştırılmadı; kimlik seçimi ve bildirim sorguları SQLite üzerinde test edildi.
- Android kaynak, Java ve D8 derlemesi yerelde tamamlandı. GitHub Actions testleri ve orijinal sertifikayla APK imzasını doğrular.
- Alt sekmeler, Activity durum geri yükleme, gerçek Android bildirim teslimi ve canlı Instagram listeleri bu ortamda cihaz/emülatörde çalıştırılmadı. API ve giriş protokolü önceki çalışan sürümle aynıdır.

## Önceki 0.2.4 incelemesi


- Kullanıcı, 0.2.3 sürümünün kendi cihazında sorunsuz çalıştığını bildirdi. Giriş, oturum doğrulaması ve Instagram HTTP istek akışı bu güncellemede değiştirilmedi.
- **243 otomatik kontrol başarılı:** önceki 221 kontrole ek olarak rapor listeleri için 7, gerçek SQLite sorgularında filtre/arama/sayfalama/liste dışa aktarma için 13, gerçek Monitor tamamlanma bildirimi için 2 kontrol.
- Filtreli hareketlerde tekrar sayısının tüm geçmişten hesaplanması; başka oturumun verilerinin okunamaması; yüzde/alt çizgi içeren arama; 101 satırlık sayfa devam kontrolü; 205 kişilik eksiksiz rapor; başlangıç zamanı bilinmeyen kişi ve tespit aralığı doğrulandı.
- Android kaynak/Java/D8 derlemesi yerelde tamamlandı. GitHub Actions aynı testleri tekrar çalıştırıp orijinal sertifikalı APK üretir.
- Görsel Android UI testi, canlı Instagram liste testi ve arka plan bildirim teslimi bu yeni sürümde cihazda yapılmadı. Ekran yenileme sinyali üretim Monitor koduyla test edildi; Activity çizimi emülatörde çalıştırılmadı.
- Şema 3 korunur; veri taşıma veya hesap/olay silme işlemi yoktur. Dışa aktarma mevcut yerel kayıtlardan tek veritabanı işlemi içinde hazırlanır ve ağ isteği göndermez.

## Önceki 0.2.3 doğrulaması


- 221 otomatik kontrol: 19 veri bütünlüğü, 15 Retry-After, 19 tarihçe/bağlantı, 15 bildirim/tanı, 21 SQLite, 25 gerçek Session sınıfıyla kayıtlı bekleme/oturum, 32 eski oturum protokolü, 22 profil sorgusu, 24 WebView sonuç/kimlik doğrulaması, 15 gerçek Monitor sınıfıyla kontrol akışı, 14 HTTP istemcisi kontrolü.
- Eski iki saatlik yerel beklemeden elle profil yenilemeye geçiş; gerçek sunucu süresinin korunması; süre verilmeden tekrar gelen 429'un sayaç üretmemesi; arka planda tekrar istek yapılmaması; başarılı manuel kontrolle devam; hesabın değişmesi; kısmi liste hatasında geçmişin kaydedilmemesi test edildi. Android SharedPreferences/CookieManager ve ağ/Store sınırları bu testlerde sentetik; Session ve Monitor üretim kodudur.
- Android kaynakları, native Java ve D8 derlemesi yerelde başarılı. Native Activity/WebView çalıştırılmadı; burada Android cihazı/emülatörü yok.
- APK'nın `res/raw/session_probe.js` dosyasının aynısı açık ve kullanıcı tarafından izin verilmiş canlı Instagram test oturumunda, başka bir profil sayfasındayken çalıştırıldı. Ziyaret edilen profil yerine oturum sahibinin gezinme düğmesini doğru tanıdı. Bu test masaüstü Chrome'dadır; mobil WebView DOM yerleşiminin aynı olduğu doğrulanmadı.
- Önceki canlı tarayıcı kontrolünde izin verilen hedefte 26 takipçi ve 27 takip edilen hesap, pencereler kaydırılarak tüm kullanıcı adlarıyla görüntülenmişti. Bu veriler APK'ya veya test fixtures içine gömülmedi.
- Tarayıcıdaki doğrudan API isteği ortamın `ERR_BLOCKED_BY_CLIENT` hatasıyla açılamadı. Bu, Instagram HTTP 429 sonucu değildir. Tarayıcı çerezleri dışarı çıkarılmadı; canlı Android/API liste erişimi çözülmüş sayılmıyor.
- Bildirim teslimi ve uzun süreli arka plan taraması cihazda denenmedi. İlk liste, eksik liste, hesap izolasyonu ve bildirim seçimi mantığı otomatik testlerle denetlenir.
- Sürüm 0.2.3-test, versionCode 7, veritabanı şeması 3. Güncelleme kullanıcı geçmişini ve gerçek sunucu sürelerini korur; eski yerel beklemeyi kaldırır.
- GitHub Actions aynı testleri çalıştırır, orijinal sertifikayla imzalı APK ve ona ait SHA256SUMS.txt üretir. CI sonucu ilgili Actions çalışmasından doğrulanmalıdır.
