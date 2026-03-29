package com.moviebooking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Manages short-lived distributed seat locks using Redis SETNX (SET if Not eXists).
 *
 * Locking flow:
 *  1. Customer selects seats → acquireAll() atomically sets Redis keys with TTL.
 *  2. Customer completes payment within TTL window → BookingService confirms & releases locks.
 *  3. If TTL expires before payment → keys disappear automatically, seats become re-bookable.
 *
 * Key format: "seat_lock:{seatInventoryId}" → value = idempotencyKey (owner)
 *
 * Thread-safety: SET NX PX is atomic in Redis; no Lua script needed for single-key ops.
 * For multi-seat scenarios we accept that partial locks are possible and roll back via
 * releaseAll() inside the booking transaction if any seat could not be locked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private static final String KEY_PREFIX = "seat_lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${booking.seat-lock.ttl-minutes:10}")
    private int ttlMinutes;

    /**
     * Attempts to acquire locks for all requested seats atomically.
     * Returns false and releases any partial locks if even one seat is already held.
     */
    public boolean acquireAll(List<UUID> seatInventoryIds, String ownerKey) {
        List<String> redisKeys = seatInventoryIds.stream()
                .map(id -> KEY_PREFIX + id)
                .toList();

        List<String> acquired = redisKeys.stream()
                .filter(key -> tryAcquire(key, ownerKey))
                .toList();

        if (acquired.size() < redisKeys.size()) {
            log.warn("Partial seat lock acquired {}/{} for owner={}, rolling back",
                    acquired.size(), redisKeys.size(), ownerKey);
            acquired.forEach(key -> redisTemplate.delete(key));
            return false;
        }

        log.debug("Acquired {} seat locks for owner={}", acquired.size(), ownerKey);
        return true;
    }

    /**
     * Releases all locks owned by the given idempotency key.
     * Safe to call multiple times (idempotent).
     */
    public void releaseAll(List<UUID> seatInventoryIds, String ownerKey) {
        seatInventoryIds.forEach(id -> {
            String key = KEY_PREFIX + id;
            String currentOwner = redisTemplate.opsForValue().get(key);
            if (ownerKey.equals(currentOwner)) {
                redisTemplate.delete(key);
                log.debug("Released seat lock key={}", key);
            }
        });
    }

    public boolean isLockedByOwner(UUID seatInventoryId, String ownerKey) {
        String val = redisTemplate.opsForValue().get(KEY_PREFIX + seatInventoryId);
        return ownerKey.equals(val);
    }

    private boolean tryAcquire(String key, String ownerKey) {
        Boolean set = redisTemplate.opsForValue()
                .setIfAbsent(key, ownerKey, Duration.ofMinutes(ttlMinutes));
        return Boolean.TRUE.equals(set);
    }
}
