package com.moviebooking.model.enums;

import java.time.LocalTime;

public enum ShowSlot {
    MORNING,    // before 12:00
    AFTERNOON,  // 12:00 – 17:00
    EVENING,    // 17:00 – 21:00
    NIGHT;      // 21:00 onwards

    public static ShowSlot from(LocalTime startTime) {
        int hour = startTime.getHour();
        if (hour < 12)  return MORNING;
        if (hour < 17)  return AFTERNOON;
        if (hour < 21)  return EVENING;
        return NIGHT;
    }
}
