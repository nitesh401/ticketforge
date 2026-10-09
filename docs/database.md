# Database design

PostgreSQL, Flyway migrations `V1` (movie + shared), `V2` (railway), `V3` (seed data).

## Tables

Movie: `users`, `movies`, `theatres`, `screens`, `seats`, `shows`, `show_seats`, `holds`, `hold_items`, `bookings`, `booking_items`, `payments`, `idempotency_keys`, `processed_events`.
Railway: `trains`, `train_schedules`, `coaches`, `berths`, `quota_inventory`, `rail_bookings`, `rail_passengers`.

`user_id` columns are intentionally not foreign keys: identities come from an external IdP and must not add lock/validation cost to the hot path. `users` is a local cache used by the dev token endpoint.

## Constraints and why

| Constraint | Why |
|---|---|
| `show_seats UNIQUE(show_id, seat_id)` | one inventory row per seat per show; also the lookup key |
| `ck_show_seats_state` | `AVAILABLE` => no hold; `HELD` => hold + expiry; `BOOKED` => hold. Prevents impossible rows even if code is wrong |
| `bookings.hold_id UNIQUE` | at most one booking per hold, even under duplicate requests |
| `payments.booking_id UNIQUE`, `provider_ref UNIQUE` | one payment per booking; callback lookup key |
| `idempotency_keys UNIQUE(user_id, idempotency_key)` | the atomic claim for retry-safety |
| `quota_inventory UNIQUE(train_schedule_id, quota_type)` | exactly one counter row per quota |
| `ck_quota_available` | `0 <= available_count <= total_capacity`: no negative stock, no over-restore |
| `rail_passengers UNIQUE(train_schedule_id, berth_id)` | a berth is allocated once per journey |
| status CHECKs | reject unknown states |

## Indexes

| Index | Query it serves |
|---|---|
| `uq_show_seats_show_seat (show_id, seat_id)` | resolving labels to rows for a show |
| `idx_show_seats_show_status (show_id, status)` | seat map, "how many available" |
| `idx_show_seats_hold (hold_id) WHERE hold_id IS NOT NULL` | confirm/release by hold; partial keeps it small |
| `idx_show_seats_held_expiry (hold_expires_at) WHERE status='HELD'` | sweeper: only live holds are indexed |
| `idx_holds_active_expires (expires_at) WHERE status='ACTIVE'` | sweeper over holds |
| `idx_holds_user (user_id)` | user's holds |
| `idx_booking_items_booking (booking_id)` | load a booking's seats |
| `idx_bookings_user (user_id, created_at DESC)` | "my bookings" |
| `idx_payments_status ... WHERE status='REFUND_REQUIRED'` | reconciliation queue |
| `idx_idempotency_created (created_at)` | TTL cleanup of old keys |
| `uq_quota_schedule_type` | the hot-row lookup |
| `idx_berths_coach_ordinal`, `uq_berths_coach_number` | berth lookup by train + ordinal |

## Versioning

`version` columns exist on `show_seats`, `holds`, `bookings`, `payments` (JPA `@Version`) and `quota_inventory` (incremented by the atomic UPDATE, observable but not used for locking). Bulk UPDATEs bump `version` explicitly so entity-based writers detect them.

## Operational notes

* `idempotency_keys` and `processed_events` grow forever: add a retention job (for example delete older than 24-72 h).
* Keep `fillfactor` lower on `show_seats` / `quota_inventory` for HOT updates if the tables are update-heavy.
* Monitor `pg_stat_activity` lock waits and `pg_stat_database.deadlocks`.
