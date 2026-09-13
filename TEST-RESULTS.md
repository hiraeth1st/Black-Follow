# Black Follow 0.2.1 doğrulaması

**135 kontrol başarılı:** 19 veri bütünlüğü, 15 bekleme politikası, 19 tarihçe/bağlantı, 15 yeni kişi bildirimi/güvenli tanı, 21 SQLite, 32 oturum/yanıt sınıflandırma, 14 HTTP istemcisi kontrolü.

- HTTP testleri gerçek InstagramClient sınıfını süreç içindeki sahte HTTPS yanıtları ve küçük Android sınıf ikameleriyle çalıştırır. Canlı Instagram hesabı kullanılmaz. 429 ve Retry-After, 403 gövdesinde challenge, feedback işlem kısıtı, HTTP 200 bekleme yanıtı, null uyarı alanları, güvenli tanı, çerez başlığında harf farkı ve engelde tek istekten sonra durma doğrulandı.
- SQLite testleri üretim SQL ifadeleriyle Python sqlite3 üzerinde çalışır. Geçiş, geçmiş koruma, tekrar takip ve tüm rapor kapsamına ek olarak bildirimlerin yalnızca son kontrolün eklenmelerini alması ve farklı oturumun olaylarını almaması doğrulandı.
- Bildirim metni testleri başlangıç listesinin sessiz kalması, yalnızca çıkışların bildirilmemesi, takipçi/takip ayrımı, @firat eklenmesi ve büyük değişiklikte metnin sınırlanmasını kapsar.
- Ana ekran ve giriş ekranındaki FLAG_SECURE kaldırıldı. Android POST_NOTIFICATIONS izni, bildirim kanalı, izin/ayar düğmesi ve hesabın Hareketler sekmesine bağlanan PendingIntent eklendi.
- Java derlemesi, AAPT2, D8 ve imza doğrulaması başarılı. Paket com.blackapps.follow, versionCode 5, 0.2.1-test, Android 8.0+.
- Önceki 0.2.0 ile aynı imza; uygulamayı silmeden güncellenebilir. APK'da önceden doldurulmuş hesap/oturum verisi veya özel imzalama anahtarı yoktur.
- Android cihazda bildirim teslimi/izin ekranı, ekran görüntüsü, dosya seçici, canlı Instagram veri erişimi ve uzun süreli arka plan çalışması burada denenmedi. Kullanıcının bildirdiği gerçek erişim engelinin çözüldüğü iddia edilmez; yeni sürüm sonraki hatada istek aşamasını ve HTTP durumunu gösterir.

Sertifika SHA-256: `e121d877773cebfa03381f990f8218e47bc1b07b33ee5f078950d3092880d0f5`
