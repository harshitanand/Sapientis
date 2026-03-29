package com.moviebooking.repository;

import com.moviebooking.model.Booking;
import com.moviebooking.model.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByBookingRef(String bookingRef);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = :status
              AND b.expiresAt < :now
            """)
    List<Booking> findExpiredBookings(@Param("status") BookingStatus status,
                                      @Param("now") LocalDateTime now);
}
