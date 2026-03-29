package com.moviebooking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A theatre and all its show timings for the requested movie and date")
public class TheatreShowDto implements Serializable {

    @Schema(description = "UUID of the theatre", example = "b1000000-0000-0000-0000-000000000001")
    private UUID theatreId;

    @Schema(description = "Display name of the theatre", example = "PVR Forum Mall")
    private String theatreName;

    @Schema(description = "Street address", example = "Forum Value Mall, ITPL Main Rd, Whitefield")
    private String address;

    @Schema(description = "City where the theatre is located", example = "Bangalore")
    private String city;

    @Schema(description = "Geographic latitude", example = "12.980801")
    private Double latitude;

    @Schema(description = "Geographic longitude", example = "77.727150")
    private Double longitude;

    @Schema(description = "All scheduled shows at this theatre for the requested movie and date")
    private List<ShowSummaryDto> shows;
}
