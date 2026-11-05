package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("""
            SELECT c FROM Comment c JOIN FETCH c.author LEFT JOIN FETCH c.parent
            WHERE c.recipe.id = :recipeId
            ORDER BY c.createdAt, c.id
            """)
    List<Comment> findForRecipe(@Param("recipeId") Long recipeId);
}
