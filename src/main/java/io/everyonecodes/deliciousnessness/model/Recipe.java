package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipeName;

    @Column(nullable = false)
    private Integer servings;

    @Column(nullable = false)
    private Integer cookTimeMinutes;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String instructions;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_seasons", joinColumns = @JoinColumn(name = "recipe_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "season", length = 20)
    private Set<Season> seasons = new HashSet<>();

    @Column(length = 2048)
    private String imageUrl;

    @Column(length = 2048)
    private String sourceUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(name = "recipe_categories",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> recipeIngredients = new ArrayList<>();

    public boolean isPrivate() {
        return categories.stream().anyMatch(c -> Category.PRIVATE.equalsIgnoreCase(c.getName()));
    }

    public void addIngredient(RecipeIngredient recipeIngredient) {
        recipeIngredients.add(recipeIngredient);
        recipeIngredient.setRecipe(this);
    }

    public void removeIngredient(RecipeIngredient recipeIngredient) {
        recipeIngredients.remove(recipeIngredient);
        recipeIngredient.setRecipe(null);
    }
}