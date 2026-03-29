package com.moviebooking.event;

import com.moviebooking.model.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BookingEvent {

    public enum Type { INITIATED, CONFIRMED, CANCELLED, EXPIRED }

    private Type        type;
    private UUID        bookingId;
    private String      bookingRef;
    private UUID        customerId;
    private UUID        showId;
    private BookingStatus status;
    private BigDecimal  finalAmount;
    private Instant     occurredAt;
}
