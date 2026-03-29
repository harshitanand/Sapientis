package com.moviebooking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated list of theatres screening the requested movie")
public class ShowPageResult implements Serializable {

    @Schema(description = "Theatres on the current page, each with their show timings")
    private List<TheatreShowDto> content;

    @Schema(description = "Total number of matching theatres across all pages", example = "3")
    private long totalElements;

    @Schema(description = "Total pages available", example = "1")
    private int totalPages;

    @Schema(description = "Current zero-based page number", example = "0")
    private int pageNumber;

    @Schema(description = "Page size requested", example = "20")
    private int pageSize;
}
