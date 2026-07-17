# Inventory Reservation + Order Saga (v0.1.9)

## Problem
`order-service` charges payment but never touches stock. `product-service` holds a static
`stockQuantity` that is never decremented. Result: unlimited oversell, and no compensation if payment
fails after any side effect. Need atomic stock reservation tied to the order lifecycle, with
compensation on failure and no leaked stock on crash.

## Decisions (approved)
- **Boundary:** extend `product-service` (already owns `stockQuantity`). No new service/DB/migration.
- **Saga:** orchestrated by `order-service` (it already orchestrates payment via gRPC + circuit breaker).
- **Reservation model:** a `reservations` table in `products_db` + a `@Scheduled` TTL sweeper.

## Flow (order-service `placeOrder`)
```
1. save Order PENDING (unchanged)
2. product.Reserve(orderId, items)   gRPC, circuit-breaker "product"
     - insufficient stock -> ReserveResponse.ok=false -> order CANCELLED, throw 409 Conflict
     - product-service down -> breaker fallback -> 503 (order stays PENDING, sweeper frees nothing yet)
3. payment.Charge(...)               (existing)
4a. charge OK   -> product.Confirm(orderId) + confirmAndStage(CONFIRMED) + Rabbit email  (existing)
4b. charge fail -> product.Release(orderId) + order CANCELLED, rethrow
```
Only step 2 is new before charge; steps 4a/4b add one gRPC call each. Reserve/Confirm/Release keyed by
`orderId` so they are idempotent and require no reservation-id plumbing back to order-service.

## product-service changes
**proto** (`proto/src/main/proto/product.proto`) — add to existing `ProductService`:
```
rpc Reserve (ReserveRequest) returns (ReserveResponse);   // items: [{product_id, quantity}]
rpc Confirm (OrderRef)       returns (Ack);
rpc Release (OrderRef)       returns (Ack);
```
`ReserveResponse { bool ok; string reason; }` — ok=false when any line lacks stock (all-or-nothing).

**entity** `Reservation` (table `reservations`): `id(UUID)`, `order_id(UUID)`, `product_id(UUID)`,
`quantity(int)`, `status(PENDING|CONFIRMED|RELEASED)`, `expires_at(Instant)`, `created_at`.
Index on `order_id`; index on `(status, expires_at)` for the sweeper.

**ReservationService** (`@Transactional`):
- `reserve(orderId, lines)`: idempotent (if PENDING/CONFIRMED rows for orderId exist -> return ok). For
  each line: `SELECT ... FOR UPDATE` the product row, check `stockQuantity >= qty`, else roll back whole
  tx -> ok=false. On success: `stockQuantity -= qty` and insert a PENDING reservation row with
  `expires_at = now + RESERVATION_TTL` (default 15m). All lines in one tx = all-or-nothing.
- `confirm(orderId)`: PENDING rows -> CONFIRMED (stock already decremented; no-op if none/already confirmed).
- `release(orderId)`: PENDING rows -> RELEASED, `stockQuantity += qty` (no-op if none/already released;
  CONFIRMED rows are NOT released — payment already settled).
- Product-service also mirrors the new `stockQuantity` into the ES `ProductDocument` on change (reuse the
  existing product->document sync path).

**Sweeper** `ReservationSweeper` `@Scheduled(fixedDelay=60s)`: `release` all PENDING rows past
`expires_at` (reuses the OutboxRelay poller pattern). Guards against crashed/hung sagas leaking stock.

**gRPC impl**: extend existing `ProductGrpcService` with the 3 methods delegating to `ReservationService`.

## order-service changes
- Add `@GrpcClient("product")` blocking stub (config mirrors `grpc.client.payment.*` ->
  `discovery:///PRODUCT-SERVICE`, plaintext). product-service already runs a gRPC server (cart uses it).
- In `placeOrder`: insert Reserve before Charge, Confirm on success, Release in the catch before rethrow.
  Reserve wrapped by `circuitBreakerFactory.create("product")`. Insufficient stock -> new
  `ConflictException` (common, 409). Skip reserve when `request.items` is empty (amount-only orders).
- Metric: `orders.reserved.rejected.total` counter on ok=false.

## Error handling
- Reserve returns ok=false (business, no stock) -> 409, order CANCELLED, no payment attempted.
- product-service unreachable at Reserve -> breaker fallback -> 503, order PENDING (no charge).
- Charge fails after successful Reserve -> Release compensates, order CANCELLED.
- Crash between Reserve and Confirm/Release -> sweeper releases after TTL. At-least-once release is safe
  (RELEASED is idempotent; CONFIRMED rows are never touched by release/sweeper).
- Double Reserve for same orderId (retry) -> idempotent, no double decrement.

## Testing
- **Unit (product-service):** `ReservationServiceTest` — reserve success decrements; insufficient stock
  -> ok=false, no decrement; release restores; confirm keeps decrement; double-reserve idempotent;
  sweeper releases expired PENDING but not CONFIRMED. (One `@SpringBootTest` slice or Mockito on repo.)
- **Integration (order-service):** saga happy path (reserve+charge+confirm), charge-fail path
  (reserve+release, order CANCELLED), no-stock path (409, no charge) — stub product+payment gRPC.
- **Manual E2E (stack up):** seed a product with stockQuantity=1, place 2 concurrent orders -> exactly
  one CONFIRMED, one 409; DB shows stockQuantity=0 and one CONFIRMED + one (no/RELEASED) reservation.

## Out of scope (YAGNI)
Separate inventory-service, multi-warehouse, backorder, partial fulfillment, reservation-id returned to
client. Add if a real requirement appears.

## Version
`v0.1.9`. Bump VERSION; product-service + order-service + proto touched. Follow-on features (own
versions): idempotent orders v0.1.10, KEDA v0.1.11, Schema Registry v0.1.12, Vault/secrets v0.1.13.
