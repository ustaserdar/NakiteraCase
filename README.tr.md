**Dil:** [English](README.md) · Türkçe

# Nakitera Brokerage Backend

Aracı kurum çalışanlarının (ve müşterilerin) hisse emri oluşturması, listelemesi, iptal etmesi ve eşleştirmesi için production tarzı bir Spring Boot REST API. TRY ayrı bir cüzdan tablosu değil, normal bir `Asset` satırı olarak tutulur. Emir oluşturma kullanılabilir bakiyeyi rezerve eder; iptal serbest bırakır; eşleştirme (admin) toplam bakiyeleri mahsup eder.

## Kapsam

Zorunlu:

- Her `/api/**` ucunda HTTP Basic kimlik doğrulama
- Atomik rezervasyon ile BUY/SELL emri oluşturma
- Müşteri ve kapsayıcı tarih aralığına göre emir listeleme
- Yalnızca PENDING emirlerin iptali ve rezervasyonun tek sefer iadesi
- Müşteri varlık (asset) listeleme
- İlişkisel kalıcılık (H2)
- Çekirdek iş kuralları için birim ve entegrasyon testleri

Bonus (uygulandı):

- Müşteri kimlik doğrulama ve müşteri kapsamlı yetkilendirme
- Yalnızca admin’in atomik toplu emir eşleştirmesi

## Sürümler ve önkoşullar

- JDK 21 veya üzeri (bytecode hedefi 21; testler JDK 24/25 üzerinde de çalışır)
- Maven Wrapper (`./mvnw`); yerel Maven kurulumu gerekmez
- `java -version` 21+ ise `JAVA_HOME` export etmeniz gerekmez
- Harici veritabanı gerekmez

## Derleme, test ve çalıştırma

Depo kökünden (clone sonrası ekstra ortam değişkeni olmadan):

```bash
./mvnw test
./mvnw spring-boot:run
```

API `http://localhost:8080` adresinde dinler.

## Swagger UI

Dolu örnek isteklerle etkileşimli dokümantasyon:

- Arayüz: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Swagger UI ve OpenAPI spec kimlik doğrulama olmadan açıktır. **Authorize** ile `admin` / `admin123` (veya bir müşteri kullanıcısı) girin, ardından **Try it out** kullanın. Emir oluşturmada BUY ve SELL örnekleri; eşleştirmede örnek `orderIds` gövdesi; liste/iptal/varlık uçlarında örnek query ve path değerleri vardır.

## Kimlik doğrulama (değerlendirme kimlikleri)

Kullanıcılar bellek içindedir (production kimlik deposu değildir). Parolalar Spring property bağlama ile ortam değişkenlerinden değiştirilebilir.

| Kullanıcı adı | Parola | Rol |
| --- | --- | --- |
| `admin` | `admin123` | `ADMIN` — tüm müşteriler, eşleştirme dahil |
| `customer-1` | `customer123` | `CUSTOMER` — yalnızca `customer-1` |
| `customer-2` | `customer123` | `CUSTOMER` — yalnızca `customer-2` |

Örnek override:

```bash
NAKITERA_SECURITY_ADMIN_PASSWORD=admin123 ./mvnw spring-boot:run
```

Spring relaxed binding: `nakitera.security.admin.password`.

Bir müşteri başka müşterinin `customerId` değerini gönderirse `403` / `ACCESS_DENIED` alır. Yetki kaynağı istemcinin gönderdiği alan değil, kimliği doğrulanmış kimliktir. Çapraz müşteri erişimi uygulanmaz.

## Veritabanı ve H2 konsolu

