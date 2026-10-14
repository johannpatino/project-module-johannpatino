package io.everyonecodes.deliciousnessness.model;

import java.time.LocalDate;

public enum Season {
    SPRING, SUMMER, FALL, WINTER;

    // The season for today's date (calendar seasons, northern hemisphere).
    public static Season current() {
        return switch (LocalDate.now().getMonth()) {
            case DECEMBER, JANUARY, FEBRUARY -> WINTER;
            case MARCH, APRIL, MAY -> SPRING;
            case JUNE, JULY, AUGUST -> SUMMER;
            default -> FALL;
        };
    }
}
