# Failure scenarios

| # | Scenario | What happens | Why it is safe |
|---|---|---|---|
| 1 | Two users, same seat | one 201, one 409 | atomic transition in DB |
| 2 | App crashes after `holds` insert, before seat update | transaction rolls back | single transaction |
| 3 | App crashes after commit, before response | client times out, retries with same key, gets original response | key claimed in same transaction |
| 4 | Client double-clicks | same key -> replay; no key -> second hold conflicts with first (409) | unique key / row state |
| 5 | Deadlock between multi-seat requests | prevented by sorted locking; if one still occurs the runner retries (bounded) | ordering + retry |
| 6 | Lock timeout / serialization failure | retried with backoff; after max attempts 503 `SERVICE_BUSY` | transient errors only |
| 7 | Payment succeeds after hold expiry | not booked, `REFUND_REQUIRED`, booking `EXPIRED` | confirm re-validates under lock |
| 8 | Payment succeeds after another user took the seat | other user's booking untouched; late payer refunded | ownership check by hold id |
| 9 | Duplicate / conflicting PSP callback | first outcome wins | payment row lock + status check |
| 10 | Payment fails | seats released, booking `PAYMENT_FAILED` | state-conditional release |
| 11 | Sweeper down | lapsed seats still acquirable; confirm still rejects expired holds | validity checks use the clock |
| 12 | Redis down | gate fails open; DB-only | DB authoritative |
| 13 | Redis lock expires mid-request | DB still arbitrates | Redis never grants |
| 14 | Redis holds a stale key after a DB failure | released on error; otherwise TTL (<= hold duration) clears it; worst case a false early 409 | gate only rejects |
| 15 | Kafka down | request succeeds; event send error logged | events are not on the correctness path |
| 16 | Duplicate Kafka delivery | consumer dedupes by event id | `processed_events` in same tx |
| 17 | Poison message | retried with backoff then sent to `<topic>.DLT` | `DefaultErrorHandler` + DLT recoverer |
| 18 | Database failover | in-flight transactions fail and are retried by clients using idempotency keys; durability depends on replication mode | atomic commits |
| 19 | Network partition | only the side reaching the primary can write; others get errors | single-writer authority (CP on the write path) |
| 20 | Sold-out Tatkal | 409 `INSUFFICIENT_INVENTORY`, key row rolled back | conditional decrement |

## Event delivery gap and the outbox

Publishing after commit is a dual write. If the process dies between commit and send, the event is lost. The production fix: insert the event into an `outbox` table inside the business transaction and have a relay (poller or CDC such as Debezium) publish it with retries. Consumers stay idempotent because relays are at-least-once.

## Reconciliation

`payments.status = REFUND_REQUIRED` rows are the work queue for finance/ops (`GET /api/v1/admin/reconciliation/payments`). Alert if the queue grows or ages.
