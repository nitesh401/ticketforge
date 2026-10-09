package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.Hold;
import com.ticketforge.movie.domain.HoldStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h where h.id = :id")
    Optional<Hold> findForUpdate(@Param("id") String id);

    @Query("select h.id from Hold h where h.status = :status and h.expiresAt < :now")
    List<String> findIdsExpiredBefore(@Param("now") Instant now, @Param("status") HoldStatus status);

    @Modifying(flushAutomatically = true)
    @Query("update Hold h set h.status = :expired, h.version = h.version + 1 "
            + "where h.status = :active and h.expiresAt < :now")
    int markExpiredInternal(@Param("now") Instant now, @Param("active") HoldStatus active,
                            @Param("expired") HoldStatus expired);

    default int markExpired(Instant now) {
        return markExpiredInternal(now, HoldStatus.ACTIVE, HoldStatus.EXPIRED);
    }
}
