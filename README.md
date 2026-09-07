**Language:** English · [Türkçe](README.tr.md)

# Nakitera Brokerage Backend

Production-style Spring Boot REST API for brokerage employees (and customers) to create, list, cancel, and match stock orders. TRY is modeled as a normal `Asset` row. Order creation reserves usable balance; cancellation releases it; matching (admin) settles totals.

## Scope

Mandatory:

- HTTP Basic authentication on every `/api/**` endpoint
- BUY/SELL order creation with atomic reservation
- Order listing by customer and inclusive date range
- PENDING-only cancellation with a single reservation release
- Customer asset listing
- Relational persistence (H2)
- Unit and integration tests for core rules

Bonus (implemented):

- Customer authentication and customer-scoped authorization
- Admin-only batch order matching with atomic settlement

## Versions and prerequisites

- JDK 21 or later (the bytecode target is 21; tests also run on JDK 24/25)
- Maven Wrapper (`./mvnw`); a local Maven install is not required
- No `JAVA_HOME` export is required if `java -version` is 21+
- No external database is required

## Build, test, and run

From the repository root (clone, then run — do not set extra environment variables):

```bash
./mvnw test
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`.

## Swagger UI

Interactive docs with filled example requests:

- UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

The Swagger UI and OpenAPI spec are open without authentication. Click **Authorize**, enter `admin` / `admin123` (or a customer user), then use **Try it out**. Create-order has BUY and SELL examples; match has a sample `orderIds` body; list/cancel/assets have example query and path values.

## Authentication (evaluation credentials)

Users are in-memory (not a production identity store). Passwords can be overridden with environment variables if you bind Spring properties.

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `admin123` | `ADMIN` — all customers, including matching |
| `customer-1` | `customer123` | `CUSTOMER` — only `customer-1` |
| `customer-2` | `customer123` | `CUSTOMER` — only `customer-2` |

Override examples:

```bash
NAKITERA_SECURITY_ADMIN_PASSWORD=admin123 ./mvnw spring-boot:run
```

Spring relaxed binding: `nakitera.security.admin.password`.

A customer who sends another customer's `customerId` receives `403` / `ACCESS_DENIED`. The authenticated identity is the authority, not the client-supplied field. Cross-customer access is never applied.

## Database and H2 console

- JDBC URL: `jdbc:h2:mem:nakitera;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Username: `sa`
- Password: empty
- Hibernate `ddl-auto=update`
- Console: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (permitted without Basic auth for local evaluation)

JDBC driver class: `org.h2.Driver`. In the console, use the JDBC URL above.

## Seed data

On startup (non-`test` profile) the following asset rows are created if missing:

| customerId | assetName | size | usableSize |
| --- | --- | --- | --- |
| customer-1 | TRY | 100000 | 100000 |
| customer-1 | THYAO | 100 | 100 |
| customer-1 | ASELS | 50 | 50 |
| customer-2 | TRY | 50000 | 50000 |
| customer-2 | THYAO | 20 | 20 |

There is no public “create asset” API. Evaluators can add rows through the H2 console if they need extra customers or symbols.

## API

Base path: `/api/v1`. JSON request and response bodies.

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| POST | `/api/v1/orders` | admin or owning customer | `201 Created` (`200` on idempotent replay) |
| GET | `/api/v1/orders/{orderId}` | admin or owning customer | `200 OK` |
| GET | `/api/v1/orders?customerId&startDate&endDate` | admin or owning customer | `200 OK` |
| DELETE | `/api/v1/orders/{orderId}` | admin or owning customer | `200 OK` (row kept, status `CANCELED`) |
| GET | `/api/v1/assets?customerId` | admin or owning customer | `200 OK` |
| GET | `/api/v1/ledger?customerId` | admin or owning customer | `200 OK` |
| POST | `/api/v1/admin/orders/match` | admin only | `200 OK` |

Optional list filters: `status`, `orderSide`, `assetName`.

Date range: inclusive `startDate` and inclusive `endDate` on server-generated `createDate`. Ordering: `createDate DESC`, `id DESC`. `startDate > endDate` → `400` / `INVALID_DATE_RANGE`.

Insufficient usable balance and non-PENDING cancel/match → `409 Conflict`.

Error body:

```json
{
  "timestamp": "2026-05-05T12:00:00Z",
  "status": 409,
  "code": "INSUFFICIENT_USABLE_BALANCE",
  "message": "Customer does not have sufficient usable TRY balance",
  "path": "/api/v1/orders"
}
```

Stable codes: `VALIDATION_ERROR`, `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `ORDER_NOT_FOUND`, `ASSET_NOT_FOUND`, `INSUFFICIENT_USABLE_BALANCE`, `ORDER_NOT_PENDING`, `INVALID_DATE_RANGE`, `CONCURRENT_MODIFICATION`, `IDEMPOTENCY_KEY_CONFLICT`.

### Sample cURL

