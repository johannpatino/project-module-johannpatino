package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// How many grams one unit of an ingredient weighs: 1 tbsp of olive oil = 13 g.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"ingredient_id", "unit"}))
@Getter
@Setter
@NoArgsConstructor
public class IngredientUnitWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ingredient ingredient;

    /** Lower case and trimmed. Empty means the recipe line has no unit ("3 onions"). */
    @Column(nullable = false, length = 32)
    private String unit;

    @Column(nullable = false)
    private double grams;

    public IngredientUnitWeight(Ingredient ingredient, String unit, double grams) {
        this.ingredient = ingredient;
        this.unit = unit;
        this.grams = grams;
    }
}
