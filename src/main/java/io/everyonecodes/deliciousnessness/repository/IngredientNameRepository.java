package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.dto.IngredientSuggestionDto;
import io.everyonecodes.deliciousnessness.model.IngredientName;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientNameRepository extends JpaRepository<IngredientName, Long> {

    List<IngredientName> findByNameIgnoreCase(String name);

    @Query("""
            SELECT new io.everyonecodes.deliciousnessness.dto.IngredientSuggestionDto(
                i.id, i.canonicalName, n.name, n.languageCode)
            FROM IngredientName n
            JOIN n.ingredient i
            WHERE lower(n.name) LIKE lower(concat('%', :query, '%'))
            ORDER BY CASE
                       WHEN lower(n.name) = lower(:query) THEN 0
                       WHEN lower(n.name) LIKE lower(concat(:query, '%')) THEN 1
                       ELSE 2
                     END,
                     n.name
            """)
    List<IngredientSuggestionDto> searchByName(@Param("query") String query, Limit limit);
}
