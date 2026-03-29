package com.moviebooking.service;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.Show;
import com.moviebooking.model.enums.ShowSlot;
import com.moviebooking.service.pricing.AfternoonShowDiscountStrategy;
import com.moviebooking.service.pricing.ThirdTicketDiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setup() {
        pricingService = new PricingService(
                List.of(new ThirdTicketDiscountStrategy(), new AfternoonShowDiscountStrategy()));
    }

    private SeatInventory seatWithPrice(BigDecimal price) {
        SeatInventory si = mock(SeatInventory.class);
        when(si.getPrice()).thenReturn(price);
        return si;
    }

    private Show showWithSlot(ShowSlot slot) {
        Show show = mock(Show.class);
        when(show.getSlot()).thenReturn(slot);
        return show;
    }

    @Test
    void firstTicket_eveningShow_noDiscount() {
        Show show = showWithSlot(ShowSlot.EVENING);
        SeatInventory seat = seatWithPrice(BigDecimal.valueOf(300));

        PricingService.PriceResult result = pricingService.calculatePrice(show, seat, 1);

        assertThat(result.discountPct()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void thirdTicket_anyShow_gets50PercentOff() {
        Show show = showWithSlot(ShowSlot.EVENING);
        SeatInventory seat = seatWithPrice(BigDecimal.valueOf(200));

        PricingService.PriceResult result = pricingService.calculatePrice(show, seat, 3);

        assertThat(result.discountPct()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void afternoonShow_firstTicket_gets20PercentOff() {
        Show show = showWithSlot(ShowSlot.AFTERNOON);
        SeatInventory seat = seatWithPrice(BigDecimal.valueOf(200));

        PricingService.PriceResult result = pricingService.calculatePrice(show, seat, 1);

        assertThat(result.discountPct()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(160));
    }

    @Test
    void afternoonShow_thirdTicket_appliesBestDiscount_50Percent() {
        // Both offers apply to the 3rd ticket in an afternoon show.
        // Best (highest) discount wins: 50% over 20%.
        Show show = showWithSlot(ShowSlot.AFTERNOON);
        SeatInventory seat = seatWithPrice(BigDecimal.valueOf(200));

        PricingService.PriceResult result = pricingService.calculatePrice(show, seat, 3);

        assertThat(result.discountPct()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }
}
