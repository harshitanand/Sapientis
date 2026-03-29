package com.moviebooking.service;

import com.moviebooking.model.Booking;
import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.enums.BookingStatus;
import com.moviebooking.model.enums.SeatStatus;
import com.moviebooking.repository.BookingRepository;
import com.moviebooking.repository.SeatInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingExpiryHelper {

    private final BookingRepository       bookingRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireBooking(Booking booking) {
        booking.setStatus(BookingStatus.EXPIRED);
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
        bookingRepository.save(booking);
    }
}
