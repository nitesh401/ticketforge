package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The hot row. Mutated only through QuotaInventoryRepository.tryConsume (atomic conditional UPDATE),
 * never by read-modify-write on this entity, hence no setters.
 */
@Entity
@Table(name = "quota_inventory")
public class QuotaInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_schedule_id")
    private Long trainScheduleId;
    @Enumerated(EnumType.STRING)
    @Column(name = "quota_type")
    private QuotaType quotaType;
    @Column(name = "total_capacity")
    private int totalCapacity;
    @Column(name = "available_count")
    private int availableCount;
    private long version;

    protected QuotaInventory() {
    }

    public Long getId() { return id; }
    public Long getTrainScheduleId() { return trainScheduleId; }
    public QuotaType getQuotaType() { return quotaType; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getAvailableCount() { return availableCount; }
}
