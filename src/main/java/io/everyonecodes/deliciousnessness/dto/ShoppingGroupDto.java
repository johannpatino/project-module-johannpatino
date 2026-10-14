package io.everyonecodes.deliciousnessness.dto;

import java.util.List;

// One line of the combined list: the total, plus the stored lines it was made from.
public record ShoppingGroupDto(String name, String amount, List<ShoppingItemDto> sources) {
}
