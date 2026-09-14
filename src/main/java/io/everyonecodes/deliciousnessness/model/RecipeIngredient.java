package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    private Double quantity;

    @Column(length = 32)
    private String unit;

    @Column(length = 128)
    private String preparation;

    @Column(length = 128)
    private String displayName;

    /** Optional heading this line sits under, e.g. "For the tofu". Null means no heading. */
    @Column(length = 64)
    private String section;

    public RecipeIngredient(Ingredient ingredient, String displayName, Double quantity, String unit, String preparation) {
        this.ingredient = ingredient;
        this.displayName = displayName;
        this.quantity = quantity;
        this.unit = unit;
        this.preparation = preparation;
    }
}
