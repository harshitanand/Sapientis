package com.moviebooking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Request body for initiating a seat reservation")
public class BookingRequest {

    @NotNull(message = "showId is required")
    @Schema(description = "UUID of the show to book", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID showId;

    @NotNull(message = "customerId is required")
    @Schema(description = "UUID of the customer making the booking", example = "e1000000-0000-0000-0000-000000000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID customerId;

    @NotEmpty(message = "at least one seat must be selected")
    @Size(max = 10, message = "maximum 10 seats per booking")
    @Schema(description = "List of seat_inventory UUIDs to reserve (1–10 seats)", example = "[\"a1b2c3d4-...\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<UUID> seatInventoryIds;

    /**
     * Client-generated idempotency key.
     * Must be unique per booking attempt; resubmitting the same key
     * returns the original response without creating a duplicate booking.
     */
    @NotBlank
    @Size(max = 100)
    @Schema(description = "Client-generated idempotency key — resubmitting the same key returns the original response",
            example = "order-session-abc123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idempotencyKey;
}
