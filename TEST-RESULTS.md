# Black Follow 0.4.6 doğrulaması

## Değişiklik kapsamı

- Eski takipçi/takip GraphQL bağlantı taraması kaldırıldı.
- `WebScanActivity`, `WebScanData`, WebView kaydırma JavaScript'i ve bunlara ait testler kaldırıldı.
- Normal REST liste sayfası isteği `count=200` kullanacak şekilde güncellendi.
- Eksik terminal listeler için kullanıcı adı önek araması eklendi: `a-z`, `0-9`, `.` ve `_`.
- Arama yanıtları sayısal kullanıcı kimliğiyle tekilleştiriliyor ve kullanıcı adı önek eşleşmesiyle filtreleniyor.
- Kalabalık/yarım önekler en fazla üç karakter derinliğine bölünüyor; liste başına istek bütçesi sınırlı.
- Tam iki liste ve son profil sayımı doğrulanmadan geçmiş/bildirim güncellenmiyor.

## Otomatik kontroller

- Önek planlayıcısı: kök karakter kapsamı, alt önek üretimi, tam önek filtresi, bölme eşiği, derinlik ve istek bütçesi.
- Android kaynak derlemesi, önek planlayıcısının saf mantık testleri ve mevcut veri bütünlüğü/oturum/veritabanı testleri birlikte çalıştırılıyor.
- Önceki ilişki bütünlüğü, geçmiş metni, oturum, profil araması, yanıt politikası, veritabanı geçişi ve monitör testleri korunuyor.
- CI, testlerden sonra Android 35 kaynak derlemesi/D8 işlemi yapıyor ve APK'yı özgün sertifika özetiyle doğruluyor.

## Canlı cihaz sınırı

Otomatik testler Instagram'ın canlı yanıtını garanti etmez. Özellikle arama sonuç sınırı, rate-limit ve hesap bazlı liste kısıtlamaları cihazda gerçek oturumla doğrulanmalıdır. Tam toplam elde edilemezse uygulamanın beklenen davranışı önizlemeyi saklamak ve doğrulanmış geçmişi değiştirmemektir.

## 0.4.6 ek kontrolleri

- Önceki doğrulanmış listedeki eksik kullanıcılar için sınırlı hedefli arama bütçesi.
- Yoğun alt öneklerin önceliklendirilmesi ve sıfır eşleşmeli görünen-ad gürültüsünün bölünmemesi.
- Dört karakterlik üst derinlik, toplam 220 istek bütçesi ve elle taramada 12 dakikalık zaman sınırı.
- Tam sonucun dışında geçmiş ve bildirim üretmeme kuralı değişmedi.

## 0.4.6 ek kontrolleri

- Bilinen önek nüfusundan daha az arama sonucu gelmesi alt-dal bölme sebebidir.
- Takip edilenler URL'sinde `includes_hashtags=false`; iki bağımsız kök turu ve ek REST geçişleri kaynak doğrulamasına dahildir.
- Önceki önizleme doğrudan geçmişe eklenmez; yalnızca aynı sayısal kimlik veya kullanıcı adı arama sonucunda yeniden görünürse kurtarılır.

## 0.4.6 ek kontrolleri

- Aramalı ilişki istekleri `count=1000`, sorgusuz normal liste istekleri `count=200` kullanır.
- Altı Türkçe harf kök taramasına dahildir; `I/ı` ve `İ/i` Türkçe yerel dönüşümü doğrulanır.
- Türkçe görünen-ad sonuçları ilişki endpointinden geldikten sonra sayısal kimlikle tekilleştirilir ve alt kullanıcı adı dallarına ayrılmaz.

## 0.4.6 ilişki taraması denetimi

- Normal cursor isteğinde `count=200`, `rank_token` ve `max_id`; arama isteğinde yalnızca kanonik arama parametreleri bulunduğu saf birim testleriyle doğrulanır.
- Arama URL'sinde `count`, `rank_token` veya `max_id` bulunması regresyon hatasıdır. Türkçe sorguların UTF-8 kodlanması ve takip edilen aramasında `includes_hashtags=false` kontrol edilir.
- Farklı yarım taramaların beklenen toplamı aşmadan birleşmesi; daha kötü yeni taramanın daha zengin önizlemeyi silememesi test edilir.
- Arama sonucunun eşleşme sebebi üyelik doğrulaması olarak kullanılmaz; endpoint kapsamındaki tüm geçerli sayısal kimlikler birleştirilir.
- Opsiyonel arama HTTP 400/404 ile reddedilirse çekirdek cursor sonucu korunur ve işlem `BF_LIST_PARTIAL` olarak sonlanır; generic kontrol hatasına dönüşmez.
- 100.000 kişilik doğrulama sınırı ve 1.000 sayfalık cursor güvenlik sınırı kaynakta sabitlenmiştir.

- Tam kullanıcı adı veya özel karakter sorgularından biri HTTP 400 döndürürse yalnızca o sorgu atlanır. Güvenli ilk ASCII kök sorgusu başarısızsa arama yüzeyi kapatılır; çekirdek cursor sonuçları korunur.

- İlk REST akışından sonra, güncel web istemcilerindeki gibi farklı `hesap_id_UUID` rank tokenlarıyla toplam on akışa kadar birleştirme yapılır; art arda iki akış yeni kimlik getirmezse gereksiz istekler durur.
- İlişki araması önce web uyumlu `count=100`, HTTP 400/404 halinde count/rank/cursor içermeyen minimal biçimle denenir; çalışan biçim takipçi ve takip listesi için ayrı hatırlanır.
- Boş/tekrarlanan imleç ve üç sayfalık durgunluk, alınmış kimlikleri silmez; bağımsız REST akışları ve isteğe bağlı aramalar devam eder. Fazla kişi ve geçersiz kimlik ölümcül kalır.
- Web istekleri `X-ASBD-ID`, Fetch metadata ve sunucunun güncellediği `X-IG-WWW-Claim` ile gönderilir; hesap değişiminde claim temizlenir.

## 0.4.6 tamamlama zinciri

- Web REST → özel mobil REST → güncel özel mobil GraphQL → hedefli/liste araması sırası uygulanır.
- Mobil yetkilendirme `Bearer IGT:2:<base64-json>` biçiminde yalnızca mevcut `ds_user_id`, `sessionid` ve `should_use_header_over_cookies` alanlarından oluşturulur.
- Mobil uygulama profili Instagram 446.0.0.49.77, App ID 567067343352427 ve güncel Bloks sürüm kimliğiyle sabitlenmiştir.
- FollowersList document ID `28479704797510738576165798526`; FollowingList document ID `161046392817718486717479294775` ve güncel kök alanları birim testleriyle doğrulanır.
- Mobil yöntem sonuçları da sayısal kullanıcı kimliğiyle tekilleştirilir; profil toplamından fazla kişi veya tarama sonu toplam değişikliği ölümcül kalır.
- Eksik tamamlama bir defalık hata sayılmaz; 30 dakika gecikmeli tekil JobScheduler işiyle yeniden denenir. Başarılı tam kayıt bu işi iptal eder.
