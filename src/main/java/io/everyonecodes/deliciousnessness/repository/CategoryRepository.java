package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.dto.CategoryDto;
import io.everyonecodes.deliciousnessness.dto.CategoryUsageDto;
import io.everyonecodes.deliciousnessness.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<CategoryDto> findAllByOrderByNameAsc();

    Optional<Category> findByNameIgnoreCase(String name);

    @Query("""
            SELECT new io.everyonecodes.deliciousnessness.dto.CategoryUsageDto(
            c.id, c.name,
            (SELECT count(r) FROM Recipe r JOIN 
            r.categories rc WHERE rc.id = c.id))
            FROM Category c 
            ORDER BY c.name
            """)
    List<CategoryUsageDto> findAllWithUsage();
}
