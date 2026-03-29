package com.moviebooking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API envelope returned by every endpoint")
public class ApiResponse<T> {

    @Schema(description = "true when the request succeeded, false otherwise", example = "true")
    private boolean success;

    @Schema(description = "Optional human-readable status message", example = "Booking confirmed! Enjoy the show.")
    private String message;

    @Schema(description = "Response payload; null on error responses")
    private T data;

    @Schema(description = "Error detail; present only when success=false")
    private ErrorDetail error;

    @Builder.Default
    @Schema(description = "UTC timestamp of the response", example = "2026-03-29T10:15:30Z")
    private Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> fail(String message, ErrorDetail error) {
        return ApiResponse.<T>builder().success(false).message(message).error(error).build();
    }

    @Data
    @Builder
    @Schema(description = "Machine-readable error detail")
    public static class ErrorDetail {

        @Schema(description = "Error code constant", example = "SEAT_UNAVAILABLE")
        private String code;

        @Schema(description = "Human-readable detail of the error", example = "Seat A3 is already held by another booking")
        private String detail;
    }
}
