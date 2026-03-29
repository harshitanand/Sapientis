package com.moviebooking.repository;

import com.moviebooking.model.Show;
import com.moviebooking.model.enums.ShowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, UUID> {

    /**
     * Read Scenario: fetch all scheduled shows for a movie in a given city on a date.
     * Joins through screen -> theatre to resolve city.
     * Returns distinct shows ordered by start time.
     *
     * Data query uses JOIN FETCH for eager loading. The explicit countQuery avoids
     * Hibernate's "applying pagination in memory" warning that occurs when the
     * JPA provider tries to derive a count from a JOIN FETCH query.
     *
     * language parameter is optional — pass null to skip the filter.
     */
    @Query(
        value = """
            SELECT s FROM Show s
            JOIN FETCH s.screen sc
            JOIN FETCH sc.theatre t
            JOIN FETCH s.movie m
            WHERE s.movie.id     = :movieId
              AND LOWER(t.city)  = LOWER(:city)
              AND s.showDate     = :date
              AND s.status       = :status
              AND t.active       = true
              AND sc.active      = true
              AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
            ORDER BY t.name, s.startTime
            """,
        countQuery = """
            SELECT COUNT(s) FROM Show s
            JOIN s.screen sc
            JOIN sc.theatre t
            JOIN s.movie m
            WHERE s.movie.id    = :movieId
              AND LOWER(t.city) = LOWER(:city)
              AND s.showDate    = :date
              AND s.status      = :status
              AND t.active      = true
              AND sc.active     = true
              AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
            """
    )
    Page<Show> findScheduledShowsForMovieInCity(
            @Param("movieId")  UUID movieId,
            @Param("city")     String city,
            @Param("date")     LocalDate date,
            @Param("status")   ShowStatus status,
            @Param("language") String language,
            Pageable pageable
    );

    @Query(
        value = """
            SELECT s FROM Show s
            JOIN FETCH s.screen sc
            JOIN FETCH sc.theatre t
            JOIN FETCH s.movie m
            WHERE s.movie.id     = :movieId
              AND LOWER(t.city)  = LOWER(:city)
              AND s.showDate     = :date
              AND s.status       = :status
              AND sc.screenType  = :screenType
              AND t.active       = true
              AND sc.active      = true
              AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
            ORDER BY t.name, s.startTime
            """,
        countQuery = """
            SELECT COUNT(s) FROM Show s
            JOIN s.screen sc
            JOIN sc.theatre t
            JOIN s.movie m
            WHERE s.movie.id    = :movieId
              AND LOWER(t.city) = LOWER(:city)
              AND s.showDate    = :date
              AND s.status      = :status
              AND sc.screenType = :screenType
              AND t.active      = true
              AND sc.active     = true
              AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
            """
    )
    Page<Show> findScheduledShowsForMovieInCityByScreenType(
            @Param("movieId")    UUID movieId,
            @Param("city")       String city,
            @Param("date")       LocalDate date,
            @Param("status")     ShowStatus status,
            @Param("screenType") String screenType,
            @Param("language")   String language,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(DISTINCT sc.theatre.id) FROM Show s
            JOIN s.screen sc
            JOIN sc.theatre t
            WHERE s.movie.id    = :movieId
              AND LOWER(t.city) = LOWER(:city)
              AND s.showDate    = :date
              AND s.status      = :status
              AND t.active      = true
              AND sc.active     = true
            """)
    long countDistinctTheatres(
            @Param("movieId") UUID movieId,
            @Param("city")    String city,
            @Param("date")    LocalDate date,
            @Param("status")  ShowStatus status
    );
}
