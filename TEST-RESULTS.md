# Black Follow 0.2.2 doğrulaması

- 157 yerel kontrol başarılı: 19 veri bütünlüğü, 15 bekleme, 19 tarihçe/bağlantı, 15 bildirim/tanı, 21 SQLite, 32 oturum, 22 profil sorgusu ve 14 HTTP istemcisi kontrolü.
- Yeni profil testleri tam kullanıcı adı eşleşmesi, benzer hesabı reddetme, kayıtlı kimlikle aramasız sorgu, kullanıcı adı değişimi, POST hedefi/verileri, kimlik uyuşmazlığı, eksik kullanıcı, GraphQL hatası ve engelde ek istek yapılmamasını doğrular.
- HTTP istemcisi sentetik HTTPS yanıtlarıyla çalıştırıldı; gerçek profil POST yolu, 429/Retry-After, diğer erişim engelleri, null alanlar ve çerez başlıkları kontrol edildi.
- Android kaynakları, native Java ve D8 derlemesi yerelde başarılı. Cihazda canlı listeler, bildirim teslimi ve arka plan davranışı bu sürüm için denenmedi.
- Kullanıcının izin verdiği herkese açık profil bu ortamdan açılmaya çalışıldı. Yanıt Instagram giriş sayfasına yönlendi; takipçi/takip sayısı veya kişi listesi alınamadı. Bu sonuç telefon oturumundaki yeni GraphQL akışının başarılı/başarısız olduğunu göstermez.
- Önceki 0.2.1 cihaz görüntüsünde profil web isteği HTTP 429 ile engelleniyordu. 0.2.2 bu istek akışını değiştirir; canlı erişim sorununun çözüldüğü iddia edilmez.
- Sürüm 0.2.2-test, versionCode 6, aynı veritabanı şeması 3. Güncelleme mevcut geçmişi ve bekleme süresini korur.
- GitHub Actions her çalışmanın imzalı APK'sını ve ona ait SHA256SUMS.txt dosyasını birlikte üretir. Yerelde üretilen eski APK özeti yeni CI çıktısının özeti olarak kullanılmaz.
