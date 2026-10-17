package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.dto.IngredientDto;
import io.everyonecodes.deliciousnessness.dto.IngredientUseDto;
import io.everyonecodes.deliciousnessness.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<IngredientDto> findByIdIn(Collection<Long> ids);

    Optional<Ingredient> findByCanonicalNameIgnoreCase(String canonicalName);

    // One row for every ingredient line of every recipe.
    @Query("""
            SELECT new io.everyonecodes.deliciousnessness.dto.IngredientUseDto(
                ri.ingredient.id, ri.ingredient.canonicalName, ri.unit, ri.quantity, ri.recipe.id)
            FROM RecipeIngredient ri
            """)
    List<IngredientUseDto> findAllUses();

}
