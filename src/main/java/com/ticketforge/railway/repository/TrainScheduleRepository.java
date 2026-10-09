package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.TrainSchedule;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainScheduleRepository extends JpaRepository<TrainSchedule, Long> {
    Optional<TrainSchedule> findByTrainIdAndJourneyDate(Long trainId, LocalDate journeyDate);
}
