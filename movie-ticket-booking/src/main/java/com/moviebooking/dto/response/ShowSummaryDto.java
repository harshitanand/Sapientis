package com.moviebooking.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moviebooking.model.enums.ShowSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary of a single show timing within a theatre")
public class ShowSummaryDto implements Serializable {

    @Schema(description = "UUID of the show — use this in the booking request", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID showId;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Show start time (HH:mm)", example = "18:00")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Estimated end time (HH:mm)", example = "21:00")
    private LocalTime endTime;

    @Schema(description = "Time-of-day slot: MORNING | AFTERNOON | EVENING | NIGHT", example = "EVENING")
    private ShowSlot slot;

    @Schema(description = "Screen name within the theatre", example = "Screen 2")
    private String screenName;

    @Schema(description = "Screen format: 2D | 3D | IMAX | 4DX", example = "3D")
    private String screenType;

    @Schema(description = "Base price per seat in INR (REGULAR category)", example = "250.00")
    private BigDecimal basePrice;

    @Schema(description = "Number of seats currently available for this show", example = "86")
    private long availableSeats;

    @Schema(description = "Total seat capacity of the screen", example = "100")
    private int totalSeats;
}
