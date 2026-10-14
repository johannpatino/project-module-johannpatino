package io.everyonecodes.deliciousnessness.dto;

import java.time.LocalDate;
import java.util.List;

// unplaced = cards that are in the week but not on a day yet.
public record PlannerWeekDto(Long id, LocalDate startDate, List<PlannerDayDto> days, List<PlannerEntryDto> unplaced) {
}
