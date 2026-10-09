package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.RailBooking;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RailBookingRepository extends JpaRepository<RailBooking, Long> {
    Optional<RailBooking> findByPublicId(String publicId);
}
