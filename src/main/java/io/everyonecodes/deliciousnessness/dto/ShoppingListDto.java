package io.everyonecodes.deliciousnessness.dto;

import java.time.LocalDate;
import java.util.List;

public record ShoppingListDto(Long weekId, LocalDate startDate, List<ShoppingGroupDto> groups) {
}
