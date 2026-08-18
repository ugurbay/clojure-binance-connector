# Public Release ve Maintainer Checklist

## Her Release Öncesi

- [ ] Binance Spot API Docs/CHANGELOG ve Testnet değişiklikleri incelendi.
- [ ] `docs/BINANCE_VERSION.md` referansları güncel.
- [ ] Public API breaking change analizi yapıldı.
- [ ] Unit/contract ve offline integration testleri temiz.
- [ ] Secret scan temiz.
- [ ] Lint ve format temiz.
- [ ] Dependency ve JDK/Clojure sürümleri gözden geçirildi.
- [ ] `CHANGELOG.md` güncellendi.
- [ ] Known limitations ve migration notları güncellendi.
- [ ] Gerekliyse opt-in full Spot Testnet acceptance geçti.
- [ ] Tag imzalanabilir/immutable release commit'ine işaret ediyor.

## GitHub Repository Kontrolü

- [ ] Public visibility doğru.
- [ ] Default branch `main`.
- [ ] Branch protection/ruleset ve required CI değerlendirildi.
- [ ] Private vulnerability reporting açık.
- [ ] Issue/PR şablonları çalışıyor.
- [ ] LICENSE GitHub tarafından MIT olarak tanınıyor.
- [ ] Description, topics ve release linkleri güncel.
- [ ] Repository veya artifacts içinde credential yok.

## Production Tüketicisine Devir

- [ ] Release tag/commit sabitlendi.
- [ ] Kullanıcı disclaimer ve security policy'yi kabul etti.
- [ ] Testnet smoke ve order lifecycle bağımsız tekrarlandı.
- [ ] Production API key en az yetkili ve withdrawal kapalı.
- [ ] IP allowlist/secret manager/rotation tanımlı.
- [ ] Order limit, position sizing ve kill switch uygulama katmanında.
- [ ] Unknown execution, UDS gap ve rate-limit operasyon prosedürü hazır.
- [ ] Monitoring, alerting ve manuel Binance UI erişimi test edildi.

Bu checklist garanti veya sertifika değildir; maintainer ve tüketicinin disiplinli release sürecine yardımcı olur.
