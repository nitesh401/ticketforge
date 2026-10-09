package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.RailPassenger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RailPassengerRepository extends JpaRepository<RailPassenger, Long> {
    List<RailPassenger> findByRailBookingId(Long railBookingId);
}