- JDBC URL: `jdbc:h2:mem:nakitera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Kullanıcı adı: `sa`
- Parola: boş
- Hibernate `ddl-auto=update`
- Konsol: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (yerel değerlendirme için Basic auth olmadan açık)

JDBC sürücü sınıfı: `org.h2.Driver`. Konsolda yukarıdaki JDBC URL’yi kullanın.

## Seed verisi

Açılışta (`test` profili dışında) yoksa şu varlık satırları oluşturulur:

| customerId | assetName | size | usableSize |
| --- | --- | --- | --- |
| customer-1 | TRY | 100000 | 100000 |
| customer-1 | THYAO | 100 | 100 |
| customer-1 | ASELS | 50 | 50 |
| customer-2 | TRY | 50000 | 50000 |
| customer-2 | THYAO | 20 | 20 |

Herkese açık bir “asset oluştur” API’si yoktur. Ek müşteri veya sembol gerekirse değerlendirici H2 konsolundan satır ekleyebilir.

## API

Taban yol: `/api/v1`. İstek ve yanıt gövdeleri JSON.

| Method | Path | Auth | Başarı |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | admin veya sahip müşteri | `201 Created` (idempotent tekrarında `200`) |
| GET | `/api/v1/orders/{orderId}` | admin veya sahip müşteri | `200 OK` |
| GET | `/api/v1/orders?customerId&startDate&endDate` | admin veya sahip müşteri | `200 OK` |
| DELETE | `/api/v1/orders/{orderId}` | admin veya sahip müşteri | `200 OK` (satır kalır, durum `CANCELED`) |
| GET | `/api/v1/assets?customerId` | admin veya sahip müşteri | `200 OK` |
| GET | `/api/v1/ledger?customerId` | admin veya sahip müşteri | `200 OK` |
| POST | `/api/v1/admin/orders/match` | yalnızca admin | `200 OK` |

İsteğe bağlı liste filtreleri: `status`, `orderSide`, `assetName`.

Tarih aralığı: sunucunun ürettiği `createDate` üzerinde kapsayıcı `startDate` ve kapsayıcı `endDate`. Sıra: `createDate DESC`, `id DESC`. `startDate > endDate` → `400` / `INVALID_DATE_RANGE`.

Yetersiz kullanılabilir bakiye ve PENDING olmayan iptal/eşleştirme → `409 Conflict`.

Hata gövdesi:

```json
{
  "timestamp": "2026-05-05T12:00:00Z",
  "status": 409,
  "code": "INSUFFICIENT_USABLE_BALANCE",
  "message": "Customer does not have sufficient usable TRY balance",
  "path": "/api/v1/orders"
}
```

Kararlı kodlar: `VALIDATION_ERROR`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `ORDER_NOT_FOUND`, `ASSET_NOT_FOUND`, `INSUFFICIENT_USABLE_BALANCE`, `ORDER_NOT_PENDING`, `INVALID_DATE_RANGE`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_CONFLICT`.

### Örnek cURL

```bash
# BUY oluştur (isteğe bağlı Idempotency-Key, retry’da ikinci rezervasyonu önler)
curl -u admin:admin123 -H 'Content-Type: application/json' -H 'Idempotency-Key: buy-thyao-001' \
  -d '{"customerId":"customer-1","assetName":"THYAO","orderSide":"BUY","size":10,"price":250.50}' \
  http://localhost:8080/api/v1/orders

# Emri getir
curl -u admin:admin123 http://localhost:8080/api/v1/orders/1

# Emirleri listele
curl -u admin:admin123 \
  'http://localhost:8080/api/v1/orders?customerId=customer-1&startDate=2026-01-01T00:00:00Z&endDate=2026-12-31T23:59:59Z'

# İptal (1 yerine dönen id’yi koyun)
curl -u admin:admin123 -X DELETE http://localhost:8080/api/v1/orders/1

# Varlıkları listele
curl -u admin:admin123 'http://localhost:8080/api/v1/assets?customerId=customer-1'

# Defter
curl -u admin:admin123 'http://localhost:8080/api/v1/ledger?customerId=customer-1'

# Eşleştir (admin)
curl -u admin:admin123 -H 'Content-Type: application/json' \
  -d '{"orderIds":[1]}' \
  http://localhost:8080/api/v1/admin/orders/match

# Müşteri (yalnızca kendi verisi)
curl -u customer-1:customer123 'http://localhost:8080/api/v1/assets?customerId=customer-1'
```

## Mimari

Katmanlı Spring Boot uygulaması (`com.nakitera.brokerage`):

- `controller` — yalnızca HTTP eşlemesi
- `dto` — istek/yanıt record’ları; entity dışarı açılmaz
- `service` — transactional use case’ler, yetkilendirme, pessimistic asset kilitleri, optimistic-lock retry
- `domain` — `Asset`, `Order`, `BalanceLedger`, enum’lar, `BigDecimal` yardımcıları
- `repository` — Spring Data JPA
- `exception` / `config` — hata modeli ve HTTP Basic güvenlik

## Bakiye rezervasyonu ve eşleştirme

- **BUY oluşturma:** `requiredTRY = size * price` (ölçek 6, `HALF_UP`). `TRY.usableSize >= requiredTRY` gerekir. Yalnızca `TRY.usableSize` düşülür. Emir durumu `PENDING`.
- **SELL oluşturma:** hisse `usableSize >= size` gerekir. Miktar o varlığın `usableSize` değerinden düşülür.
- **PENDING BUY/SELL iptali:** rezerve edilen TRY veya hisse `usableSize` aynen iade edilir, durum `CANCELED` olur. Zaten `CANCELED`/`MATCHED` emirler `409` döner ve tekrar iade yapılmaz.
- **BUY eşleştirme:** `requiredTRY` `TRY.size` değerinden düşülür (`usableSize` tekrar düşülmez); alınan hissenin hem `size` hem `usableSize` değerine `size` eklenir (yoksa önce sıfır bakiyeli satır oluşturulur).
- **SELL eşleştirme:** hisse `size` değerinden `size` düşülür; `requiredTRY` müşterinin TRY `size` ve `usableSize` değerine eklenir.
- Eşleştirme emrin kendi fiyatında tam dolumdur. Komisyon, kısmi dolum, order book eşleştirmesi yoktur.
- Eşleştirme batch’i **atomiktir**: eksik veya PENDING olmayan herhangi bir id tüm batch’i geri alır.

