# Interview guide: presenting TicketForge

Say up front: *"This is not how BookMyShow or IRCTC are built internally; it's a reference implementation of the concurrency problems those kinds of systems face."*

## 1. Problem
Many users try to reserve the same limited inventory at the same instant. Requirement: never double-book a seat, never oversell a quota, never lose or duplicate a booking on retries, and handle payment that completes late.

## 2. Constraints
* Many app instances behind a load balancer (no shared JVM memory)
* Bursty traffic (a Tatkal-style opening)
* Payments are asynchronous and can arrive late or twice
* Clients time out and retry
* Seat state must be strongly consistent; views can be stale

## 3. Naive solution
Read seat, `if AVAILABLE` set `HELD`, save. Add `synchronized` for safety.

## 4. Race condition
Draw the A/B interleaving: both read AVAILABLE, both write HELD. `synchronized` only helps inside one JVM, and I have 20 of them.

## 5. Improved solution
Make check+change one atomic statement: `UPDATE ... WHERE status='AVAILABLE'`, rows==1 means you won. Mention the pessimistic alternative (`FOR UPDATE`) and when you'd pick it. Multi-seat: one transaction, sorted ids, all-or-nothing.

## 6. Distributed solution
The database is the arbiter; instances are stateless. Optional Redis `SET NX EX` as an early filter that can only reject. Be explicit that Redis + DB is not a distributed transaction.

## 7. Temporary holds and payments
Hold = `HELD` + expiry (5 min). Confirmation re-checks hold validity and ownership under row locks. Late success becomes `REFUND_REQUIRED`; it never overwrites the new owner. Idempotency keys make booking/payment retries safe; a timeout is not a failure.

## 8. Scaling
Tatkal: atomic decrement on `quota_inventory`. The row is hot, so: short transactions, pool sizing, waiting room, rate limiting, then sharded counters or pre-allocation if one row is not enough. Reads go to replicas/caches.

## 9. Failure handling
Walk the table in docs/failure-scenarios.md: app crash, DB deadlock (retry transient only), Redis down (fail open), Kafka down (outbox), duplicate events (idempotent consumers), sweeper down (lazy expiry).

## 10. Trade-offs to volunteer
Atomic UPDATE vs pessimistic; Redis speed vs extra failure modes; strict expiry vs grace period; after-commit events vs outbox; READ COMMITTED vs SERIALIZABLE (and why not the latter by default).

## 11. Final architecture
Client -> LB -> N stateless Booking Services -> PostgreSQL (authoritative state, idempotency keys) with optional Redis gate and Kafka events; waiting room in front for bursts; sweeper for tidy-up; metrics/alerts on conflict ratio, retries, lock waits, reconciliation backlog.

## Demo script (3 minutes)
1. `./mvnw test -Dtest=InventoryRaceIT` - show Unsafe grants > 100, Safe exactly 100.
2. `./mvnw test -Dtest=SeatContentionIT` - 100 users one seat, exactly one winner.
3. Curl demo: A holds A1 (201), B holds A1 (409).
4. Show `QuotaInventoryRepository.consume` and `AtomicSeatAcquisition`.
5. Show `HoldLifecycleIT.latePaymentFromUserA_neverOverwritesUserBsBooking`.
