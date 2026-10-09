// k6 load test: 10,000 users compete for the 100-berth Tatkal quota of train 12951 at T=0.
//   k6 run -e BASE_URL=http://localhost:8080 load-tests/tatkal.js
// Reset inventory between runs: psql ... -f load-tests/reset.sql
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 10000);

const success = new Counter('reservations_success');
const conflict = new Counter('reservations_conflict');
const unexpected = new Counter('reservations_unexpected');
const successRate = new Rate('success_rate');

export const options = {
  scenarios: {
    tatkal_opens: {
      executor: 'shared-iterations',   // all users fire as fast as the VUs allow = "T=0 stampede"
      vus: Number(__ENV.VUS || 500),
      iterations: USERS,
      maxDuration: '5m',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],     // 409s are expected and are NOT counted as failures (see responseCallback)
    http_req_duration: ['p(95)<3000'],
    reservations_unexpected: ['count==0'],
  },
};

http.setResponseCallback(http.expectedStatuses(201, 409));

let token;  // one demo token per VU

function tokenFor(vu) {
  if (!token) {
    const res = http.post(`${BASE}/api/v1/auth/token`, JSON.stringify({ userId: `load-user-${vu}` }),
      { headers: { 'Content-Type': 'application/json' } });
    token = res.json('token');
  }
  return token;
}

export default function () {
  const body = JSON.stringify({
    trainNo: '12951',
    journeyDate: '2026-11-15',
    passengers: [{ name: `User ${__VU}-${__ITER}`, age: 30 }],
  });
  const res = http.post(`${BASE}/api/v1/tatkal/reservations`, body, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${tokenFor(__VU)}`,
      'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}`,
    },
  });
  if (res.status === 201) { success.add(1); successRate.add(1); }
  else if (res.status === 409) { conflict.add(1); successRate.add(0); }
  else { unexpected.add(1); successRate.add(0); }
  check(res, { 'status is 201 or 409': (r) => r.status === 201 || r.status === 409 });
}

export function handleSummary(data) {
  const m = data.metrics;
  const out = {
    requests: m.http_reqs.values.count,
    rps: m.http_reqs.values.rate,
    p50_ms: m.http_req_duration.values.med,
    p95_ms: m.http_req_duration.values['p(95)'],
    p99_ms: m.http_req_duration.values['p(99)'],
    success: m.reservations_success ? m.reservations_success.values.count : 0,
    conflicts: m.reservations_conflict ? m.reservations_conflict.values.count : 0,
    unexpected: m.reservations_unexpected ? m.reservations_unexpected.values.count : 0,
    note: 'Expected: success == 100 exactly. DB latency: see pg_stat_statements / Hikari metrics at /actuator/prometheus.',
  };
  return { stdout: JSON.stringify(out, null, 2) + '\n' };
}
