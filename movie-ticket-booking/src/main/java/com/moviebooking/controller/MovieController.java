package com.moviebooking.controller;

import com.moviebooking.dto.request.ShowSearchRequest;
import com.moviebooking.dto.response.ApiResponse;
import com.moviebooking.dto.response.ShowPageResult;
import com.moviebooking.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * READ SCENARIO:
 * Browse theatres currently running a movie in a given city,
 * including show timings for the selected date.
 *
 * GET /shows?movieId=&city=&date=
 */
@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
@Tag(name = "Movie Shows", description = "Browse show schedules and theatre availability")
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    @Operation(
        summary     = "Find shows by movie and city",
        description = "Returns all theatres screening the specified movie in the given city "
                    + "on the requested date, with per-show seat availability. Results are "
                    + "cached in Redis for 3 minutes per unique (movieId, city, date) combination."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Shows retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — missing or invalid query parameters",
            content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @Parameters({
        @Parameter(name = "movieId",    description = "UUID of the movie",                                    required = true,  example = "d1000000-0000-0000-0000-000000000001"),
        @Parameter(name = "city",       description = "City name (case-insensitive)",                         required = true,  example = "Bangalore"),
        @Parameter(name = "date",       description = "Show date in ISO-8601 format (yyyy-MM-dd)",            required = true,  example = "2026-03-30"),
        @Parameter(name = "language",   description = "Optional language filter, e.g. Hindi | Tamil | Telugu",required = false, example = "Hindi"),
        @Parameter(name = "screenType", description = "Optional screen type filter: 2D | 3D | IMAX | 4DX",   required = false, example = "3D"),
        @Parameter(name = "page",       description = "Zero-based page number",                               required = false, example = "0"),
        @Parameter(name = "size",       description = "Page size (max 100)",                                  required = false, example = "20")
    })
    public ResponseEntity<ApiResponse<ShowPageResult>> findShows(
            @Valid @ModelAttribute ShowSearchRequest request) {

        ShowPageResult result = movieService.findShowsByMovieAndCity(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
