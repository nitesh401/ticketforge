package com.ticketforge.waitingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketforge.common.exception.ForbiddenOperationException;
import com.ticketforge.config.TicketForgeProperties;
import com.ticketforge.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WaitingRoomServiceTest {
    private final MutableClock clock = new MutableClock();
    private final WaitingRoomService service = new WaitingRoomService(new TicketForgeProperties(
            new TicketForgeProperties.Hold(Duration.ofMinutes(5), "ATOMIC"),
            new TicketForgeProperties.Retry(3, Duration.ofMillis(1), Duration.ofMillis(4)),
            new TicketForgeProperties.Security("x".repeat(32), Duration.ofHours(1), false, "s"),
            new TicketForgeProperties.WaitingRoom(true, 2, Duration.ofMinutes(2), 100)), clock);

    @Test
    void admitsInFifoOrderAtABoundedRate() {
        var first = service.join("u1");
        var second = service.join("u2");
        var third = service.join("u3");
        assertThat(third.position()).isEqualTo(3);

        assertThat(service.admit(2)).isEqualTo(2);

        assertThat(service.status(first.ticketId(), "u1").state()).isEqualTo(WaitingRoomService.State.ADMITTED);
        assertThat(service.status(second.ticketId(), "u2").state()).isEqualTo(WaitingRoomService.State.ADMITTED);
        var waiting = service.status(third.ticketId(), "u3");
        assertThat(waiting.state()).isEqualTo(WaitingRoomService.State.WAITING);
        assertThat(waiting.position()).isEqualTo(1);
    }

    @Test
    void bookingRequiresAValidUnexpiredAdmission() {
        var ticket = service.join("u1");
        assertThatThrownBy(() -> service.requireAdmission("u1", ticket.ticketId()))
                .isInstanceOf(ForbiddenOperationException.class);

        service.admit(1);
        service.requireAdmission("u1", ticket.ticketId());                       // admitted: allowed
        assertThatThrownBy(() -> service.requireAdmission("other", ticket.ticketId()))
                .isInstanceOf(ForbiddenOperationException.class);              // token is bound to the user

        clock.advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> service.requireAdmission("u1", ticket.ticketId()))
                .isInstanceOf(ForbiddenOperationException.class);              // admission expired
    }
}
