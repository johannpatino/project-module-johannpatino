package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.IngredientUnitWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientUnitWeightRepository extends JpaRepository<IngredientUnitWeight, Long> {

    Optional<IngredientUnitWeight> findByIngredientIdAndUnit(Long ingredientId, String unit);

    List<IngredientUnitWeight> findAllByIngredientIdIn(Collection<Long> ingredientIds);
}
