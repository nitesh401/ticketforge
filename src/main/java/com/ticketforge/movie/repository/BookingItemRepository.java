package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.BookingItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingId(Long bookingId);

    @Query("""
            select s.label from BookingItem bi, ShowSeat ss, Seat s
             where bi.bookingId = :bookingId and ss.id = bi.showSeatId and s.id = ss.seatId
             order by ss.id
            """)
    List<String> findSeatLabels(@Param("bookingId") Long bookingId);
}