Oluşturma / iptal / eşleştirme her biri tek veritabanı transaction’ında çalışır.

### Sayısal örnek (BUY 10 × 250.50)

`customer-1` seed TRY: `size = 100000`, `usableSize = 100000`. `requiredTRY = 2505`.

| Adım | TRY.size | TRY.usableSize | THYAO.size | THYAO.usableSize | Defter |
| --- | --- | --- | --- | --- | --- |
| Başlangıç | 100000 | 100000 | 100 | 100 | — |
| PENDING BUY oluştur | 100000 | 97495 | 100 | 100 | TRY usable −2505 `RESERVE` |
| Eşleştir | 97495 | 97495 | 110 | 110 | TRY size −2505 `MATCH_DEBIT`; THYAO +10/+10 `MATCH_CREDIT` |
| Bunun yerine: PENDING iptal | 100000 | 100000 | 100 | 100 | TRY usable +2505 `RELEASE` |

Oluşturma yalnızca kullanılabilir nakdi rezerve eder. Eşleştirme toplam TRY’yi düşürür ve hisseyi alacaklandırır. İptal `size` alanına dokunmaz.

İsteğe bağlı `Idempotency-Key` başlığı: aynı müşteri + aynı anahtar + aynı gövde orijinal emri (`200`) döndürür ve yeniden rezerve etmez. Aynı anahtarla farklı gövde `409` / `IDEMPOTENCY_KEY_CONFLICT` döner.

## Eşzamanlılık

Bakiye değişiklikleri ilgili `Asset` satırını `SELECT … FOR UPDATE` (pessimistic write) ile kilitler. Aracı kurum bakiyeleri hot row’dur; reserve / release / match’i o satırda serileştirmek doğal uyumdur ve overspend’i retry’a bel bağlamadan önler.

`Asset` ve `Order` ayrıca `@Version` tutar. Emir iptal/eşleştirme `OptimisticLockingFailureException` üzerinde en fazla üç kez dener. Kalan çatışmalar `409` / `CONCURRENT_MODIFICATION` döner. Check constraint’ler `size >= 0`, `usable_size >= 0` ve `usable_size <= size` şartını uygular. `(customerId, assetName)` benzersizdir.

## Varsayımlar (PDF’nin sessiz bıraktığı noktalar)

- Teknik birincil anahtarlar `Long` identity değerleridir (eşleştirme API’si sayısal `orderIds` kullanır).
- Tarih-saat değerleri UTC `Instant` (ISO-8601).
- Kapsayıcı tarih penceresi; sıralama yukarıdaki gibi deterministiktir.
- İptal, iptal edilmiş emri `200` ile döndürür.
- Yetersiz bakiye `409` kullanır (`422` değil).
- Çapraz müşteri erişimi `403` kullanır (gizli `404` değil).
- Gerekli varlık satırı yoksa `404` döner; sessizce oluşturulmaz (BUY eşleştirmede alınan hisse hariç).
- Emirlerdeki varlık adları büyük harfle saklanır; `TRY` işlem gören sembol olarak reddedilir.
- Değerlendirme kullanıcıları bellektedir; H2 bellek içidir ve yeniden başlatınca sıfırlanır.
- Idempotency anahtarları müşteri bazında ve isteğe bağlıdır; başlık yoksa her zaman yeni emir oluşur.

## Ödünler ve sınırlar

- Sayfalama yok (PDF zorunlu tutmaz; değerlendirme listeleri küçük beklenir).
- JWT/OAuth, komisyon, vergi, kurumsal işlem veya çoklu para birimi yok.
- Eşleştirme karşı taraf eşlemez ve borsa fiyatı kullanmaz.
- Bellek içi kullanıcılar ve H2 değerlendirme içindir, production işletimi değil.
- H2 konsolu değerlendirici kolaylığı için kimlik doğrulamasız açıktır.
- Swagger UI kimlik doğrulamasız açıktır; Try it out içinden `/api/**` çağırmak yine Basic auth ister.

## Testler

`./mvnw test` rezervasyon / iptal / eşleştirme aritmetiği için birim testlerini ve H2 üzerinde MockMvc / güvenlik / eşzamanlılık entegrasyon testlerini çalıştırır.
