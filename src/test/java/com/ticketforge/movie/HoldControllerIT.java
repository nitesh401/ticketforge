package com.ticketforge.movie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ticketforge.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class HoldControllerIT extends AbstractPostgresIT {
    @Autowired MockMvc mvc;

    private static String holdBody(String userId, String... seats) {
        StringBuilder s = new StringBuilder("{\"userId\":\"" + userId + "\",\"seatIds\":[");
        for (int i = 0; i < seats.length; i++) {
            s.append(i > 0 ? "," : "").append('"').append(seats[i]).append('"');
        }
        return s.append("]}").toString();
    }

    @Test
    void userAThenUserB_sameSeat_201ThenConflict() throws Exception {
        mvc.perform(post("/api/v1/shows/{id}/holds", SHOW).header("Authorization", bearer("user-101"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-101", "A1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.holdId").isNotEmpty());

        mvc.perform(post("/api/v1/shows/{id}/holds", SHOW).header("Authorization", bearer("user-102"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-102", "A1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_HELD"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void requestWithoutToken_is401() throws Exception {
        mvc.perform(post("/api/v1/shows/{id}/holds", SHOW)
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-101", "A1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBody_is400() throws Exception {
        mvc.perform(post("/api/v1/shows/{id}/holds", SHOW).header("Authorization", bearer("user-101"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void holdingOnBehalfOfAnotherUser_is403() throws Exception {
        mvc.perform(post("/api/v1/shows/{id}/holds", SHOW).header("Authorization", bearer("user-101"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-102", "A1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HOLD_OWNER_MISMATCH"));
    }

    @Test
    void unknownShow_is404_andSeatMapIsPublic() throws Exception {
        mvc.perform(get("/api/v1/shows/NOPE/seats")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/shows/{id}/seats", SHOW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(12));
    }

    @Test
    void fullFlowOverHttp_holdBookPayConfirm() throws Exception {
        String hold = mvc.perform(post("/api/v1/shows/{id}/holds", SHOW).header("Authorization", bearer("user-101"))
                        .contentType(MediaType.APPLICATION_JSON).content(holdBody("user-101", "B1", "B2")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String holdId = JsonPath.read(hold, "$.holdId");

        String booking = mvc.perform(post("/api/v1/bookings").header("Authorization", bearer("user-101"))
                        .header("Idempotency-Key", "http-key-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + holdId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.totalAmountMinor").value(50000))
                .andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(booking, "$.bookingId");
        String paymentRef = JsonPath.read(booking, "$.paymentRef");

        mvc.perform(post("/api/v1/payments/callback").header("X-Webhook-Secret", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentRef\":\"" + paymentRef + "\",\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/payments/callback").header("X-Webhook-Secret", "test-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentRef\":\"" + paymentRef + "\",\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));

        mvc.perform(get("/api/v1/bookings/{id}", bookingId).header("Authorization", bearer("user-101")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/v1/bookings/{id}", bookingId).header("Authorization", bearer("user-102")))
                .andExpect(status().isNotFound());
    }
}
