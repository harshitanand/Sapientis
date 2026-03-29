package com.moviebooking.controller;

import com.moviebooking.dto.request.BookingRequest;
import com.moviebooking.dto.response.ApiResponse;
import com.moviebooking.dto.response.BookingConfirmationDto;
import com.moviebooking.dto.response.SeatAvailabilityDto;
import com.moviebooking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * WRITE SCENARIO:
 * Book movie tickets by selecting a theatre, show timing, and preferred seats.
 *
 * POST /bookings            — initiate (seat reservation + pricing)
 * POST /bookings/{ref}/confirm — confirm after payment callback
 * POST /bookings/{ref}/cancel  — customer or admin cancellation
 * GET  /bookings/shows/{showId}/seats — seat map for a show
 */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
@Tag(name = "Ticket Booking", description = "Seat selection, booking lifecycle, and cancellation")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary     = "Initiate a booking",
        description = "Reserves the requested seats (Redis SETNX lock + DB SELECT FOR UPDATE SKIP LOCKED) "
                    + "and returns a booking in AWAITING_PAYMENT state. The seat hold expires in 10 minutes. "
                    + "Submitting the same idempotencyKey twice returns the original response."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Booking initiated — seats reserved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "One or more seats already held",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookingConfirmationDto>> initiateBooking(
            @Valid @RequestBody BookingRequest request) {

        BookingConfirmationDto dto = bookingService.initiateBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PostMapping("/{bookingRef}/confirm")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary     = "Confirm booking post-payment",
        description = "Called by the payment gateway callback after a successful charge. "
                    + "Transitions the booking from AWAITING_PAYMENT to CONFIRMED "
                    + "and promotes seat status from LOCKED to BOOKED. Idempotent."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking confirmed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Booking already expired or in wrong state",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking reference not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookingConfirmationDto>> confirmBooking(
            @Parameter(description = "Human-readable booking reference", example = "BK2026032900042")
            @PathVariable String bookingRef,
            @Parameter(description = "Payment gateway transaction reference", required = true, example = "PAY-XYZ-123456")
            @RequestParam  String paymentRef) {

        BookingConfirmationDto dto = bookingService.confirmBooking(bookingRef, paymentRef);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/{bookingRef}/cancel")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary     = "Cancel a booking",
        description = "Cancels the booking and releases held seats. The authenticated principal "
                    + "must be the customer who created the booking (identified by UUID username). "
                    + "Cancelling a CONFIRMED booking logs a warning for refund processing."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking cancelled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid principal format"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not the booking owner",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<BookingConfirmationDto>> cancelBooking(
            @Parameter(description = "Human-readable booking reference", example = "BK2026032900042")
            @PathVariable String bookingRef,
            @AuthenticationPrincipal UserDetails principal) {

        UUID requesterId;
        try {
            requesterId = UUID.fromString(principal.getUsername());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail("Invalid principal format",
                            ApiResponse.ErrorDetail.builder()
                                    .code("INVALID_PRINCIPAL").detail("Username is not a valid UUID").build()));
        }
        BookingConfirmationDto dto = bookingService.cancelBooking(bookingRef, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @GetMapping("/shows/{showId}/seats")
    @Operation(
        summary     = "Get seat map for a show",
        description = "Returns the full seat layout (rows A–J, seats 1–10) with real-time availability "
                    + "status. No authentication required — publicly accessible."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Seat map returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Show not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<List<SeatAvailabilityDto>>> getSeatMap(
            @Parameter(description = "UUID of the show", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable UUID showId) {

        List<SeatAvailabilityDto> seats = bookingService.getSeatMap(showId);
        return ResponseEntity.ok(ApiResponse.ok(seats));
    }
}
