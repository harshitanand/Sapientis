package com.moviebooking.service;

import com.moviebooking.dto.request.BookingRequest;
import com.moviebooking.dto.response.BookingConfirmationDto;
import com.moviebooking.event.BookingEventPublisher;
import com.moviebooking.exception.SeatUnavailableException;
import com.moviebooking.model.*;
import com.moviebooking.model.enums.BookingStatus;
import com.moviebooking.model.enums.SeatStatus;
import com.moviebooking.model.enums.ShowSlot;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.SeatInventoryRepository;
import com.moviebooking.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository       bookingRepository;
    @Mock private ShowRepository          showRepository;
    @Mock private SeatInventoryRepository seatInventoryRepository;
    @Mock private SeatLockService         seatLockService;
    @Mock private PricingService          pricingService;
    @Mock private BookingEventPublisher   eventPublisher;
    @Mock private BookingExpiryHelper     expiryHelper;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private UUID showId;
    private UUID customerId;
    private UUID seatInvId;
    private Show show;
    private SeatInventory seatInventory;
    private BookingRequest request;

    @BeforeEach
    void setup() {
        showId      = UUID.randomUUID();
        customerId  = UUID.randomUUID();
        seatInvId   = UUID.randomUUID();

        Screen screen = Screen.builder()
                .id(UUID.randomUUID()).name("Screen 1").screenType("2D").totalSeats(100).build();
        Theatre theatre = Theatre.builder()
                .id(UUID.randomUUID()).name("PVR Cinemas").city("Bangalore").country("IN").build();
        screen.setTheatre(theatre);

        show = Show.builder()
                .id(showId).slot(ShowSlot.EVENING).basePrice(BigDecimal.valueOf(250))
                .screen(screen).build();

        Seat seat = Seat.builder()
                .id(UUID.randomUUID()).rowLabel("C").seatNumber(5).category("REGULAR")
                .screen(screen).build();
        seatInventory = SeatInventory.builder()
                .id(seatInvId).show(show).seat(seat)
                .price(BigDecimal.valueOf(250)).status(SeatStatus.AVAILABLE).build();

        request = new BookingRequest();
        request.setShowId(showId);
        request.setCustomerId(customerId);
        request.setSeatInventoryIds(List.of(seatInvId));
        request.setIdempotencyKey("idem-key-001");
    }

    @Test
    void initiateBooking_success() {
        when(bookingRepository.findByIdempotencyKey("idem-key-001")).thenReturn(Optional.empty());
        when(showRepository.findById(showId)).thenReturn(Optional.of(show));
        when(seatLockService.acquireAll(any(), eq("idem-key-001"))).thenReturn(true);
        when(seatInventoryRepository.lockAvailableSeats(any())).thenReturn(List.of(seatInventory));
        when(pricingService.calculatePrice(eq(show), eq(seatInventory), eq(1)))
                .thenReturn(new PricingService.PriceResult(
                        BigDecimal.valueOf(250), BigDecimal.ZERO, BigDecimal.valueOf(250)));

        Booking savedBooking = Booking.builder()
                .id(UUID.randomUUID()).bookingRef("BK20240312000001")
                .show(show).customerId(customerId).idempotencyKey("idem-key-001")
                .status(BookingStatus.AWAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(250))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(250))
                .build();
        savedBooking.getItems().add(BookingItem.builder()
                .seatInventory(seatInventory).basePrice(BigDecimal.valueOf(250))
                .discountPct(BigDecimal.ZERO).finalPrice(BigDecimal.valueOf(250)).build());

        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingConfirmationDto dto = bookingService.initiateBooking(request);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
        assertThat(dto.getFinalAmount()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(dto.getSeats()).hasSize(1);
        assertThat(dto.getSeats().get(0).getRowLabel()).isEqualTo("C");
        assertThat(dto.getSeats().get(0).getSeatNumber()).isEqualTo(5);
        verify(seatInventoryRepository).saveAll(any());
        verify(eventPublisher).publishBookingInitiated(any());
    }

    @Test
    void initiateBooking_seatAlreadyLocked_throwsException() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(showRepository.findById(showId)).thenReturn(Optional.of(show));
        when(seatLockService.acquireAll(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(SeatUnavailableException.class)
                .hasMessageContaining("currently held");
    }

    @Test
    void initiateBooking_idempotentReplay_returnsSameBooking() {
        Booking existingBooking = Booking.builder()
                .id(UUID.randomUUID()).bookingRef("BK20240312000001")
                .show(show).customerId(customerId).idempotencyKey("idem-key-001")
                .status(BookingStatus.AWAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(250))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(250))
                .build();
        existingBooking.getItems().add(BookingItem.builder()
                .seatInventory(seatInventory).basePrice(BigDecimal.valueOf(250))
                .discountPct(BigDecimal.ZERO).finalPrice(BigDecimal.valueOf(250)).build());

        when(bookingRepository.findByIdempotencyKey("idem-key-001"))
                .thenReturn(Optional.of(existingBooking));

        BookingConfirmationDto dto = bookingService.initiateBooking(request);

        assertThat(dto.getBookingRef()).isEqualTo("BK20240312000001");
        verify(seatLockService, never()).acquireAll(any(), any());
    }

    @Test
    void initiateBooking_dbSeatNoLongerAvailable_throwsException() {
        when(bookingRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(showRepository.findById(showId)).thenReturn(Optional.of(show));
        when(seatLockService.acquireAll(any(), any())).thenReturn(true);
        when(seatInventoryRepository.lockAvailableSeats(any())).thenReturn(List.of()); // 0 available

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(SeatUnavailableException.class);

        verify(seatLockService).releaseAll(any(), any());
    }

    @Test
    void confirmBooking_success() {
        Booking existingBooking = Booking.builder()
                .id(UUID.randomUUID()).bookingRef("BK20260330000001")
                .show(show).customerId(customerId).idempotencyKey("idem-key-002")
                .status(BookingStatus.AWAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(250))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(250))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        existingBooking.getItems().add(BookingItem.builder()
                .seatInventory(seatInventory).basePrice(BigDecimal.valueOf(250))
                .discountPct(BigDecimal.ZERO).finalPrice(BigDecimal.valueOf(250)).build());

        when(bookingRepository.findByBookingRef("BK20260330000001"))
                .thenReturn(Optional.of(existingBooking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(existingBooking);

        BookingConfirmationDto dto = bookingService.confirmBooking("BK20260330000001", "PG-REF-001");

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(seatInventoryRepository).saveAll(any());
        verify(eventPublisher).publishBookingConfirmed(any());
    }

    @Test
    void cancelBooking_awaitingPayment_releasesSeats() {
        Booking existingBooking = Booking.builder()
                .id(UUID.randomUUID()).bookingRef("BK20260330000002")
                .show(show).customerId(customerId).idempotencyKey("idem-key-003")
                .status(BookingStatus.AWAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(250))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(250))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        existingBooking.getItems().add(BookingItem.builder()
                .seatInventory(seatInventory).basePrice(BigDecimal.valueOf(250))
                .discountPct(BigDecimal.ZERO).finalPrice(BigDecimal.valueOf(250)).build());

        when(bookingRepository.findByBookingRef("BK20260330000002"))
                .thenReturn(Optional.of(existingBooking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(existingBooking);

        BookingConfirmationDto dto = bookingService.cancelBooking("BK20260330000002", customerId);

        assertThat(dto.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(seatInventoryRepository).saveAll(any());
        verify(seatLockService).releaseAll(any(), any());
        verify(eventPublisher).publishBookingCancelled(any());
    }
}
