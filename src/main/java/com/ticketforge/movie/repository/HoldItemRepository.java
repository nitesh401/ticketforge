package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.HoldItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldItemRepository extends JpaRepository<HoldItem, Long> {

    @Query("""
            select s.label from HoldItem hi, ShowSeat ss, Seat s
             where hi.holdId = :holdId and ss.id = hi.showSeatId and s.id = ss.seatId
             order by ss.id
            """)
    List<String> findSeatLabels(@Param("holdId") String holdId);

    long countByHoldId(String holdId);

    List<HoldItem> findByHoldId(String holdId);
}
