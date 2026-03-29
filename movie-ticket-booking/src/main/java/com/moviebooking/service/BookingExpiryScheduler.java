package com.moviebooking.service;

import com.moviebooking.model.enums.BookingStatus;
import com.moviebooking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository   bookingRepository;
    private final BookingExpiryHelper expiryHelper;

    @Scheduled(fixedDelay = 60_000)
    public void releaseExpiredBookings() {
        var expired = bookingRepository.findExpiredBookings(
                BookingStatus.AWAITING_PAYMENT, LocalDateTime.now());

        if (!expired.isEmpty()) {
            log.info("Expiry sweep: found {} expired bookings to release", expired.size());
        }

        expired.forEach(booking -> {
            try {
                expiryHelper.expireBooking(booking);
                log.debug("Expired booking ref={}", booking.getBookingRef());
            } catch (Exception ex) {
                log.error("Failed to expire booking ref={}", booking.getBookingRef(), ex);
            }
        });
    }
}
