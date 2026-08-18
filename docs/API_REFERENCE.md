# Public API Referansı

Bu belge V1 tüketicisinin kullanması beklenen public namespace'leri özetler. Arity ve validation ayrıntılarında kaynak docstring'leri nihai otoritedir. `binance-clj` içindeki transport, pipeline ve validator namespace'leri public uygulama sözleşmesi değildir.

## Ortak Sonuç ve Hata Modeli

Başarılı REST çağrısı:

```clojure
{:ok? true
 :data <endpoint cevabı>
 :metadata {:endpoint-id keyword
            :environment :testnet|:production
            :http-status integer
            :attempts integer
            :rate-limits map}}
```

Hatalar `ExceptionInfo` olarak yükselir. Güvenli `ex-data` içinde `:binance-clj/error` kategorisi bulunur:

```clojure
#{:api :auth :client-closed :configuration :rate-limit
  :timeout :transport :unknown-execution :validation}
```

Binance kaynaklı hatalarda mevcutsa `:binance-code`, allowlist edilmiş `:binance-reason`, `:http-status` ve rate-limit metadata bulunur. Ham signed query, signature ve credential taşınmaz.

## `binance-clj.core`

| Fonksiyon | Açıklama |
|---|---|
| `create-client` | Validate edilmiş connector client oluşturur; ağ bağlantısı açmaz. |
| `execute!` | Registry endpoint'ini pipeline üzerinden çalıştırır; ileri seviye kullanım. |
| `close!` | Client ve sahip olduğu transport/WebSocket kaynaklarını idempotent kapatır. |
| `closed?` | Client kapanma durumunu döndürür. |
| `client-config` | Credential içermeyen config görünümü döndürür. |
| `synchronize-time!` | Binance server time ile default clock offset'ini günceller. |
| `runtime-info` | Phase/status ve runtime metadata döndürür. |

Uygulama endpointleri için çoğunlukla `binance-clj.client` ve `binance-clj.spot` kullanılır.

## `binance-clj.client`

### `create-client`

```clojure
(client/create-client
 {:environment :testnet
  :credentials {:api-key "..." :api-secret "..."}
  :connect-timeout-ms 10000
  :request-timeout-ms 15000
  :recv-window 5000M
  :max-read-retries 2
  :retry-base-delay-ms 200})
```

Önemli config alanları:

| Alan | Default | Not |
|---|---:|---|
| `:environment` | `:testnet` | `:testnet` veya `:production` |
| `:enable-live-trading?` | `false` | Production command için `true` zorunlu |
| `:credentials` | `{}` | `:api-key` + HMAC `:api-secret` veya Ed25519 `:private-key` |
| `:recv-window` | `5000M` | En fazla `60000`, en çok 3 decimal |
| `:request-timeout-ms` | `15000` | Pozitif integer |
| `:max-read-retries` | `2` | Yalnız safe read; command retry edilmez |
| `:time-unit` | `:millisecond` | `:millisecond` veya `:microsecond` |

`client/public-config`, `client/close!`, `client/closed?`, `client/synchronize-time!` ve `client/next-client-order-id` desteklenen lifecycle yardımcılarıdır.

## `binance-clj.spot` — Public REST

| Fonksiyon | Örnek | Binance alanı |
|---|---|---|
| `ping` | `(spot/ping c)` | Bağlantı testi |
| `server-time` | `(spot/server-time c)` | Server timestamp |
| `exchange-info` | `(spot/exchange-info c {:symbol "BTCUSDT"})` | Symbol/filter/rate limit |
| `ticker-price` | `(spot/ticker-price c "BTCUSDT")` | Son fiyat |
| `ticker-24h` | `(spot/ticker-24h c {:symbol "BTCUSDT" :type :mini})` | 24 saat ticker |
| `book-ticker` | `(spot/book-ticker c "BTCUSDT")` | Best bid/ask |
| `depth` | `(spot/depth c "BTCUSDT" {:limit 100})` | Order-book snapshot |

Multi-symbol desteklenen çağrılarda sembol vector veya `{:symbols [...]}` kullanılabilir. Paramsız all-market çağrıları yüksek request weight tüketebilir.

## `binance-clj.spot` — Signed Account

| Fonksiyon | Temel parametreler |
|---|---|
| `account` | Opsiyonel `{:omit-zero-balances? true}` |
| `my-trades` | `{:symbol "BTCUSDT"}`, opsiyonel ID/time/limit |
| `query-order` | `:symbol` + `:order-id` veya `:original-client-order-id` |
| `open-orders` | Opsiyonel symbol; symbolsüz çağrı daha pahalıdır |

