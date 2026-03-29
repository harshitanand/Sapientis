package com.moviebooking.repository;

import com.moviebooking.model.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    @Query("""
            SELECT m FROM Movie m
            WHERE m.active = true
              AND (:language IS NULL OR LOWER(m.language) = LOWER(:language))
              AND (:genre    IS NULL OR LOWER(m.genre)    = LOWER(:genre))
            ORDER BY m.releaseDate DESC
            """)
    Page<Movie> findActiveMovies(@Param("language") String language,
                                 @Param("genre") String genre,
                                 Pageable pageable);
}
