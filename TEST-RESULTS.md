# Black Follow 0.4.4 doğrulaması

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

## 0.4.4 ek kontrolleri

- Önceki doğrulanmış listedeki eksik kullanıcılar için sınırlı hedefli arama bütçesi.
- Yoğun alt öneklerin önceliklendirilmesi ve sıfır eşleşmeli görünen-ad gürültüsünün bölünmemesi.
- Dört karakterlik üst derinlik, toplam 220 istek bütçesi ve elle taramada 12 dakikalık zaman sınırı.
- Tam sonucun dışında geçmiş ve bildirim üretmeme kuralı değişmedi.

## 0.4.4 ek kontrolleri

- Bilinen önek nüfusundan daha az arama sonucu gelmesi alt-dal bölme sebebidir.
- Takip edilenler URL'sinde `includes_hashtags=false`; iki bağımsız kök turu ve ek REST geçişleri kaynak doğrulamasına dahildir.
- Önceki önizleme doğrudan geçmişe eklenmez; yalnızca aynı sayısal kimlik veya kullanıcı adı arama sonucunda yeniden görünürse kurtarılır.

## 0.4.4 ek kontrolleri

- Aramalı ilişki istekleri `count=1000`, sorgusuz normal liste istekleri `count=200` kullanır.
- Altı Türkçe harf kök taramasına dahildir; `I/ı` ve `İ/i` Türkçe yerel dönüşümü doğrulanır.
- Türkçe görünen-ad sonuçları ilişki endpointinden geldikten sonra sayısal kimlikle tekilleştirilir ve alt kullanıcı adı dallarına ayrılmaz.
