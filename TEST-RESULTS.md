# Black Follow 0.2.3 doğrulaması

- 221 otomatik kontrol: 19 veri bütünlüğü, 15 Retry-After, 19 tarihçe/bağlantı, 15 bildirim/tanı, 21 SQLite, 25 gerçek Session sınıfıyla kayıtlı bekleme/oturum, 32 eski oturum protokolü, 22 profil sorgusu, 24 WebView sonuç/kimlik doğrulaması, 15 gerçek Monitor sınıfıyla kontrol akışı, 14 HTTP istemcisi kontrolü.
- Eski iki saatlik yerel beklemeden elle profil yenilemeye geçiş; gerçek sunucu süresinin korunması; süre verilmeden tekrar gelen 429'un sayaç üretmemesi; arka planda tekrar istek yapılmaması; başarılı manuel kontrolle devam; hesabın değişmesi; kısmi liste hatasında geçmişin kaydedilmemesi test edildi. Android SharedPreferences/CookieManager ve ağ/Store sınırları bu testlerde sentetik; Session ve Monitor üretim kodudur.
- Android kaynakları, native Java ve D8 derlemesi yerelde başarılı. Native Activity/WebView çalıştırılmadı; burada Android cihazı/emülatörü yok.
- APK'nın `res/raw/session_probe.js` dosyasının aynısı açık ve kullanıcı tarafından izin verilmiş canlı Instagram test oturumunda, başka bir profil sayfasındayken çalıştırıldı. Ziyaret edilen profil yerine oturum sahibinin gezinme düğmesini doğru tanıdı. Bu test masaüstü Chrome'dadır; mobil WebView DOM yerleşiminin aynı olduğu doğrulanmadı.
- Önceki canlı tarayıcı kontrolünde izin verilen hedefte 26 takipçi ve 27 takip edilen hesap, pencereler kaydırılarak tüm kullanıcı adlarıyla görüntülenmişti. Bu veriler APK'ya veya test fixtures içine gömülmedi.
- Tarayıcıdaki doğrudan API isteği ortamın `ERR_BLOCKED_BY_CLIENT` hatasıyla açılamadı. Bu, Instagram HTTP 429 sonucu değildir. Tarayıcı çerezleri dışarı çıkarılmadı; canlı Android/API liste erişimi çözülmüş sayılmıyor.
- Bildirim teslimi ve uzun süreli arka plan taraması cihazda denenmedi. İlk liste, eksik liste, hesap izolasyonu ve bildirim seçimi mantığı otomatik testlerle denetlenir.
- Sürüm 0.2.3-test, versionCode 7, veritabanı şeması 3. Güncelleme kullanıcı geçmişini ve gerçek sunucu sürelerini korur; eski yerel beklemeyi kaldırır.
- GitHub Actions aynı testleri çalıştırır, orijinal sertifikayla imzalı APK ve ona ait SHA256SUMS.txt üretir. CI sonucu ilgili Actions çalışmasından doğrulanmalıdır.
