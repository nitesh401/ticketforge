package com.ticketforge.railway.service;

import com.ticketforge.common.Ids;
import com.ticketforge.common.TicketMetrics;
import com.ticketforge.common.exception.InsufficientInventoryException;
import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.idempotency.IdempotencyService;
import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.railway.domain.Berth;
import com.ticketforge.railway.domain.QuotaInventory;
import com.ticketforge.railway.domain.QuotaType;
import com.ticketforge.railway.domain.RailBooking;
import com.ticketforge.railway.domain.RailPassenger;
import com.ticketforge.railway.domain.Train;
import com.ticketforge.railway.domain.TrainSchedule;
import com.ticketforge.railway.dto.AvailabilityResponse;
import com.ticketforge.railway.dto.PassengerAllocation;
import com.ticketforge.railway.dto.PassengerRequest;
import com.ticketforge.railway.dto.ReservationRequest;
import com.ticketforge.railway.dto.ReservationResponse;
import com.ticketforge.railway.repository.BerthRepository;
import com.ticketforge.railway.repository.QuotaInventoryRepository;
import com.ticketforge.railway.repository.RailBookingRepository;
import com.ticketforge.railway.repository.RailPassengerRepository;
import com.ticketforge.railway.repository.TrainRepository;
import com.ticketforge.railway.repository.TrainScheduleRepository;
import com.ticketforge.security.AuthenticatedUser;
import com.ticketforge.waitingroom.WaitingRoomService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RailReservationService {
    private static final QuotaType QUOTA = QuotaType.TATKAL;

    private final TrainRepository trains;
    private final TrainScheduleRepository schedules;
    private final QuotaInventoryRepository inventory;
    private final InventoryAllocator allocator;
    private final BerthRepository berths;
    private final RailBookingRepository bookings;
    private final RailPassengerRepository passengers;
    private final IdempotencyService idempotency;
    private final WaitingRoomService waitingRoom;
    private final Clock clock;

    public RailReservationService(TrainRepository trains, TrainScheduleRepository schedules,
                                  QuotaInventoryRepository inventory, InventoryAllocator allocator,
                                  BerthRepository berths, RailBookingRepository bookings,
                                  RailPassengerRepository passengers, IdempotencyService idempotency,
                                  WaitingRoomService waitingRoom, Clock clock) {
        this.trains = trains;
        this.schedules = schedules;
        this.inventory = inventory;
        this.allocator = allocator;
        this.berths = berths;
        this.bookings = bookings;
        this.passengers = passengers;
        this.idempotency = idempotency;
        this.waitingRoom = waitingRoom;
        this.clock = clock;
    }

    public IdempotentResult<ReservationResponse> reserve(ReservationRequest request, AuthenticatedUser user,
                                                         String idempotencyKey, String admissionToken) {
        waitingRoom.requireAdmission(user.userId(), admissionToken);
        return idempotency.execute(user.userId(), idempotencyKey, request, ReservationResponse.class, 201,
                () -> reserveInTransaction(request, user));
    }

    /**
     * Runs inside the idempotency transaction. The conditional UPDATE both decides success and takes the
     * row lock; because the lock is held until commit, nobody else can change the counter while we read
     * it back, so (remaining + 1 .. remaining + n) is a berth range no other reservation can receive.
     */
    private ReservationResponse reserveInTransaction(ReservationRequest request, AuthenticatedUser user) {
        Train train = trains.findByTrainNo(request.trainNo())
                .orElseThrow(() -> new ResourceNotFoundException("TRAIN_NOT_FOUND", "Unknown train"));
        TrainSchedule schedule = schedules.findByTrainIdAndJourneyDate(train.getId(), request.journeyDate())
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "No schedule for that date"));
        int count = request.passengers().size();

        if (!allocator.tryAllocate(schedule.getId(), QUOTA, count)) {
            throw new InsufficientInventoryException();
        }
        int remaining = inventory.findAvailable(schedule.getId(), QUOTA).orElseThrow();

        Instant now = clock.instant();
        RailBooking booking = bookings.save(new RailBooking(Ids.reservation(), Ids.pnr(), user.userId(),
                schedule.getId(), QUOTA, count, now));
        List<PassengerAllocation> allocations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PassengerRequest p = request.passengers().get(i);
            Berth berth = berths.findByTrainAndOrdinal(train.getId(), remaining + 1 + i);
            // uq_passenger_berth makes a duplicate berth impossible even if this arithmetic were wrong.
            passengers.save(new RailPassenger(booking.getId(), schedule.getId(), p.name(), p.age(), berth.getId()));
            allocations.add(new PassengerAllocation(p.name(), p.age(), berth.getLabel()));
        }
        return new ReservationResponse(booking.getPublicId(), booking.getPnr(), train.getTrainNo(),
                schedule.getJourneyDate(), QUOTA.name(), booking.getStatus(), allocations);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String reservationId, AuthenticatedUser user) {
        RailBooking booking = bookings.findByPublicId(reservationId)
                .filter(b -> user.isAdmin() || b.getUserId().equals(user.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("RESERVATION_NOT_FOUND", "Reservation not found"));
        TrainSchedule schedule = schedules.findById(booking.getTrainScheduleId()).orElseThrow();
        Train train = trains.findById(schedule.getTrainId()).orElseThrow();
        List<PassengerAllocation> allocations = passengers.findByRailBookingId(booking.getId()).stream()
                .map(p -> new PassengerAllocation(p.getName(), p.getAge(),
                        berths.findById(p.getBerthId()).orElseThrow().getLabel()))
                .toList();
        return new ReservationResponse(booking.getPublicId(), booking.getPnr(), train.getTrainNo(),
                schedule.getJourneyDate(), booking.getQuotaType().name(), booking.getStatus(), allocations);
    }

    /** Advisory: a positive number here does not reserve anything. */
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(String trainNo, LocalDate journeyDate) {
        Train train = trains.findByTrainNo(trainNo)
                .orElseThrow(() -> new ResourceNotFoundException("TRAIN_NOT_FOUND", "Unknown train"));
        TrainSchedule schedule = schedules.findByTrainIdAndJourneyDate(train.getId(), journeyDate)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEDULE_NOT_FOUND", "No schedule for that date"));
        QuotaInventory quota = inventory.findByTrainScheduleIdAndQuotaType(schedule.getId(), QUOTA).orElseThrow();
        return new AvailabilityResponse(train.getTrainNo(), train.getName(), journeyDate, QUOTA.name(),
                quota.getTotalCapacity(), quota.getAvailableCount(),
                "Advisory view: only a successful reservation guarantees a berth.");
    }
}
