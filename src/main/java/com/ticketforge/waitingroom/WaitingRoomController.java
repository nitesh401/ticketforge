package com.ticketforge.waitingroom;

import com.ticketforge.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/waiting-room")
public class WaitingRoomController {
    private final WaitingRoomService service;

    public WaitingRoomController(WaitingRoomService service) {
        this.service = service;
    }

    @PostMapping("/join")
    public WaitingRoomService.Status join() {
        return service.join(CurrentUser.require().userId());
    }

    @GetMapping("/status/{ticketId}")
    public WaitingRoomService.Status status(@PathVariable String ticketId) {
        return service.status(ticketId, CurrentUser.require().userId());
    }
}
