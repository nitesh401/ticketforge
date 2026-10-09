package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.Berth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BerthRepository extends JpaRepository<Berth, Long> {

    @Query(value = """
            SELECT b.* FROM berths b JOIN coaches c ON c.id = b.coach_id
             WHERE c.train_id = :trainId AND b.ordinal = :ordinal
            """, nativeQuery = true)
    Berth findByTrainAndOrdinal(@Param("trainId") Long trainId, @Param("ordinal") int ordinal);
}
