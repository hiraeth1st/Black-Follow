# GitHub otomatik APK derlemesi

Hazırlanan kaynak sürümü: 0.4.6-test (versionCode 21).

## Depo

Kaynak depo: https://github.com/hiraeth1st/Black-Follow

`main` dalına gönderilen değişiklikler otomatik derlemeyi tetikler. İş akışı elle de başlatılabilir. Aynı imzayla APK üretmek için aşağıdaki iki Actions secret'ı gereklidir.

## İlk kurulum

1. Depoda **Settings → Secrets and variables → Actions → New repository secret** yoluyla iki gizli değer eklenmeli:
   - `BF_KEYSTORE_BASE64`: mevcut black-follow.jks dosyasının Base64 karşılığı.
   - `BF_KEY_PASSWORD`: mevcut imzalama anahtarının parolası.
   Özel imza yedeği bu değerleri hazırlamak için kullanılır. Anahtar, parola ve Base64 metni depo dosyası olarak yüklenmez. Kaynak arşivinde bunlar bulunmaz.
2. Secrets tamamlandıktan sonra **Actions → Build Android APK → Run workflow → main** ile derlemeyi başlat. Sonraki main güncellemeleri otomatik derlenir.
3. Başarılı çalışmanın **Artifacts → Black-Follow-APK-…** dosyasını indirip ZIP içindeki APK'yı kur. SHA256SUMS.txt de aynı arşivdedir.

## Derlemenin davranışı

- Java 17 ve Android SDK 35 ile testleri çalıştırıp APK üretir.
- Sürüm numarası değişse de güncel Black-Follow APK'sını çıktıya ekler.
- İmza eksikse kurulamaz bir APK'yı başarılı çıktı diye sunmaz; açık hata ile durur.
- Sertifika önceki APK'larla aynı olmalıdır; farklı anahtar kullanılırsa derleme doğrulaması durur.
- İmza anahtarı geçici dosyası adım bitince silinir; indirilen çıktıya yalnızca APK ve SHA-256 listesi girer.
- Mevcut secrets ile imzalı APK üretimi doğrulandı. Her yeni sürümün sonucu kendi Actions çalışmasından kontrol edilir.

Kaynak: https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets
