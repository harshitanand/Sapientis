package com.moviebooking.service.pricing;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.Show;
import com.moviebooking.service.PricingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Platform offer: 50% discount on the 3rd ticket in a single booking.
 */
@Component
public class ThirdTicketDiscountStrategy implements PricingStrategy {

    private static final BigDecimal DISCOUNT = BigDecimal.valueOf(50);
    private static final int TARGET_POSITION = 3;

    @Override
    public BigDecimal discountPercentage(Show show, SeatInventory seat, int ticketPosition) {
        return ticketPosition == TARGET_POSITION ? DISCOUNT : BigDecimal.ZERO;
    }

    @Override
    public int priority() {
        return 10;   // higher priority than afternoon discount
    }
}
