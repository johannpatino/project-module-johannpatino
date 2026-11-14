package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.RecipeImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeImageRepository extends JpaRepository<RecipeImage, Long> {
}
