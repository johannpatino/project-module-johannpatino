package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.dto.RecipeSummaryDto;
import io.everyonecodes.deliciousnessness.model.Recipe;
import io.everyonecodes.deliciousnessness.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<RecipeSummaryDto> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT ri.recipe.id
            FROM RecipeIngredient  ri
            WHERE ri.ingredient.id IN :ingredientIds
            GROUP BY ri.recipe.id
            HAVING COUNT(DISTINCT ri.ingredient.id) = :requiredCount
            """)
    List<Long> findRecipeIdsWithAllIngredients(@Param("ingredientIds") Collection<Long> ingredientIds, @Param("requiredCount") long requiredCount);

    List<RecipeSummaryDto> findByIdInOrderByCreatedAtDesc(Collection<Long> recipeIds);

    @Query("""
            SELECT r.id FROM Recipe r
            WHERE lower(r.recipeName) LIKE lower(concat('%', :query, '%'))
            """)
    List<Long> findIdsByNameContaining(@Param("query") String query);

    @Query("""
            SELECT DISTINCT r.id FROM Recipe r
            JOIN r.categories c
            WHERE c.id IN :categoryIds
            """)
    List<Long> findIdsByAnyCategory(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("""
            SELECT DISTINCT r.id FROM Recipe r
            JOIN r.seasons s
            WHERE s IN :seasons
            """)
    List<Long> findIdsByAnySeason(@Param("seasons") Collection<Season> seasons);

    List<Recipe> findByCategoriesId(Long categoryId);
}
