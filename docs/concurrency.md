# Concurrency

## 1. Why check-then-update is unsafe

```
A: SELECT status -> AVAILABLE
B: SELECT status -> AVAILABLE
A: UPDATE status = HELD   (A thinks it won)
B: UPDATE status = HELD   (B thinks it won)
```
Two reads, two writes, no coordination between them. Wrapping this in `@Transactional` at READ COMMITTED does not fix it: both transactions read the same committed value.

## 2. Mechanism A - pessimistic (`PessimisticSeatAcquisition`)

`SELECT ... FOR UPDATE ... ORDER BY id` locks every requested row. The second transaction blocks on the first locked row; when the first commits, the blocked `SELECT` returns the *new* committed row (`HELD`) and the check fails. Pros: obvious semantics, lets you run arbitrary checks. Cons: waiters queue, lock held across application logic, deadlocks if order differs.

## 3. Mechanism B - atomic UPDATE (`AtomicSeatAcquisition`)

```sql
UPDATE show_seats SET status='HELD', hold_id=?, hold_expires_at=?
 WHERE id=? AND (status='AVAILABLE' OR (status='HELD' AND hold_expires_at < ?))
```
`rowsUpdated == 1` wins. A concurrent UPDATE waits for the row lock, then PostgreSQL re-evaluates the `WHERE` against the updated row (EvalPlanQual) and matches 0 rows. No read in the application, no window.

## 4. Optimistic locking

`@Version` columns detect lost updates on entities modified read-modify-write. Appropriate when conflicts are rare. Not used for seat acquisition because contention is the common case there.

## 5. Multi-seat all-or-nothing and lock ordering

All seats of a hold are acquired in one transaction. The first failure throws `SeatAlreadyHeldException`, which rolls the transaction back, undoing any seat already updated in the loop. Ids are sorted ascending first. Example: A asks `A1,A2`, B asks `A2,A1`; unsorted, A holds A1 waiting for A2 while B holds A2 waiting for A1 (deadlock, PostgreSQL aborts one with `40P01`). Sorted, both go A1 then A2; one waits, none cycle.

## 6. Retry of transient failures

`TransactionalRunner` catches `PessimisticLockingFailureException` (parent of `DeadlockLoserDataAccessException`, `CannotAcquireLockException`, `CannotSerializeTransactionException`) and `TransientDataAccessException`, up to `ticketforge.retry.max-attempts`, with exponential backoff and jitter. The retry wraps the *whole* transaction because a transaction aborted by deadlock detection is gone. `SeatAlreadyHeldException` and every other `TicketForgeException` propagate immediately: retrying "seat taken" cannot succeed.

## 7. Isolation levels

* **READ COMMITTED** (used): single-statement atomic updates and `FOR UPDATE` are correct here; deadlocks only on lock-order mistakes.
* **REPEATABLE READ**: transaction-wide snapshot; updating a row changed since the snapshot raises a serialization failure (`40001`). More retries, fewer anomalies for multi-statement reads.
* **SERIALIZABLE**: PostgreSQL SSI aborts transactions whose read/write dependencies could form a cycle. Strongest, but: more aborts, mandatory retry loops, higher latency under contention, and false positives. Our invariants are single-row, so it buys nothing.

## 8. Hold expiry vs payment confirmation

Both are conditional statements on the same rows:
* expiry: `... WHERE status='HELD' AND hold_expires_at < now`
* confirm: lock the hold's seats, require `HELD`, owner = this hold, `hold_expires_at >= now`, then set `BOOKED`

Whoever gets the row lock first wins; the other re-evaluates and either no-ops (sweeper sees `BOOKED`) or fails validation (confirm sees `AVAILABLE`/another owner) and routes to refund.

## 9. Redis-based expiry alternative

Instead of a polling sweeper, rely on key TTL: `SET seat:{show}:{seat} {holdId} NX EX 300`, and treat a seat as free when its key is gone. Subscribing to keyspace-expiry notifications can trigger DB cleanup, but notifications are best-effort, so a DB reconciliation sweep is still required. Hence the polling sweeper is the default here.

## 10. Why per-JVM locks cannot work

`synchronized(seatId)` or `ReentrantLock` lives in one process's heap. Instance 1 and Instance 2 each own a different lock object for the same seat, so both proceed. Mutual exclusion requires a shared arbiter: the database row lock (strong, durable) or a Redis key (fast, best-effort).

## 11. Multiple instances in tests

All concurrency tests use many threads sharing nothing except the PostgreSQL container. That is equivalent to separate JVMs: any correctness that depends on JVM memory would fail them.
