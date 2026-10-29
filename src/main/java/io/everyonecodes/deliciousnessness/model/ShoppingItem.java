package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a week deletes its shopping list (done by the database).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlannerWeek week;

    @Column(nullable = false, length = 128)
    private String name;

    // Free text on purpose: "400 g", "2 bunches", "1 jar". Easy to type and easy to edit.
    @Column(length = 64)
    private String amount;

    // The ingredient this line is about, when it is known. Lines of one ingredient are one line on the list.
    private Long ingredientId;

    // The amount as a number and a unit (the spelling the app uses, e.g. "clove"). Null when the amount is text.
    private Double quantity;

    @Column(length = 32)
    private String unit;

    // true = worked out from the planned recipes; false = added by hand.
    private boolean fromRecipes;

    public ShoppingItem(PlannerWeek week, String name, String amount, boolean fromRecipes) {
        this.week = week;
        this.name = name;
        this.amount = amount;
        this.fromRecipes = fromRecipes;
    }
}
