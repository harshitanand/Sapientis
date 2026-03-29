package com.moviebooking.repository;

import com.moviebooking.model.SeatInventory;
import com.moviebooking.model.enums.SeatStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatInventoryRepository extends JpaRepository<SeatInventory, UUID> {

    @Query("""
            SELECT si FROM SeatInventory si
            JOIN FETCH si.seat s
            WHERE si.show.id = :showId
            ORDER BY s.rowLabel ASC, s.seatNumber ASC
            """)
    List<SeatInventory> findByShowIdOrderBySeatLayout(@Param("showId") UUID showId);

    @Query("SELECT COUNT(si) FROM SeatInventory si WHERE si.show.id = :showId AND si.status = 'AVAILABLE'")
    long countAvailableSeats(@Param("showId") UUID showId);

    @Query("""
            SELECT si.show.id, COUNT(si) FROM SeatInventory si
            WHERE si.show.id IN :showIds
              AND si.status = 'AVAILABLE'
            GROUP BY si.show.id
            """)
    List<Object[]> countAvailableSeatsByShowIds(@Param("showIds") List<UUID> showIds);

    /**
     * Pessimistic write lock with SKIP LOCKED — prevents double-booking and avoids
     * waiting on rows already locked by another transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(value = """
            SELECT si FROM SeatInventory si
            WHERE si.id IN :ids
              AND si.status = 'AVAILABLE'
            """)
    List<SeatInventory> lockAvailableSeats(@Param("ids") List<UUID> ids);

    @Query("SELECT si FROM SeatInventory si WHERE si.show.id = :showId AND si.status = :status")
    List<SeatInventory> findByShowIdAndStatus(@Param("showId") UUID showId,
                                              @Param("status") SeatStatus status);
}