```bash
# Create BUY (optional Idempotency-Key prevents double reservation on retry)
curl -u admin:admin123 -H 'Content-Type: application/json' -H 'Idempotency-Key: buy-thyao-001' \
  -d '{"customerId":"customer-1","assetName":"THYAO","orderSide":"BUY","size":10,"price":250.50}' \
  http://localhost:8080/api/v1/orders

# Get order
curl -u admin:admin123 http://localhost:8080/api/v1/orders/1

# List orders
curl -u admin:admin123 \
  'http://localhost:8080/api/v1/orders?customerId=customer-1&startDate=2026-01-01T00:00:00Z&endDate=2026-12-31T23:59:59Z'

# Cancel (replace 1 with the returned id)
curl -u admin:admin123 -X DELETE http://localhost:8080/api/v1/orders/1

# List assets
curl -u admin:admin123 'http://localhost:8080/api/v1/assets?customerId=customer-1'

# Ledger
curl -u admin:admin123 'http://localhost:8080/api/v1/ledger?customerId=customer-1'

# Match (admin)
curl -u admin:admin123 -H 'Content-Type: application/json' \
  -d '{"orderIds":[1]}' \
  http://localhost:8080/api/v1/admin/orders/match

# Customer (own data only)
curl -u customer-1:customer123 'http://localhost:8080/api/v1/assets?customerId=customer-1'
```

## Architecture

Layered Spring Boot application (`com.nakitera.brokerage`):

- `controller` — HTTP mapping only
- `dto` — request/response records; entities are not exposed
- `service` — transactional use cases, authorization, pessimistic asset locks, optimistic-lock retry
- `domain` — `Asset`, `Order`, `BalanceLedger`, enums, `BigDecimal` helpers
- `repository` — Spring Data JPA
- `exception` / `config` — error model and HTTP Basic security

## Balance reservation and matching

- **BUY create:** `requiredTRY = size * price` (scale 6, `HALF_UP`). Requires `TRY.usableSize >= requiredTRY`. Subtracts from `TRY.usableSize` only. Order status `PENDING`.
- **SELL create:** requires stock `usableSize >= size`. Subtracts quantity from that asset's `usableSize`.
- **Cancel PENDING BUY/SELL:** restores the exact reserved TRY or stock `usableSize` and sets `CANCELED`. Already `CANCELED`/`MATCHED` orders return `409` and do not release again.
- **Match BUY:** subtract `requiredTRY` from `TRY.size` (not from `usableSize` again); credit `size` to both `size` and `usableSize` of the stock asset (created at zero if missing).
- **Match SELL:** subtract `size` from stock `size`; credit `requiredTRY` to TRY `size` and `usableSize`.
- Matching is full fill at the order's own price. No fees, no partial fills, no order book pairing.
- A match batch is **atomic**: any missing or non-PENDING id rolls back the entire batch.

Create/cancel/match each run in a single database transaction.

### Worked example (BUY 10 × 250.50)

Seed TRY for `customer-1`: `size = 100000`, `usableSize = 100000`. `requiredTRY = 2505`.

| Step | TRY.size | TRY.usableSize | THYAO.size | THYAO.usableSize | Ledger |
| --- | --- | --- | --- | --- | --- |
| Start | 100000 | 100000 | 100 | 100 | — |
| Create PENDING BUY | 100000 | 97495 | 100 | 100 | TRY usable −2505 `RESERVE` |
| Match | 97495 | 97495 | 110 | 110 | TRY size −2505 `MATCH_DEBIT`; THYAO +10/+10 `MATCH_CREDIT` |
| Instead: cancel PENDING | 100000 | 100000 | 100 | 100 | TRY usable +2505 `RELEASE` |

Create reserves usable cash only. Match then reduces total TRY and credits the stock. Cancel never touches `size`.

Optional header `Idempotency-Key`: the same customer + key + body returns the original order (`200`) and does not reserve again. A different body with the same key returns `409` / `IDEMPOTENCY_KEY_CONFLICT`.

## Concurrency

Balance mutations lock the relevant `Asset` row with `SELECT … FOR UPDATE` (pessimistic write). Brokerage balances are hot rows; serializing reserve/release/match on that row is the natural fit and prevents overspend without relying on retries.

`Asset` and `Order` also keep `@Version`. Order cancel/match still retry up to three times on `OptimisticLockingFailureException`. Remaining conflicts return `409` / `CONCURRENT_MODIFICATION`. Check constraints enforce `size >= 0`, `usable_size >= 0`, and `usable_size <= size`. `(customerId, assetName)` is unique.

## Assumptions (PDF silent items)

- Technical primary keys are `Long` identity values (match API uses numeric `orderIds`).
- Date-time values are UTC `Instant` (ISO-8601).
- Inclusive date window; deterministic sort as above.
- Cancel returns `200` with the canceled order.
- Insufficient funds use `409` (not `422`).
- Cross-customer access uses `403` (not hidden `404`).
- Missing required asset rows return `404` rather than auto-creating them (except purchased stock on BUY match).
- Asset names on orders are stored uppercased; `TRY` is rejected as a traded symbol.
- Evaluation users live in memory; H2 is in-memory and resets on restart.
- Idempotency keys are scoped per customer and optional; missing header always creates a new order.

## Trade-offs and limitations

- No pagination (not required; lists are expected to stay small in evaluation).
- No JWT/OAuth, commissions, taxes, corporate actions, or multi-currency.
- Matching does not pair counterparties or use an exchange price.
- In-memory users and H2 are for evaluation, not production operations.
- H2 console is open without authentication for evaluator convenience.
- Swagger UI is open without authentication; calling `/api/**` from Try it out still requires Basic auth.

## Tests

`./mvnw test` runs unit tests for reservation/cancel/match arithmetic and MockMvc/security/concurrency integration tests against H2.
