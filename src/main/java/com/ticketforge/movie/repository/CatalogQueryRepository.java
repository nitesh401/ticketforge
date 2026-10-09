package com.ticketforge.movie.repository;

import com.ticketforge.movie.dto.MovieView;
import com.ticketforge.movie.dto.ShowView;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only catalog join across movie/theatre/screen tables; no entities needed for display data. */
@Repository
public class CatalogQueryRepository {
    private final JdbcTemplate jdbc;

    public CatalogQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MovieView> listMovies() {
        Map<Long, String> titles = new LinkedHashMap<>();
        Map<Long, Integer> durations = new LinkedHashMap<>();
        Map<Long, List<ShowView>> shows = new LinkedHashMap<>();
        jdbc.query("""
                SELECT m.id, m.title, m.duration_minutes, sh.public_id, t.name, sc.name, sh.starts_at,
                       sh.price_minor, sh.currency
                  FROM movies m
                  LEFT JOIN shows sh ON sh.movie_id = m.id
                  LEFT JOIN screens sc ON sc.id = sh.screen_id
                  LEFT JOIN theatres t ON t.id = sc.theatre_id
                 ORDER BY m.id, sh.starts_at
                """, rs -> {
            long id = rs.getLong(1);
            titles.putIfAbsent(id, rs.getString(2));
            durations.putIfAbsent(id, rs.getInt(3));
            List<ShowView> list = shows.computeIfAbsent(id, k -> new ArrayList<>());
            String showId = rs.getString(4);
            if (showId != null) {
                Timestamp ts = rs.getTimestamp(7);
                list.add(new ShowView(showId, rs.getString(5), rs.getString(6), ts.toInstant(),
                        rs.getLong(8), rs.getString(9).trim()));
            }
        });
        List<MovieView> result = new ArrayList<>();
        titles.forEach((id, title) -> result.add(new MovieView(title, durations.get(id), shows.get(id))));
        return result;
    }
}
