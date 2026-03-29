package com.moviebooking.service;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Evaluates all registered {@link PricingStrategy} instances and returns
 * the best (highest) discount for each ticket position.
 *
 * Design rationale: Strategy pattern allows new offers (loyalty tiers,
 * coupon codes, city-based promos) to be added without touching booking logic.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final List<PricingStrategy> strategies;

    /**
     * Returns the final price for a single ticket after applying the best discount.
     *
     * @param show           show context (slot, movie, etc.)
     * @param seat           specific seat being priced
     * @param ticketPosition 1-based index of this ticket in the booking
     * @return finalPrice and effectiveDiscountPct as a PriceResult record
     */
    public PriceResult calculatePrice(Show show, SeatInventory seat, int ticketPosition) {
        BigDecimal bestDiscount = strategies.stream()
                .sorted(Comparator.comparingInt(PricingStrategy::priority))
                .map(s -> s.discountPercentage(show, seat, ticketPosition))
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        BigDecimal basePrice   = seat.getPrice();
        BigDecimal discountAmt = basePrice.multiply(bestDiscount)
                                          .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalPrice  = basePrice.subtract(discountAmt);

        return new PriceResult(basePrice, bestDiscount, finalPrice);
    }

    public record PriceResult(
            BigDecimal basePrice,
            BigDecimal discountPct,
            BigDecimal finalPrice
    ) {}
}
