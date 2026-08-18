# Clojars Yayın Rehberi

[English](CLOJARS_RELEASE.en.md) | **Türkçe**

Bu belge `binance-clj` kütüphanesini Clojars'a güvenli ve tekrarlanabilir biçimde yayınlama sürecini açıklar. Hedef Maven koordinatı:

```clojure
io.github.ugurbay/binance-clj {:mvn/version "1.0.1"}
```

> [!IMPORTANT]
> Clojars deploy token'ını hiçbir sohbet mesajına, issue'ya, loga veya repository dosyasına yapıştırmayın. Token yalnız yayın komutunu çalıştırdığınız PowerShell process'inin environment alanında bulunmalıdır.

> [!WARNING]
> Clojars'taki `SNAPSHOT` olmayan sürümler değiştirilemez ve aynı sürüm yeniden yayınlanamaz. `VERSION`, Git tag'i ve üretilen POM tamamen uyuşmadan yayın yapmayın.

## 1. Bir Defalık Clojars Hesap Kurulumu

1. [Clojars](https://clojars.org/) üzerinde **GitHub ile giriş yapın**. GitHub hesabı `ugurbay` olmalıdır.
2. Profilinizde e-posta adresini doğrulayın ve mümkünse iki faktörlü kimlik doğrulamayı etkinleştirin.
3. GitHub ile giriş, `io.github.ugurbay` grup adını hesabınız için otomatik doğrular. Paket bu nedenle `io.github.ugurbay/binance-clj` adıyla yayınlanır.
4. Clojars kullanıcı adınızı kaydedin. Bu ad GitHub kullanıcı adından farklı olabilir ve `CLOJARS_USERNAME` değeri olarak kullanılacaktır.

## 2. İlk Deploy Token'ını Oluşturma

1. [Clojars Deploy Tokens](https://clojars.org/tokens) sayfasını açın.
2. İlk yayın için örneğin `binance-clj-first-release` açıklamalı bir token oluşturun.
3. Artifact henüz Clojars'ta var olmadığı için ilk token'ı artifact ile sınırlandırmak mümkün değildir. İlk yayın için geçici, kapsamlandırılmamış token kullanın.
4. Token yalnız bir kez gösterilir. Güvenli bir parola yöneticisine alın; repository'ye kaydetmeyin.
5. İlk yayın başarıyla tamamlandıktan sonra bu token'ı iptal edin ve sonraki sürümler için `io.github.ugurbay/binance-clj` veya ilgili grup ile sınırlandırılmış yeni token oluşturun.

Deploy token, Clojars hesap parolası değildir. Yayın araçları token'ı parola alanı üzerinden aldığı için environment değişkeninin adı `CLOJARS_PASSWORD` olur.

## 3. Release Öncesi Hazırlık

Repository kökünde çalışın:

```powershell
cd 'C:\Users\ismailugur\Documents\ChatGPT\BINANCE CLOJURE API CONNECTOR\Clojure Binance Connector'
```

`VERSION` dosyasındaki sürümü inceleyin. Her normal release için daha önce kullanılmamış Semantic Versioning değeri seçin. Sonra paketi yayınlamadan doğrulayın:

```powershell
.\scripts\verify-clojars-package.ps1
```

Bu kontrol:

- kaynak JAR'ını ve Maven POM'unu üretir;
- koordinatı, sürümü ve MIT lisans metadata'sını doğrular;
- lisans ve OpenAI ChatGPT/Codex geliştirme bildirimini JAR içinde doğrular;
- artifact'i yerel Maven deposuna kurar;
- temiz bir dış tüketici classpath'inden namespace'i yükler.

Ardından normal release kapısını çalıştırın:

```powershell
.\scripts\verify-phase-9.ps1
```

Canlı Testnet kabulü ayrıca çalıştırılacaksa yalnız Binance Spot Testnet credential'larıyla şu opt-in komutu kullanılır:

```powershell
.\scripts\verify-phase-9.ps1 -RunFullTestnet
```

Clojars yayını için Binance API key veya secret gerekmez.

## 4. Git Commit, Tag ve Push

Yayın script'i kirli worktree'yi ve etiketsiz commit'i reddeder. Release commit'ini main branch'e alın, sonra `VERSION` ile aynı etiketi oluşturup GitHub'a gönderin. `1.0.1` için:

```powershell
git status
git tag -a v1.0.1 -m 'binance-clj v1.0.1'
git push origin v1.0.1
```

Etiketi oluşturmadan önce `git status` temiz olmalı ve `HEAD` tam olarak yayınlanacak commit olmalıdır. Yanlış etiketi veya sürümü tahmin ederek düzeltmeye çalışmayın; yayın yapılmadıysa önce release sürecini durdurup Git geçmişini inceleyin.

## 5. Credential'ları Yalnız Mevcut Process'e Verme

Token değerini aşağıdaki örnekteki placeholder yerine yalnız kendi PowerShell pencerenizde yazın:

```powershell
$env:CLOJARS_USERNAME='clojars-kullanici-adiniz'
$env:CLOJARS_PASSWORD='clojars-deploy-tokeniniz'
```

Değerleri ekrana yazdırmadan varlıklarını kontrol edin:

```powershell
[bool]$env:CLOJARS_USERNAME
[bool]$env:CLOJARS_PASSWORD
```

Her iki komut da `True` vermelidir. `Get-ChildItem Env:` gibi bütün environment'ı döken komutları çalıştırmayın ve PowerShell transcript/log özelliği açıksa token girmeden önce kapatın.

## 6. Clojars'a Yayınlama

Yayın, açık onay parametresi olmadan başlamaz:

```powershell
.\scripts\publish-clojars.ps1 -ConfirmRelease
```

Script sırasıyla şunları zorunlu kılar:

- process-local Clojars kullanıcı adı ve deploy token;
- temiz Git worktree;
- `HEAD` üzerinde `v<VERSION>` etiketi;
- Faz 9 test, integration, secret scan, lint, format ve performans kontrolleri;
- JAR/POM paket ve dış tüketici doğrulaması;
- son olarak tek Clojars deploy işlemi.

Başarı mesajından sonra token'ı process environment'tan kaldırın:

```powershell
Remove-Item Env:CLOJARS_USERNAME
Remove-Item Env:CLOJARS_PASSWORD
```

PowerShell penceresini kapatmak da process-local değerleri siler.

## 7. Yayını Doğrulama

Artifact sayfası erişilebilir olmalıdır:

```text
https://clojars.org/io.github.ugurbay/binance-clj
```

Yeni ve boş bir klasörde Clojure CLI ile gerçek Clojars çözümlemesini sınayın:

```powershell
clojure -Srepro -Sdeps '{:deps {io.github.ugurbay/binance-clj {:mvn/version "1.0.1"}}}' -M -e "(require '[binance-clj.core :as core]) (println (core/runtime-info))"
```

Beklenen sonuçta en azından `:name "binance-clj"`, `:phase 9` ve `:status :ready` bulunur. Clojars/Maven indekslerinin kısa süreli gecikmesi mümkündür; başarısız çözümlemede aynı sürümü yeniden deploy etmeyin, önce birkaç dakika sonra yalnız consumer doğrulamasını tekrarlayın.

## 8. Tüketici Kullanımı

`deps.edn`:

```clojure
{:deps
 {io.github.ugurbay/binance-clj {:mvn/version "1.0.1"}}}
```

Leiningen:

```clojure
:dependencies [[io.github.ugurbay/binance-clj "1.0.1"]]
```

Kütüphanenin Testnet-first kullanımı için [Başlangıç Rehberi](GETTING_STARTED.md), hukuki ve finansal riskler için [Sorumluluk Reddi](../DISCLAIMER.md) okunmalıdır.

## Hata Durumları

- **401/403:** Kullanıcı adı yanlış, token geçersiz/iptal edilmiş veya grup yetkisi yoktur. Hesap parolasını kullanmayın.
- **Group not verified:** Clojars'tan çıkıp doğru `ugurbay` GitHub hesabıyla tekrar giriş yapın ve `io.github.ugurbay` doğrulamasını kontrol edin.
- **Artifact already exists:** Aynı release sürümü daha önce yayınlanmıştır. Yeniden deploy etmeyin; değişiklik gerekiyorsa `VERSION` değerini artıran yeni release hazırlayın.
- **Dirty worktree / missing tag:** Script bilinçli olarak durmuştur. Değişiklikleri review/commit edin ve tam sürüm tag'ini doğru commit'e koyun.
- **Credential leak scan:** Yayını kesinlikle sürdürmeyin. Bildirilen dosyayı ve Git geçmişini güvenli biçimde inceleyin; açığa çıkan token/credential'ı iptal edin.

## Resmî Kaynaklar

- [Clojars: Clojure CLI ile yayın](https://github.com/clojars/clojars-web/wiki/Clojure-CLI-deps.edn)
- [Clojars: Deploy Tokens](https://github.com/clojars/clojars-web/wiki/Deploy-Tokens)
- [Clojars: Verified Group Names](https://github.com/clojars/clojars-web/wiki/Verified-Group-Names)
- [Clojars: Pushing ve doğrulamalar](https://github.com/clojars/clojars-web/wiki/Pushing)
- [Clojure: tools.build guide](https://clojure.org/guides/tools_build)
- [deps-deploy](https://github.com/slipset/deps-deploy)
