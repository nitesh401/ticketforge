package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.SeatStatus;
import com.ticketforge.movie.domain.ShowSeat;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /** Resolves seat labels to show_seat ids, already in ascending id order (the global lock order). */
    @Query("""
            select ss.id from ShowSeat ss, Seat s
             where s.id = ss.seatId and ss.showId = :showId and s.label in :labels
             order by ss.id
            """)
    List<Long> findIdsByShowAndLabels(@Param("showId") Long showId, @Param("labels") Collection<String> labels);

    /** Advisory read: never takes locks, may be stale the moment it returns. */
    @Query("""
            select new com.ticketforge.movie.repository.SeatRow(s.label, ss.status, ss.holdExpiresAt)
              from ShowSeat ss, Seat s
             where s.id = ss.seatId and ss.showId = :showId
             order by ss.id
            """)
    List<SeatRow> findSeatMap(@Param("showId") Long showId);

    // ---------------------------------------------------------------------------------------------
    // Mechanism A: pessimistic locking. SELECT ... FOR UPDATE, ordered by id so that every
    // transaction acquires row locks in the same global order (no lock-order deadlocks).
    // ---------------------------------------------------------------------------------------------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.id in :ids order by ss.id")
    List<ShowSeat> lockAllOrdered(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ss from ShowSeat ss where ss.holdId = :holdId order by ss.id")
    List<ShowSeat> lockByHoldOrdered(@Param("holdId") String holdId);

    // ---------------------------------------------------------------------------------------------
    // Mechanism B: atomic conditional UPDATE. The WHERE clause is the compare-and-set; the database
    // evaluates it under the row lock, so exactly one concurrent caller sees rowsUpdated == 1.
    // ---------------------------------------------------------------------------------------------
    @Modifying(flushAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :heldStatus,
                   s.holdId = :holdId,
                   s.holdExpiresAt = :expiresAt,
                   s.version = s.version + 1,
                   s.updatedAt = :now
             where s.id = :seatId
               and (s.status = :availableStatus
                    or (s.status = :heldStatus and s.holdExpiresAt < :now))
            """)
    int acquireSeat(@Param("seatId") Long seatId,
                    @Param("holdId") String holdId,
                    @Param("expiresAt") Instant expiresAt,
                    @Param("now") Instant now,
                    @Param("heldStatus") SeatStatus heldStatus,
                    @Param("availableStatus") SeatStatus availableStatus);

    default boolean tryAcquire(Long seatId, String holdId, Instant expiresAt, Instant now) {
        return acquireSeat(seatId, holdId, expiresAt, now, SeatStatus.HELD, SeatStatus.AVAILABLE) == 1;
    }

    /** Sweeper: release every seat whose hold deadline passed. Never matches BOOKED rows. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :availableStatus, s.holdId = null, s.holdExpiresAt = null,
                   s.version = s.version + 1, s.updatedAt = :now
             where s.status = :heldStatus and s.holdExpiresAt < :now
            """)
    int releaseExpiredInternal(@Param("now") Instant now,
                               @Param("heldStatus") SeatStatus heldStatus,
                               @Param("availableStatus") SeatStatus availableStatus);

    default int releaseExpired(Instant now) {
        return releaseExpiredInternal(now, SeatStatus.HELD, SeatStatus.AVAILABLE);
    }

    /** Payment failed / booking cancelled while pending: free what this hold still owns. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :availableStatus, s.holdId = null, s.holdExpiresAt = null,
                   s.version = s.version + 1, s.updatedAt = :now
             where s.holdId = :holdId and s.status = :heldStatus
            """)
    int releaseHeldByInternal(@Param("holdId") String holdId, @Param("now") Instant now,
                              @Param("heldStatus") SeatStatus heldStatus,
                              @Param("availableStatus") SeatStatus availableStatus);

    default int releaseHeldBy(String holdId, Instant now) {
        return releaseHeldByInternal(holdId, now, SeatStatus.HELD, SeatStatus.AVAILABLE);
    }

    /** Cancelling a confirmed booking returns BOOKED seats to inventory. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ShowSeat s
               set s.status = :availableStatus, s.holdId = null, s.holdExpiresAt = null,
                   s.version = s.version + 1, s.updatedAt = :now
             where s.id in :ids and s.status = :bookedStatus
            """)
    int releaseBookedInternal(@Param("ids") Collection<Long> ids, @Param("now") Instant now,
                              @Param("bookedStatus") SeatStatus bookedStatus,
                              @Param("availableStatus") SeatStatus availableStatus);

    default int releaseBooked(Collection<Long> ids, Instant now) {
        return releaseBookedInternal(ids, now, SeatStatus.BOOKED, SeatStatus.AVAILABLE);
    }

    long countByHoldIdAndStatus(String holdId, SeatStatus status);
}
