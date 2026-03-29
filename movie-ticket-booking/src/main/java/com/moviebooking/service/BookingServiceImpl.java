package com.moviebooking.service;

import com.moviebooking.dto.request.BookingRequest;
import com.moviebooking.dto.response.BookingConfirmationDto;
import com.moviebooking.dto.response.SeatAvailabilityDto;
import com.moviebooking.event.BookingEventPublisher;
import com.moviebooking.exception.BookingNotFoundException;
import com.moviebooking.exception.SeatUnavailableException;
import com.moviebooking.model.*;
import com.moviebooking.model.enums.BookingStatus;
import com.moviebooking.model.enums.SeatStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.SeatInventoryRepository;
import com.moviebooking.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository       bookingRepository;
    private final ShowRepository          showRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final SeatLockService         seatLockService;
    private final PricingService          pricingService;
    private final BookingEventPublisher   eventPublisher;
    private final BookingExpiryHelper     expiryHelper;

    @Value("${booking.seat-lock.ttl-minutes:10}")
    private int seatLockTtlMinutes;

    // -------------------------------------------------------------------------
    // Initiate booking (write scenario)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BookingConfirmationDto initiateBooking(BookingRequest request) {

        // 1. Idempotency guard — return existing booking if key already used
        return bookingRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .map(existing -> {
                    log.info("Idempotent replay for key={}", request.getIdempotencyKey());
                    return toConfirmationDto(existing);
                })
                .orElseGet(() -> createNewBooking(request));
    }

    private BookingConfirmationDto createNewBooking(BookingRequest request) {
        Show show = showRepository.findById(request.getShowId())
                .orElseThrow(() -> new BookingNotFoundException("Show not found: " + request.getShowId()));

        List<UUID> seatIds = request.getSeatInventoryIds();

        // 2. Acquire Redis locks first (fast-fail, no DB involvement yet)
        boolean locked = seatLockService.acquireAll(seatIds, request.getIdempotencyKey());
        if (!locked) {
            throw new SeatUnavailableException(
                    "One or more selected seats are currently held by another booking. Please try again.");
        }

        try {
            // 3. Pessimistic DB lock — verifies seats are still AVAILABLE in the DB
            List<SeatInventory> lockedSeats = seatInventoryRepository.lockAvailableSeats(seatIds);
            if (lockedSeats.size() != seatIds.size()) {
                int unavailable = seatIds.size() - lockedSeats.size();
                throw new SeatUnavailableException(
                        unavailable + " seat(s) are no longer available. Please refresh and try again.");
            }

            // 4. Sort lockedSeats to match the client-specified order so ticketPosition is deterministic
            Map<UUID, SeatInventory> lockedById = lockedSeats.stream()
                    .collect(Collectors.toMap(SeatInventory::getId, si -> si));
            List<SeatInventory> orderedSeats = seatIds.stream()
                    .map(lockedById::get)
                    .toList();

            // 5. Calculate pricing with applicable offers
            AtomicInteger position = new AtomicInteger(1);
            List<BookingItem> items      = new ArrayList<>();
            BigDecimal totalAmount       = BigDecimal.ZERO;
            BigDecimal totalDiscount     = BigDecimal.ZERO;

            for (SeatInventory si : orderedSeats) {
                PricingService.PriceResult result =
                        pricingService.calculatePrice(show, si, position.getAndIncrement());

                items.add(BookingItem.builder()
                        .seatInventory(si)
                        .basePrice(result.basePrice())
                        .discountPct(result.discountPct())
                        .finalPrice(result.finalPrice())
                        .build());

                totalAmount   = totalAmount.add(result.basePrice());
                totalDiscount = totalDiscount.add(result.basePrice().subtract(result.finalPrice()));
            }

            BigDecimal finalAmount = totalAmount.subtract(totalDiscount);

            // 6. Mark seats as LOCKED in the DB
            orderedSeats.forEach(si -> {
                si.setStatus(SeatStatus.LOCKED);
                si.setLockedBy(request.getIdempotencyKey());
                si.setLockedAt(LocalDateTime.now());
            });
            seatInventoryRepository.saveAll(orderedSeats);

            // 7. Persist the booking
            Booking booking = Booking.builder()
                    .bookingRef(generateBookingRef())
                    .customerId(request.getCustomerId())
                    .show(show)
                    .idempotencyKey(request.getIdempotencyKey())
                    .status(BookingStatus.AWAITING_PAYMENT)
                    .totalAmount(totalAmount)
                    .discountAmount(totalDiscount)
                    .finalAmount(finalAmount)
                    .expiresAt(LocalDateTime.now().plusMinutes(seatLockTtlMinutes))
                    .build();

            items.forEach(item -> item.setBooking(booking));
            booking.getItems().addAll(items);

            Booking saved = bookingRepository.save(booking);
            log.info("Booking created ref={} status={} amount={}", saved.getBookingRef(), saved.getStatus(), finalAmount);

            // 8. Publish event asynchronously (Kafka) — notification, analytics
            eventPublisher.publishBookingInitiated(saved);

            return toConfirmationDto(saved);

        } catch (SeatUnavailableException e) {
            // Release Redis locks on failure
            seatLockService.releaseAll(seatIds, request.getIdempotencyKey());
            throw e;
        } catch (Exception e) {
            seatLockService.releaseAll(seatIds, request.getIdempotencyKey());
            log.error("Unexpected error during booking creation", e);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // Confirm booking (post payment-gateway callback)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BookingConfirmationDto confirmBooking(String bookingRef, String paymentRef) {
        Booking booking = findByRef(bookingRef);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return toConfirmationDto(booking);   // idempotent
        }

        if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                    "Cannot confirm booking in status: " + booking.getStatus());
        }

        if (booking.getExpiresAt() != null && LocalDateTime.now().isAfter(booking.getExpiresAt())) {
            expiryHelper.expireBooking(booking);
            throw new IllegalStateException("Booking has expired. Please rebook.");
        }

        // Promote seat status from LOCKED -> BOOKED
        List<SeatInventory> toBook = booking.getItems().stream()
                .map(item -> {
                    SeatInventory si = item.getSeatInventory();
                    si.setStatus(SeatStatus.BOOKED);
                    si.setLockedBy(null);
                    si.setLockedAt(null);
                    return si;
                })
                .toList();
        seatInventoryRepository.saveAll(toBook);

        // Release Redis locks (seats are now hard-booked in DB)
        List<UUID> seatIds = booking.getItems().stream()
                .map(bi -> bi.getSeatInventory().getId())
                .toList();
        seatLockService.releaseAll(seatIds, booking.getIdempotencyKey());

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentRef(paymentRef);
        booking.setExpiresAt(null);

        Booking confirmed = bookingRepository.save(booking);
        log.info("Booking confirmed ref={} paymentRef={}", bookingRef, paymentRef);

        eventPublisher.publishBookingConfirmed(confirmed);
        return toConfirmationDto(confirmed);
    }

    // -------------------------------------------------------------------------
    // Cancel booking
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public BookingConfirmationDto cancelBooking(String bookingRef, UUID requestedBy) {
        Booking booking = findByRef(bookingRef);

        if (!booking.getCustomerId().equals(requestedBy)) {
            throw new SecurityException("You are not authorised to cancel this booking.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return toConfirmationDto(booking);
        }

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            // Business rule: confirmed bookings require a refund workflow
            // Refund initiation is delegated to a separate RefundService (out of scope here)
            log.warn("Cancellation of CONFIRMED booking ref={} — refund required", bookingRef);
        }

        // Release seat inventory
        List<SeatInventory> toRelease = booking.getItems().stream()
                .map(item -> {
                    SeatInventory si = item.getSeatInventory();
                    si.setStatus(SeatStatus.AVAILABLE);
                    si.setLockedBy(null);
                    si.setLockedAt(null);
                    return si;
                })
                .toList();
        seatInventoryRepository.saveAll(toRelease);

        // Release Redis locks if still held
        List<UUID> seatIds = booking.getItems().stream()
                .map(bi -> bi.getSeatInventory().getId())
                .toList();
        seatLockService.releaseAll(seatIds, booking.getIdempotencyKey());

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);

        Booking cancelled = bookingRepository.save(booking);
        log.info("Booking cancelled ref={} by={}", bookingRef, requestedBy);
        eventPublisher.publishBookingCancelled(cancelled);

        return toConfirmationDto(cancelled);
    }

    // -------------------------------------------------------------------------
    // Seat map
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SeatAvailabilityDto> getSeatMap(UUID showId) {
        return seatInventoryRepository
                .findByShowIdOrderBySeatLayout(showId)
                .stream()
                .map(si -> SeatAvailabilityDto.builder()
                        .seatInventoryId(si.getId())
                        .rowLabel(si.getSeat().getRowLabel())
                        .seatNumber(si.getSeat().getSeatNumber())
                        .category(si.getSeat().getCategory())
                        .price(si.getPrice())
                        .status(si.getStatus())
                        .build())
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Booking findByRef(String bookingRef) {
        return bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingRef));
    }

    private BookingConfirmationDto toConfirmationDto(Booking booking) {
        List<SeatAvailabilityDto> seats = booking.getItems().stream()
                .map(item -> {
                    SeatInventory si = item.getSeatInventory();
                    return SeatAvailabilityDto.builder()
                            .seatInventoryId(si.getId())
                            .rowLabel(si.getSeat().getRowLabel())
                            .seatNumber(si.getSeat().getSeatNumber())
                            .category(si.getSeat().getCategory())
                            .price(item.getFinalPrice())
                            .status(si.getStatus())
                            .build();
                })
                .toList();

        return BookingConfirmationDto.builder()
                .bookingId(booking.getId())
                .bookingRef(booking.getBookingRef())
                .status(booking.getStatus())
                .seats(seats)
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .finalAmount(booking.getFinalAmount())
                .expiresAt(booking.getExpiresAt())
                .message(resolveMessage(booking.getStatus()))
                .build();
    }

    private String resolveMessage(BookingStatus status) {
        return switch (status) {
            case AWAITING_PAYMENT -> "Seats reserved for 10 minutes. Please complete payment.";
            case CONFIRMED        -> "Booking confirmed! Enjoy the show.";
            case CANCELLED        -> "Booking has been cancelled.";
            case EXPIRED          -> "Booking expired due to payment timeout.";
            case PENDING          -> "Booking is being processed.";
            default               -> "";
        };
    }

    private static final DateTimeFormatter REF_FMT    = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom     SECURE_RNG  = new SecureRandom();

    private String generateBookingRef() {
        return "BK" + LocalDateTime.now().format(REF_FMT)
               + String.format("%06d", SECURE_RNG.nextInt(1_000_000));
    }
}
