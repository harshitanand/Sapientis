package com.moviebooking.service;

import com.moviebooking.dto.request.BookingRequest;
import com.moviebooking.dto.response.BookingConfirmationDto;
import com.moviebooking.dto.response.SeatAvailabilityDto;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    /**
     * Write scenario: reserve seats and create a booking in AWAITING_PAYMENT state.
     * Idempotent — re-submitting the same idempotencyKey returns the original response.
     */
    BookingConfirmationDto initiateBooking(BookingRequest request);

    /**
     * Called by payment callback to confirm a booking after successful payment.
     */
    BookingConfirmationDto confirmBooking(String bookingRef, String paymentRef);

    /**
     * Cancel a booking and release seat locks / inventory.
     */
    BookingConfirmationDto cancelBooking(String bookingRef, UUID requestedBy);

    /**
     * Seat map for a given show: useful for the seat-selection UI.
     */
    List<SeatAvailabilityDto> getSeatMap(UUID showId);
}
