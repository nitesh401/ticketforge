package com.ticketforge.railway.service;

import com.ticketforge.railway.domain.QuotaType;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * DELIBERATELY BROKEN. Exists only so tests can show the lost-update race:
 *
 *   read count -> (pause) -> write count - n
 *
 * Every request that read "100" before anyone wrote believes it succeeded, so many more than 100
 * allocations are "granted". Each statement runs in autocommit mode with no row lock held across the
 * pause, which is exactly what a check-then-act bug looks like in production code.
 * Enabled only with ticketforge.demo.unsafe-inventory-enabled=true.
 */
@Service
@ConditionalOnProperty(name = "ticketforge.demo.unsafe-inventory-enabled", havingValue = "true")
public class UnsafeInventoryService implements InventoryAllocator {
    private final JdbcTemplate jdbc;
    private volatile long pauseMillis = 20;
    private final AtomicLong grants = new AtomicLong();

    public UnsafeInventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void setPauseMillis(long pauseMillis) {
        this.pauseMillis = pauseMillis;
    }

    public long grants() {
        return grants.get();
    }

    @Override
    public boolean tryAllocate(long scheduleId, QuotaType quota, int requested) {
        Integer available = jdbc.queryForObject(
                "SELECT available_count FROM quota_inventory WHERE train_schedule_id = ? AND quota_type = ?",
                Integer.class, scheduleId, quota.name());
        if (available == null || available < requested) {
            return false;
        }
        pause();
        // Absolute write based on a stale read: this is the lost update.
        jdbc.update("UPDATE quota_inventory SET available_count = ? WHERE train_schedule_id = ? AND quota_type = ?",
                available - requested, scheduleId, quota.name());
        grants.incrementAndGet();
        return true;
    }

    private void pause() {
        try {
            Thread.sleep(pauseMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
