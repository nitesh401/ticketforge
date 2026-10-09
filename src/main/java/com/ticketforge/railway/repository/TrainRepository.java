package com.ticketforge.railway.repository;

import com.ticketforge.railway.domain.Train;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainRepository extends JpaRepository<Train, Long> {
    Optional<Train> findByTrainNo(String trainNo);
}
