# Load tests

Requires [k6](https://k6.io/docs/get-started/installation/).

```bash
docker compose up -d postgres redis kafka
set -a; source .env; set +a
./mvnw spring-boot:run &                       # or: docker compose up --build

psql "postgresql://$DB_USERNAME:$DB_PASSWORD@localhost:5432/${DB_NAME:-ticketforge}" -f load-tests/reset.sql
k6 run -e BASE_URL=http://localhost:8080 load-tests/tatkal.js
```

Scenario: 10,000 reservation attempts, 500 virtual users, 100 berths. Expected: exactly 100 `201`, the rest `409 INSUFFICIENT_INVENTORY`, zero 5xx.

Reported: RPS, p50/p95/p99 latency, success and conflict counts. DB latency comes from the database side (`pg_stat_statements`, `pg_stat_activity`) and the Hikari/HTTP metrics at `/actuator/prometheus`.

Verify afterwards:
```sql
SELECT available_count FROM quota_inventory;                         -- 0
SELECT count(*), count(DISTINCT berth_id) FROM rail_passengers;      -- 100, 100
```
To try the waiting room, start with `TICKETFORGE_WAITING_ROOM_ENABLED=true` and add the join/admit step to the script.
