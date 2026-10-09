package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.QuotaInventory;
import com.ticketforge.railway.domain.QuotaType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotaInventoryRepository extends JpaRepository<QuotaInventory, Long> {

    Optional<QuotaInventory> findByTrainScheduleIdAndQuotaType(Long trainScheduleId, QuotaType quotaType);

    /**
     * THE most important statement in the project. The WHERE guard and the decrement are one atomic
     * operation under the row lock: 10,000 concurrent callers are serialised by the database, each sees
     * the up-to-date count, and available_count can never go below zero. rows == 1 means allocated.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update QuotaInventory q
               set q.availableCount = q.availableCount - :requested,
                   q.version = q.version + 1
             where q.trainScheduleId = :scheduleId
               and q.quotaType = :quota
               and q.availableCount >= :requested
            """)
    int consume(@Param("scheduleId") Long scheduleId, @Param("quota") QuotaType quota,
                @Param("requested") int requested);

    @Query("select q.availableCount from QuotaInventory q where q.trainScheduleId = :scheduleId and q.quotaType = :quota")
    Optional<Integer> findAvailable(@Param("scheduleId") Long scheduleId, @Param("quota") QuotaType quota);

    default boolean tryConsume(Long scheduleId, QuotaType quota, int requested) {
        return consume(scheduleId, quota, requested) == 1;
    }

    /** Hands inventory back (cancellation). The upper bound keeps the counter within total_capacity. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update QuotaInventory q
               set q.availableCount = q.availableCount + :count, q.version = q.version + 1
             where q.trainScheduleId = :scheduleId and q.quotaType = :quota
               and q.availableCount + :count <= q.totalCapacity
            """)
    int restore(@Param("scheduleId") Long scheduleId, @Param("quota") QuotaType quota, @Param("count") int count);
}
