package io.everyonecodes.deliciousnessness.model;

import java.time.LocalDate;
import java.util.List;

public enum Season {
    SPRING, SUMMER, FALL, WINTER;

    // The two seasons shown together on the front page: fall and winter from September to February,
    // spring and summer from March to August.
    public static List<Season> inSeasonNow() {
        return switch (LocalDate.now().getMonth()) {
            case MARCH, APRIL, MAY, JUNE, JULY, AUGUST -> List.of(SPRING, SUMMER);
            default -> List.of(FALL, WINTER);
        };
    }

    // "Fall", "Summer", ... for headings.
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
