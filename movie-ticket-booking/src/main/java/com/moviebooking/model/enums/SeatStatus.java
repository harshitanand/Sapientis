package com.moviebooking.model.enums;

public enum SeatStatus {
    AVAILABLE,
    LOCKED,     // held in Redis during payment window
    BOOKED,     // confirmed booking
    BLOCKED     // maintenance / partner-blocked
}
