package com.ticketforge.railway;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketforge.railway.domain.QuotaType;
import com.ticketforge.railway.service.SafeInventoryService;
import com.ticketforge.railway.service.UnsafeInventoryService;
import com.ticketforge.support.AbstractPostgresIT;
import com.ticketforge.support.ConcurrencyHarness;
import com.ticketforge.support.ConcurrencyHarness.Stats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryRaceIT extends AbstractPostgresIT {
    @Autowired SafeInventoryService safe;
    @Autowired UnsafeInventoryService unsafe;

    private long scheduleId() {
        return jdbc.queryForObject("SELECT id FROM train_schedules LIMIT 1", Long.class);
    }

    private int available() {
        return jdbc.queryForObject("SELECT available_count FROM quota_inventory WHERE quota_type = 'TATKAL'", Integer.class);
    }

    @Test
    void unsafeReadSleepWrite_grantsMoreThanCapacity() {
        unsafe.setPauseMillis(50);   // widens the race window so the outcome is reproducible
        long id = scheduleId();

        Stats stats = ConcurrencyHarness.run("UNSAFE read-sleep-write, 100 seats, 1000 requests", 1000,
                i -> unsafe.tryAllocate(id, QuotaType.TATKAL, 1));

        assertThat(stats.errors()).isZero();
        assertThat(stats.success()).as("overselling: more grants than the 100 seats that exist").isGreaterThan(100);
    }

    @Test
    void safeAtomicUpdate_thousandRequests_neverExceedsCapacity() {
        long id = scheduleId();

        Stats stats = ConcurrencyHarness.run("SAFE atomic update, 100 seats, 1000 requests", 1000,
                i -> safe.tryAllocate(id, QuotaType.TATKAL, 1));

        assertThat(stats.errors()).isZero();
        assertThat(stats.success()).isEqualTo(100);
        assertThat(available()).isZero();
    }

    @Test
    void safeAtomicUpdate_tenThousandRequests_neverExceedsCapacity() {
        long id = scheduleId();

        Stats stats = ConcurrencyHarness.run("SAFE atomic update, 100 seats, 10000 requests", 10_000,
                i -> safe.tryAllocate(id, QuotaType.TATKAL, 1));

        assertThat(stats.errors()).as("%s", stats.errorSamples()).isZero();
        assertThat(stats.success()).isEqualTo(100);
        assertThat(stats.rejected()).isEqualTo(9_900);
        assertThat(available()).isZero();            // never negative: ck_quota_available would reject it anyway
    }

    @Test
    void groupRequestsNeverPartiallyAllocate() {
        long id = scheduleId();

        Stats stats = ConcurrencyHarness.run("SAFE groups of 3, 100 seats, 100 requests", 100,
                i -> safe.tryAllocate(id, QuotaType.TATKAL, 3));

        assertThat(stats.success()).isEqualTo(33);   // 33 * 3 = 99, one seat left, no group of 3 fits
        assertThat(available()).isEqualTo(1);
    }
}
