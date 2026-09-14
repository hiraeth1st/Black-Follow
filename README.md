# Black Follow — Android 0.4.3

Instagram takipçi ve takip listelerine oturumun izin verdiği ölçüde erişip yerel geçmiş tutan bağımsız Android uygulaması. Instagram veya Meta'nın resmî uygulaması değildir.

## 0.4.3: çoklu REST kurtarma ve eksik-dal tespiti

- Takip edilenler isteklerine Instagram'ın kanonik `includes_hashtags=false` parametresi eklendi.
- Normal taramadan sonra iki bağımsız rank-token REST geçişi birleştirilir; takipçilerde ayrıca en yeni/en eski sıralı geçişler denenir.
- Önceki tam listeye ek olarak son yarım önizleme de yalnızca tam kullanıcı adıyla yeniden doğrulanacak aday kaynağıdır.
- Aynı önek altında zaten bilinen kullanıcı sayısından daha az sonuç dönerse o dal eksik kabul edilip alt öneklere ayrılır.
- Kök önekler iki bağımsız rank bağlamıyla taranır; toplam doğrulanmadan geçmiş yine değişmez.

## 0.4.3: hedefli kurtarma ve yoğunluk önceliği

- Normal liste eksik kaldığında, son doğrulanmış listede bulunup yeni yanıtta görünmeyen kullanıcılar önce tam kullanıcı adlarıyla hedefli aranır.
- Geniş önek taramasında kalabalık alt önekler önce işlenir; olası olmayan boş kombinasyonlar kuyruğun başını tüketmez.
- Sadece görünen ad eşleşmesi getiren kalabalık aramalar artık gereksiz alt dallara ayrılmaz.
- Elle başlatılan tam kontrolün güvenli süre sınırı 12 dakikadır; otomatik kontroller 7 dakikalık sınırı korur.
- Tam sayı yine doğrulanamazsa önizleme saklanır ve geçmiş değişmez.

## 0.4.3: eksik listeleri önek aramasıyla tamamlama

Bu sürümde eski GraphQL “ikinci yöntem” ve görünür WebView kaydırma ekranı kaldırıldı. Liste yenileme artık tek bir doğrulanabilir akış kullanır:

1. Takipçi veya takip edilenler listesi normal REST sayfalamasıyla alınır.
2. Profildeki toplam ile benzersiz kişi sayısı eşleşmezse liste-içi arama devreye girer.
3. Kullanıcı adları önce `a-z`, sonra `0-9`, `.` ve `_` önekleriyle aranır.
4. Kalabalık veya yarım kalan bir önek gerektiğinde iki ve üç karakterli alt öneklere bölünür.
5. Arama yanıtlarında yalnızca kullanıcı adı gerçekten istenen önekle başlayan kişiler kabul edilir; görünen ad eşleşmeleri listeye katılmaz.
6. Bütün sonuçlar Instagram'ın sayısal kullanıcı kimliğiyle tekilleştirilir.
7. İki listenin benzersiz sayıları profil toplamlarıyla tam eşleşmeden geçmiş, takipten çıkma kaydı veya bildirim güncellenmez.
8. Tam sonuçtan sonra profil sayıları tekrar okunur; tarama sırasında değişiklik olmuşsa yeni liste reddedilir.

Önek araması sınırlı bir istek ve derinlik bütçesine sahiptir. Instagram tüm kişileri hiçbir yöntemde göndermiyorsa alınan bölüm yalnızca **önizleme** olarak saklanır; doğrulanmış geçmiş korunur.

Sürüm: **0.4.3-test**, `versionCode=18`, veritabanı şeması **4**. Aynı imza kullanıldığında önceki sürüm silinmeden güncellenebilir.

## Temel özellikler

- Birden fazla hesabın takipçi ve takip edilen listelerini yerelde saklama
- Yeni gelenleri ve listeden çıkanları sayısal kullanıcı kimliğiyle karşılaştırma
- Kendi profilinde “Beni takipten çıkanlar” görünümü
- Eksik taramaları doğrulanmış geçmişten ayrı önizleme olarak gösterme
- Yerel TXT dışa aktarma
- Android bildirimleri ve 6/12/24 saatlik arka plan kontrol seçenekleri
- Oturum, rate-limit, challenge ve kimlik değişimi durumlarında güvenli durdurma

## Güvenlik ve veri sınırları

- Parola uygulamaya verilmez; giriş Instagram'ın HTTPS WebView sayfasında yapılır.
- Cookie, parola, kullanıcı listesi veya profil kimlikleri harici bir sunucuya gönderilmez.
- Uygulama challenge atlatma, proxy döndürme veya erişim kısıtını aşma denemesi yapmaz.
- Instagram bir listeyi eksik döndürürse eksik kişiler “takipten çıktı” kabul edilmez.
- Arama tamamlama yalnızca hedef hesabın takipçi/takip edilenler liste yolunu kullanır.
- Kayıtlar uygulamanın yerel SQLite veritabanında tutulur.

## Telefonda kullanım

1. GitHub Actions çıktısından `Black-Follow-0.4.3-test.apk` dosyasını indirip Android 8.0 veya üzeri cihaza kur.
2. **Instagram'a giriş yap** ekranında Instagram hesabınla giriş yap ve gerekiyorsa doğrulamayı tamamla.
3. **Giriş yaptım • oturumu doğrula** düğmesine bas.
4. Bir kullanıcı adı ekle veya **Profilim** sekmesini aç.
5. **Listeyi şimdi yenile** düğmesine bas. Normal sayfalama eksik kalırsa önek araması aynı işlem içinde otomatik başlar.
6. Sonuç tam değilse kişiler önizleme olarak görünür; geçmiş değişmez.

Kalabalık listelerde önek tamamlama normal taramadan daha uzun sürebilir. Uygulamayı zorla kapatmak veya oturumu değiştirmek işlemi güvenli biçimde durdurur.

## Yerel test

JDK 17 ile temel testler:

```bash
bash test.sh
```

JSON bağımlılığı bulunan oturum ve profil testleri:

```bash
export BF_JSON_JAR=/path/to/json-20240303.jar
bash test.sh
```

Beklenen test JAR SHA-256 değeri:

```text
3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed
```

## APK derleme

Android SDK platform 35, build-tools 35.0.0 ve JDK 17 gerekir.

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export BF_KEYSTORE=/path/to/original-signing-key.jks
export BF_KEY_ALIAS=blackfollow
export BF_KEY_PASSWORD='...'
bash build-apk.sh
```

İmzalı çıktı:

```text
out/Black-Follow-0.4.3-test.apk
```

İmza değişirse Android mevcut kurulumun üzerine güncelleme yapmaz. Anahtar dosyası ve parolası repoya eklenmemelidir.

## Bilinen sınırlar

- Instagram özel ve değişebilen web uçları kullandığı için erişim her hesapta garanti değildir.
- Çok kalabalık öneklerde uygulama alt öneklere ayrılsa da Instagram arama yanıtını yine sınırlayabilir.
- Tarama sırasında hesap toplamı değişirse sonuç bilerek kaydedilmez.
- Arka plan kontrolü Android pil tasarrufu nedeniyle gecikebilir.
- Oturum sahibinin erişemediği gizli hesap listeleri alınamaz.
