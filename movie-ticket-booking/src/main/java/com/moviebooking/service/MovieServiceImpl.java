package com.moviebooking.service;

import com.moviebooking.dto.request.ShowSearchRequest;
import com.moviebooking.dto.response.ShowPageResult;
import com.moviebooking.dto.response.ShowSummaryDto;
import com.moviebooking.dto.response.TheatreShowDto;
import com.moviebooking.model.Screen;
import com.moviebooking.model.Show;
import com.moviebooking.model.Theatre;
import com.moviebooking.model.enums.ShowStatus;
import com.moviebooking.repository.ShowRepository;
import com.moviebooking.repository.SeatInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final ShowRepository          showRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    /**
     * Cache key includes movieId + city + date so each combination is cached independently.
     * TTL is configured via booking.cache.show-ttl-minutes (default 3 min).
     */
    @Cacheable(
        cacheNames = "shows_by_city",
        key         = "#request.movieId + ':' + #request.city + ':' + #request.date",
        unless      = "#result.content.isEmpty()"
    )
    @Transactional(readOnly = true)
    @Override
    public ShowPageResult findShowsByMovieAndCity(ShowSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());

        Page<Show> showPage = StringUtils.hasText(request.getScreenType())
                ? showRepository.findScheduledShowsForMovieInCityByScreenType(
                        request.getMovieId(), request.getCity(), request.getDate(),
                        ShowStatus.SCHEDULED, request.getScreenType(), request.getLanguage(), pageable)
                : showRepository.findScheduledShowsForMovieInCity(
                        request.getMovieId(), request.getCity(), request.getDate(),
                        ShowStatus.SCHEDULED, request.getLanguage(), pageable);

        log.debug("Found {} shows for movieId={} city={} date={}",
                showPage.getTotalElements(), request.getMovieId(), request.getCity(), request.getDate());

        // Bulk-fetch available seat counts to avoid N+1 per show
        List<UUID> showIds = showPage.getContent().stream().map(Show::getId).toList();
        Map<UUID, Long> availableCountByShow = seatInventoryRepository
                .countAvailableSeatsByShowIds(showIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]));

        // Group shows by theatre
        Map<UUID, List<Show>> byTheatre = showPage.getContent().stream()
                .collect(Collectors.groupingBy(s -> s.getScreen().getTheatre().getId(),
                         LinkedHashMap::new, Collectors.toList()));

        List<TheatreShowDto> theatreDtos = byTheatre.entrySet().stream()
                .map(entry -> toTheatreShowDto(entry.getValue(), availableCountByShow))
                .toList();

        long totalTheatres = showRepository.countDistinctTheatres(
                request.getMovieId(), request.getCity(), request.getDate(), ShowStatus.SCHEDULED);

        return ShowPageResult.builder()
                .content(theatreDtos)
                .totalElements(totalTheatres)
                .totalPages((int) Math.ceil((double) totalTheatres / pageable.getPageSize()))
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .build();
    }

    // -------------------------------------------------------------------------

    private TheatreShowDto toTheatreShowDto(List<Show> shows, Map<UUID, Long> availableCountByShow) {
        Show first    = shows.get(0);
        Theatre t     = first.getScreen().getTheatre();

        List<ShowSummaryDto> summaries = shows.stream()
                .map(show -> toShowSummaryDto(show, availableCountByShow))
                .toList();

        return TheatreShowDto.builder()
                .theatreId(t.getId())
                .theatreName(t.getName())
                .address(t.getAddress())
                .city(t.getCity())
                .latitude(t.getLatitude())
                .longitude(t.getLongitude())
                .shows(summaries)
                .build();
    }

    private ShowSummaryDto toShowSummaryDto(Show show, Map<UUID, Long> availableCountByShow) {
        Screen screen       = show.getScreen();
        long availableSeats = availableCountByShow.getOrDefault(show.getId(), 0L);

        return ShowSummaryDto.builder()
                .showId(show.getId())
                .startTime(show.getStartTime())
                .endTime(show.getEndTime())
                .slot(show.getSlot())
                .screenName(screen.getName())
                .screenType(screen.getScreenType())
                .basePrice(show.getBasePrice())
                .availableSeats(availableSeats)
                .totalSeats(screen.getTotalSeats())
                .build();
    }
}
