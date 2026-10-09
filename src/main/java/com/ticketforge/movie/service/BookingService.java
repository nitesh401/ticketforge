package com.ticketforge.movie.service;

import com.ticketforge.common.Ids;
import com.ticketforge.common.exception.BusinessRuleException;
import com.ticketforge.common.exception.ConflictException;
import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.idempotency.IdempotencyService;
import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import com.ticketforge.infrastructure.events.DomainEvents;
import com.ticketforge.infrastructure.kafka.Topics;
import com.ticketforge.movie.domain.Booking;
import com.ticketforge.movie.domain.BookingItem;
import com.ticketforge.movie.domain.BookingStatus;
import com.ticketforge.movie.domain.Hold;
import com.ticketforge.movie.domain.HoldItem;
import com.ticketforge.movie.domain.HoldStatus;
import com.ticketforge.movie.domain.SeatStatus;
import com.ticketforge.movie.domain.Show;
import com.ticketforge.movie.dto.BookingRequest;
import com.ticketforge.movie.dto.BookingResponse;
import com.ticketforge.movie.repository.BookingItemRepository;
import com.ticketforge.movie.repository.BookingRepository;
import com.ticketforge.movie.repository.HoldItemRepository;
import com.ticketforge.movie.repository.HoldRepository;
import com.ticketforge.movie.repository.ShowRepository;
import com.ticketforge.movie.repository.ShowSeatRepository;
import com.ticketforge.payment.domain.Payment;
import com.ticketforge.payment.domain.PaymentStatus;
import com.ticketforge.payment.repository.PaymentRepository;
import com.ticketforge.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final HoldRepository holds;
    private final HoldItemRepository holdItems;
    private final BookingRepository bookings;
    private final BookingItemRepository bookingItems;
    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final PaymentRepository payments;
    private final IdempotencyService idempotency;
    private final TransactionalRunner tx;
    private final DomainEvents events;
    private final Clock clock;

    public BookingService(HoldRepository holds, HoldItemRepository holdItems, BookingRepository bookings,
                          BookingItemRepository bookingItems, ShowRepository shows, ShowSeatRepository showSeats,
                          PaymentRepository payments, IdempotencyService idempotency, TransactionalRunner tx,
                          DomainEvents events, Clock clock) {
        this.holds = holds;
        this.holdItems = holdItems;
        this.bookings = bookings;
        this.bookingItems = bookingItems;
        this.shows = shows;
        this.showSeats = showSeats;
        this.payments = payments;
        this.idempotency = idempotency;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
    }

    /** Turns an active hold into a booking awaiting payment. Safe to retry with the same Idempotency-Key. */
    public IdempotentResult<BookingResponse> createBooking(BookingRequest request, AuthenticatedUser user,
                                                           String idempotencyKey) {
        return idempotency.execute(user.userId(), idempotencyKey, request, BookingResponse.class, 201,
                () -> createInTransaction(request, user));
    }

    private BookingResponse createInTransaction(BookingRequest request, AuthenticatedUser user) {
        // Row lock on the hold serialises concurrent booking attempts for the same hold.
        Hold hold = holds.findForUpdate(request.holdId())
                .filter(h -> user.isAdmin() || h.getUserId().equals(user.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("HOLD_NOT_FOUND", "Hold not found"));
        Instant now = clock.instant();
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new BusinessRuleException("HOLD_NOT_ACTIVE", "Hold is " + hold.getStatus());
        }
        if (!hold.isActiveAt(now)) {
            throw new BusinessRuleException("HOLD_EXPIRED", "Hold expired, please select seats again");
        }
        List<HoldItem> items = holdItems.findByHoldId(hold.getId());
        if (showSeats.countByHoldIdAndStatus(hold.getId(), SeatStatus.HELD) != items.size()) {
            throw new BusinessRuleException("HOLD_LOST", "Seats of this hold are no longer held");
        }
        if (bookings.existsByHoldId(hold.getId())) {
            throw new ConflictException("BOOKING_ALREADY_EXISTS", "A booking already exists for this hold");
        }

        Show show = shows.findById(hold.getShowId()).orElseThrow();
        String currency = show.getCurrency().trim();
        long total = show.getPriceMinor() * items.size();
        Booking booking = new Booking(Ids.booking(), hold.getUserId(), hold.getShowId(), hold.getId(), total, currency, now);
        booking.transitionTo(BookingStatus.PAYMENT_PENDING, now);
        bookings.save(booking);
        bookingItems.saveAll(items.stream()
                .map(i -> new BookingItem(booking.getId(), i.getShowSeatId(), show.getPriceMinor())).toList());
        Payment payment = payments.save(new Payment(Ids.payment(), booking.getId(), total, currency, now));

        events.publish(Topics.BOOKING_EVENTS, "booking.payment.pending", booking.getPublicId(),
                Map.of("bookingId", booking.getPublicId(), "paymentRef", payment.getProviderRef()));
        return toResponse(booking, show.getPublicId(), holdItems.findSeatLabels(hold.getId()), payment);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(String bookingId, AuthenticatedUser user) {
        Booking booking = bookings.findByPublicId(bookingId)
                .filter(b -> user.isAdmin() || b.getUserId().equals(user.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
        Show show = shows.findById(booking.getShowId()).orElseThrow();
        Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
        return toResponse(booking, show.getPublicId(), bookingItems.findSeatLabels(booking.getId()), payment);
    }

    public BookingResponse cancelBooking(String bookingId, AuthenticatedUser user) {
        return tx.run(() -> {
            Booking booking = bookings.findByPublicIdForUpdate(bookingId)
                    .filter(b -> user.isAdmin() || b.getUserId().equals(user.userId()))
                    .orElseThrow(() -> new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
            Instant now = clock.instant();
            BookingStatus before = booking.getStatus();
            booking.transitionTo(BookingStatus.CANCELLED, now);   // 422 for CONFIRMED-less terminal states

            if (before == BookingStatus.CONFIRMED) {
                List<Long> seatIds = bookingItems.findByBookingId(booking.getId()).stream()
                        .map(BookingItem::getShowSeatId).sorted().toList();
                showSeats.releaseBooked(seatIds, now);
            } else {
                showSeats.releaseHeldBy(booking.getHoldId(), now);
                holds.findById(booking.getHoldId()).ifPresent(h -> h.setStatus(HoldStatus.RELEASED));
            }
            Payment payment = payments.findByBookingId(booking.getId()).orElseThrow();
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                payment.markRefundRequired("BOOKING_CANCELLED", now);
            }
            events.publish(Topics.BOOKING_EVENTS, "booking.cancelled", booking.getPublicId(),
                    Map.of("bookingId", booking.getPublicId()));
            Show show = shows.findById(booking.getShowId()).orElseThrow();
            return toResponse(booking, show.getPublicId(), bookingItems.findSeatLabels(booking.getId()), payment);
        });
    }

    public static BookingResponse toResponse(Booking b, String showPublicId, List<String> labels, Payment p) {
        return new BookingResponse(b.getPublicId(), b.getStatus().name(), b.getHoldId(), showPublicId, labels,
                b.getTotalAmountMinor(), b.getCurrency(), p.getProviderRef(), p.getStatus().name());
    }
}
