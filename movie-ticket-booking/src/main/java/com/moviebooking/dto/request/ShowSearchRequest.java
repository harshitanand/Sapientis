package com.moviebooking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Query parameters for browsing shows by movie, city, and date")
public class ShowSearchRequest {

    @NotNull(message = "movieId is required")
    @Schema(description = "UUID of the movie to search shows for", example = "d1000000-0000-0000-0000-000000000001", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID movieId;

    @NotBlank(message = "city is required")
    @Schema(description = "City name (case-insensitive)", example = "Bangalore", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    @NotNull(message = "date is required")
    @FutureOrPresent(message = "date must be today or in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Show date in ISO-8601 format (yyyy-MM-dd), must be today or future", example = "2026-03-30", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;

    @Schema(description = "Optional language filter (case-insensitive), e.g. Hindi, Tamil", example = "Hindi")
    private String language;

    @Schema(description = "Optional screen type filter: 2D | 3D | IMAX | 4DX", example = "3D")
    private String screenType;

    @Schema(description = "Zero-based page number for pagination", example = "0", defaultValue = "0")
    private int page = 0;

    @Schema(description = "Page size (max 100)", example = "20", defaultValue = "20")
    private int size = 20;
}
