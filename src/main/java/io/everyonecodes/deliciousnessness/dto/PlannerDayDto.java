package io.everyonecodes.deliciousnessness.dto;

import java.time.DayOfWeek;
import java.util.List;

public record PlannerDayDto(DayOfWeek day, List<PlannerEntryDto> ela, List<PlannerEntryDto> johann) {

    // True when nothing is planned on this day for either person.
    public boolean empty() {
        return ela.isEmpty() && johann.isEmpty();
    }
}
