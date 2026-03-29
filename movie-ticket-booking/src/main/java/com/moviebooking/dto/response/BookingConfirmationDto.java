package com.moviebooking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moviebooking.model.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Booking confirmation returned after initiation or status transitions")
public class BookingConfirmationDto {

    @Schema(description = "Internal UUID of the booking", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID bookingId;

    @Schema(description = "Human-readable booking reference", example = "BK2026032900042")
    private String bookingRef;

    @Schema(description = "Current booking status", example = "AWAITING_PAYMENT")
    private BookingStatus status;

    @Schema(description = "List of reserved seats with real-time status")
    private List<SeatAvailabilityDto> seats;

    @Schema(description = "Sum of base prices before discounts", example = "600.00")
    private BigDecimal totalAmount;

    @Schema(description = "Total discount applied", example = "120.00")
    private BigDecimal discountAmount;

    @Schema(description = "Amount due after discounts", example = "480.00")
    private BigDecimal finalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Timestamp when the seat hold expires; null once confirmed or cancelled", example = "2026-03-29 10:25:00")
    private LocalDateTime expiresAt;

    @Schema(description = "User-facing status message", example = "Seats reserved for 10 minutes. Please complete payment.")
    private String message;
}
