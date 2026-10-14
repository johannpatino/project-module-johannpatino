package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.ShoppingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

    List<ShoppingItem> findAllByWeekIdOrderByIdAsc(Long weekId);

    // Only the lines that came from the recipes, so lines added by hand survive a rebuild.
    void deleteByWeekIdAndFromRecipesTrue(Long weekId);

    // Removes every line of the week, for the Clear button.
    void deleteByWeekId(Long weekId);

    // Makes sure an edit or delete can only touch a line that belongs to the week in the URL.
    Optional<ShoppingItem> findByIdAndWeekId(Long id, Long weekId);
}
