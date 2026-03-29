package com.moviebooking.service;

import com.moviebooking.dto.request.ShowSearchRequest;
import com.moviebooking.dto.response.ShowPageResult;

public interface MovieService {

    /**
     * Read scenario: return all theatres screening the given movie in the requested
     * city on the specified date, grouped by theatre with per-show availability.
     */
    ShowPageResult findShowsByMovieAndCity(ShowSearchRequest request);
}
