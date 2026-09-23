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
            SELECT DISTINCT ri.recipe.id
            FROM RecipeIngredient  ri
            WHERE ri.ingredient.id IN (
                        SELECT n.ingredient.id FROM IngredientName n
                        WHERE lower(n.name) LIKE lower(concat('%', :term, '%'))
                        )
            """)
    List<Long> findRecipeIdsByIngredientNameContaining(@Param("term") String term);

    List<RecipeSummaryDto> findByIdInOrderByCreatedAtDesc(Collection<Long> recipeIds);

    @Query("""
            SELECT r.id FROM Recipe r
            WHERE lower(r.recipeName) LIKE lower(concat('%', :query, '%'))
            """)
    List<Long> findIdsByNameContaining(@Param("query") String query);

    @Query("""
            SELECT r.id FROM Recipe r
            JOIN r.categories c
            WHERE c.id IN :categoryIds
            GROUP BY r.id
            HAVING COUNT(DISTINCT c.id) = :requiredCount
            """)
    List<Long> findIdsByAllCategories(@Param("categoryIds") Collection<Long> categoryIds, @Param("requiredCount") long requiredCount);

    @Query("""
            SELECT r.id FROM Recipe r
            JOIN r.seasons s
            WHERE s IN :seasons
            GROUP BY r.id
            HAVING COUNT(DISTINCT s) = :requiredCount
            """)
    List<Long> findIdsByAllSeasons(@Param("seasons") Collection<Season> seasons, @Param("requiredCount") long requiredCount);

    List<Recipe> findByCategoriesId(Long categoryId);
}
