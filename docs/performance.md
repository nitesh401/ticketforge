# Performance and scaling

## The hot row

Every Tatkal request updates the same `quota_inventory` row. PostgreSQL serialises them on the row lock; throughput ~ 1 / (time the lock is held). Adding app instances does not help beyond the point where the DB row is saturated.

```
thousands of requests -> single inventory row -> serialised commits
```

Practical levers, in the order you would reach for them:

1. **Keep the lock window short.** One statement, short transaction, no remote calls inside it. (In `RailReservationService` the counter update happens first and the inserts follow; a production variant could take the counter in its own tiny transaction and finalise passenger rows afterwards.)
2. **Right-size the connection pool.** More connections than the DB can run just adds queueing. Pool size is a database capacity decision, not a function of users.
3. **Shed load before the DB.** Rate limiting at the edge; bounded queues; fail fast with `429/503` and retry hints.
4. **Waiting room / admission control** (`waitingroom` package). Users receive a ticket, wait in FIFO order, and are admitted at a rate the database sustains, so the DB sees a steady stream instead of a spike.
5. **Early rejection with Redis.** Reject obvious losers cheaply; DB still arbitrates the winners.
6. **Sharded counters / partitioned inventory.** Split 100 berths into per-coach counters (e.g. 5 x 20). Requests hash to a shard and fall back to others when empty. Throughput scales with shards; costs: a request needing several seats may span shards, "exactly N left" needs a sum, and fairness is harder.
7. **Pre-allocation / token buckets in Redis.** Move N inventory tokens into Redis atomics (`DECR`); DB records the winners asynchronously. Fastest, but now Redis durability and reconciliation matter (tokens lost on failover = unsold seats; replays can oversell without a DB check).
8. **Queue-based allocation.** One consumer per inventory partition processes requests sequentially, removing lock contention entirely. Adds latency and an async result protocol.

## Waiting room

```
Client -> Waiting room -> Queue -> Booking workers -> Inventory
```
Reduces contention because only `admitPerSecond` requests per second reach the booking path; everyone else waits in memory/Redis, which is cheap. The demo version is single-JVM; a real one keeps the queue in Redis and signs admission tokens.

## Seat map reads

Advisory and cacheable: serve from read replicas or a short-TTL cache (1-2 s). Staleness is acceptable because the hold endpoint is authoritative.

## Throughput expectations

Treat all numbers as hardware-dependent; run `load-tests/tatkal.js` and compare p50/p95/p99 and conflict rate on your machine. Measure DB-side: `pg_stat_statements` (mean time of the UPDATE), `pg_locks` waits, pool wait time (Hikari metrics).

## 1 million concurrent requests

Edge rate limiting and CDN absorb most; waiting room admits at DB-sustainable rate; inventory is sharded and/or tokenised; stateless app tier autoscaled; DB sized for the admitted rate rather than the offered rate; clients use idempotency keys and jittered backoff; communicate position/ETA to users so they do not hammer retry.
