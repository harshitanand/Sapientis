package com.moviebooking.service.pricing;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.Show;
import com.moviebooking.model.enums.ShowSlot;
import com.moviebooking.service.PricingStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Platform offer: 20% discount on all tickets for afternoon shows (12:00–17:00).
 */
@Component
public class AfternoonShowDiscountStrategy implements PricingStrategy {

    private static final BigDecimal DISCOUNT = BigDecimal.valueOf(20);

    @Override
    public BigDecimal discountPercentage(Show show, SeatInventory seat, int ticketPosition) {
        return show.getSlot() == ShowSlot.AFTERNOON ? DISCOUNT : BigDecimal.ZERO;
    }

    @Override
    public int priority() {
        return 20;
    }
}
