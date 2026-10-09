package com.ticketforge.railway.service;

import com.ticketforge.common.TicketMetrics;
import com.ticketforge.railway.domain.QuotaType;
import com.ticketforge.railway.repository.QuotaInventoryRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correct implementation: the availability check and the decrement are one SQL statement.
 * No Java-side "if (available >= n)" exists, so there is no window for another request to interleave.
 */
@Service
@Primary
public class SafeInventoryService implements InventoryAllocator {
    private final QuotaInventoryRepository inventory;
    private final TicketMetrics metrics;

    public SafeInventoryService(QuotaInventoryRepository inventory, TicketMetrics metrics) {
        this.inventory = inventory;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public boolean tryAllocate(long scheduleId, QuotaType quota, int requested) {
        boolean allocated = inventory.tryConsume(scheduleId, quota, requested);
        if (allocated) {
            metrics.inventorySuccess();
        } else {
            metrics.inventoryConflict();
        }
        return allocated;
    }
}
