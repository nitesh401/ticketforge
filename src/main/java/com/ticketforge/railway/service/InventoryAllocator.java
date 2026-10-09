package com.ticketforge.railway.service;

import com.ticketforge.railway.domain.QuotaType;

public interface InventoryAllocator {
    /** @return true if {@code requested} units were taken from the quota, false if not enough remained */
    boolean tryAllocate(long scheduleId, QuotaType quota, int requested);
}
