package com.ticketforge.movie.repository;

import com.ticketforge.movie.domain.Show;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowRepository extends JpaRepository<Show, Long> {
    Optional<Show> findByPublicId(String publicId);
}
