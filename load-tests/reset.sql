-- Reset the Tatkal demo state between load-test runs.
DELETE FROM rail_passengers;
DELETE FROM rail_bookings;
DELETE FROM idempotency_keys;
UPDATE quota_inventory SET available_count = total_capacity, version = 0;
