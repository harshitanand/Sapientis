package com.moviebooking.dto.response;

import com.moviebooking.model.enums.SeatStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Real-time availability and pricing for a single seat")
public class SeatAvailabilityDto {

    @Schema(description = "UUID of the seat_inventory row — pass this when initiating a booking", example = "9f8e7d6c-...")
    private UUID seatInventoryId;

    @Schema(description = "Row label (A–J)", example = "C")
    private String rowLabel;

    @Schema(description = "Seat number within the row (1–10)", example = "5")
    private int seatNumber;

    @Schema(description = "Seat category: REGULAR | PREMIUM | RECLINER", example = "PREMIUM")
    private String category;

    @Schema(description = "Per-seat price in INR including category surcharge", example = "260.00")
    private BigDecimal price;

    @Schema(description = "Current seat status: AVAILABLE | LOCKED | BOOKED | BLOCKED", example = "AVAILABLE")
    private SeatStatus status;
}
