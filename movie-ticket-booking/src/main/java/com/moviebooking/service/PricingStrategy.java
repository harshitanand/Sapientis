package com.moviebooking.service;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.Show;

import java.math.BigDecimal;

/**
 * Strategy interface for computing the discount percentage (0–100) to apply
 * to a specific ticket within a booking.
 *
 * Implementations are registered as Spring beans and applied in priority order.
 * The highest applicable discount wins per ticket (discounts do not stack).
 */
public interface PricingStrategy {

    /**
     * @param show          the show being booked
     * @param seat          the seat inventory entry
     * @param ticketPosition 1-based position of this ticket in the booking
     * @return discount percentage as a value between 0 and 100 (inclusive)
     */
    BigDecimal discountPercentage(Show show, SeatInventory seat, int ticketPosition);

    /**
     * Lower value = higher priority when multiple strategies apply.
     */
    int priority();
}
