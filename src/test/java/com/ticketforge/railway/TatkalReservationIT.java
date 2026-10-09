package com.ticketforge.railway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketforge.common.exception.InsufficientInventoryException;
import com.ticketforge.railway.dto.PassengerRequest;
import com.ticketforge.railway.dto.ReservationRequest;
import com.ticketforge.railway.service.RailReservationService;
import com.ticketforge.support.AbstractPostgresIT;
import com.ticketforge.support.ConcurrencyHarness;
import com.ticketforge.support.ConcurrencyHarness.Stats;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class TatkalReservationIT extends AbstractPostgresIT {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    @Autowired RailReservationService reservations;
    @Autowired MockMvc mvc;

    private ReservationRequest request(int passengers) {
        return new ReservationRequest("12951", DATE, java.util.stream.IntStream.range(0, passengers)
                .mapToObj(i -> new PassengerRequest("Passenger " + i, 30)).toList());
    }

    private boolean tryReserve(int i, int passengers) {
        try {
            reservations.reserve(request(passengers), user("user-" + i), "key-" + i, null);
            return true;
        } catch (InsufficientInventoryException e) {
            return false;
        }
    }

    @Test
    void thousandUsersCompeteFor100Berths_exactly100Succeed_andNoBerthIsDoubleAllocated() {
        Stats stats = ConcurrencyHarness.run("Tatkal end-to-end, 1000 users, 100 berths", 1000, i -> tryReserve(i, 1));

        assertThat(stats.errors()).as("%s", stats.errorSamples()).isZero();
        assertThat(stats.success()).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rail_passengers", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT berth_id) FROM rail_passengers", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT available_count FROM quota_inventory", Integer.class)).isZero();
    }

    @Test
    void groupBookings_getDisjointBerths_andNeverPartiallyAllocate() {
        Stats stats = ConcurrencyHarness.run("Tatkal groups of 4, 100 berths", 60, i -> tryReserve(i, 4));

        assertThat(stats.errors()).as("%s", stats.errorSamples()).isZero();
        assertThat(stats.success()).isEqualTo(25);   // 25 * 4 = 100
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT berth_id) FROM rail_passengers", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rail_bookings", Long.class)).isEqualTo(25L);
    }

    @Test
    void availabilityIsAdvisory_reservationIsIdempotentOverHttp() throws Exception {
        mvc.perform(get("/api/v1/trains/12951/availability").param("journeyDate", "2026-11-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(100));

        String body = "{\"trainNo\":\"12951\",\"journeyDate\":\"2026-11-15\","
                + "\"passengers\":[{\"name\":\"Asha\",\"age\":31}]}";
        String first = mvc.perform(post("/api/v1/tatkal/reservations").header("Authorization", bearer("user-101"))
                        .header("Idempotency-Key", "tatkal-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/tatkal/reservations").header("Authorization", bearer("user-101"))
                        .header("Idempotency-Key", "tatkal-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.pnr").value(com.jayway.jsonpath.JsonPath.read(first, "$.pnr").toString()));

        mvc.perform(get("/api/v1/trains/12951/availability").param("journeyDate", "2026-11-15"))
                .andExpect(jsonPath("$.availableCount").value(99));   // retry did not consume a second berth
    }

    @Test
    void soldOut_returns409() throws Exception {
        jdbc.update("UPDATE quota_inventory SET available_count = 0");
        mvc.perform(post("/api/v1/tatkal/reservations").header("Authorization", bearer("user-101"))
                        .header("Idempotency-Key", "tatkal-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainNo\":\"12951\",\"journeyDate\":\"2026-11-15\","
                                + "\"passengers\":[{\"name\":\"Ravi\",\"age\":40}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
    }
}