Signed çağrıdan önce `client/synchronize-time!` önerilir.

## `binance-clj.spot` — Trading

### `validate-order`

Saf yerel validation yapar; ağ çağrısı yoktur:

```clojure
(spot/validate-order symbol-info order)
(spot/validate-order symbol-info market-order {:reference-price 65000M})
```

### `test-order`

Yerel validation ve non-executing Binance `order/test` çağrısı yapar:

```clojure
(spot/test-order connector symbol-info order)
```

### `new-order`

Gerçek state-changing command. Network belirsizliğinde `:unknown-execution` exception üretebilir; otomatik retry yoktur.

### `submit-order!`

Önerilen yüksek seviye API. Tek submit + bounded query reconciliation:

```clojure
(spot/submit-order! connector symbol-info order
                    {:reconciliation-policy
                     {:max-query-attempts 5
                      :query-delay-ms 250
                      :max-query-delay-ms 2000}})
```

Lifecycle map ana alanları:

- `:state`
- `:resolution`
- `:client-order-id`
- `:symbol`
- `:submit-attempts`
- `:query-attempts`
- `:result` veya `:order`
- Secret-safe `:events`/`:error`

### `cancel-order`

```clojure
(spot/cancel-order connector {:symbol "BTCUSDT" :order-id 123})
```

Cancel command da otomatik retry edilmez.

## Order Map Sözleşmesi

LIMIT:

```clojure
{:symbol "BTCUSDT"
 :side :buy|:sell
 :type :limit
 :time-in-force :gtc|:ioc|:fok
 :quantity 0.001M
 :price 10000M
 :new-client-order-id "opsiyonel-unique-id"}
```

MARKET:

```clojure
{:symbol "BTCUSDT"
 :side :buy|:sell
 :type :market
 :quantity 0.001M}
```

veya desteklenen sembolde:

```clojure
{:symbol "BTCUSDT"
 :side :buy
 :type :market
 :quote-order-qty 10M}
```

Finansal değerler `BigDecimal`, integer veya strict plain decimal string olabilir; `float`/`double`, exponent notation ve sessiz rounding kabul edilmez.

## `binance-clj.spot.streams`

Stream name yardımcıları:

| Fonksiyon | Sonuç örneği |
|---|---|
| `(all-mini-tickers)` | `!miniTicker@arr` |
| `(ticker "BTCUSDT")` | `btcusdt@ticker` |
| `(book-ticker "BTCUSDT")` | `btcusdt@bookTicker` |
| `(partial-depth "BTCUSDT" 20 100)` | `btcusdt@depth20@100ms` |

Lifecycle:

```clojure
(def s (streams/create-stream connector options))
(streams/subscribe! s stream-name)
(streams/connect! s)
(streams/poll-event! s 1000)
(streams/snapshot s)
(streams/renew! s)
(streams/unsubscribe! s stream-name)
(streams/close! s)
```

Options içinde `:buffer-capacity`, `:overflow-policy`, reconnect policy ve test boundary'leri bulunabilir. Uygulama kodu varsayılanlarla başlayıp snapshot metriklerini izlemelidir.

## `binance-clj.spot.user-stream`

```clojure
(def uds (user-stream/create-stream connector))
(user-stream/connect! uds)
(user-stream/poll-event! uds 1000)
(user-stream/snapshot uds)
(user-stream/renew! uds)
(user-stream/unsubscribe! uds)
(user-stream/subscribe! uds)
(user-stream/close! uds)
```

`user-stream/reconcile-order` matching `executionReport` eventini unknown/unresolved lifecycle'a uygular.

## Decimal ve Encoding Yardımcıları

`binance-clj.decimal`:

- `parse`: exact decimal parse
- `plain-string`: scientific notation olmadan wire metni
- `normalize`: değeri değiştirmeden redundant trailing-zero scale temizleme

`binance-clj.encoding` düşük seviye canonical query/payload araçlarıdır. Signed uygulama akışında doğrudan kullanmak yerine client pipeline tercih edilmelidir.

## Compatibility ve Sınırlar

Eksiksiz endpoint/weight/security tablosu [BINANCE_ENDPOINT_MATRIX.md](BINANCE_ENDPOINT_MATRIX.md), bilinmeyen/uygulanmayan davranışlar [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) içindedir. Binance sunucu sözleşmesi değişebilir; release öncesi [BINANCE_VERSION.md](BINANCE_VERSION.md) protokolü izlenmelidir.
