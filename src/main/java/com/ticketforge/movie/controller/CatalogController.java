package com.ticketforge.movie.controller;

import com.ticketforge.movie.dto.MovieView;
import com.ticketforge.movie.dto.SeatMapResponse;
import com.ticketforge.movie.repository.CatalogQueryRepository;
import com.ticketforge.movie.service.SeatMapService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogQueryRepository catalog;
    private final SeatMapService seatMapService;

    public CatalogController(CatalogQueryRepository catalog, SeatMapService seatMapService) {
        this.catalog = catalog;
        this.seatMapService = seatMapService;
    }

    @GetMapping("/movies")
    public List<MovieView> movies() {
        return catalog.listMovies();
    }

    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse seats(@PathVariable String showId) {
        return seatMapService.seatMap(showId);
    }
}
