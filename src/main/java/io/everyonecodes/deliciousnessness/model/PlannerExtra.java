package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

// Something a person ate on a day besides the planned dishes. It is one of three kinds:
// a dish (recipe + amount = servings), an ingredient (ingredient + amount + unit),
// or a custom snack (name + nutrients, the values for what was eaten, not per 100 g).
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlannerExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting the week, the recipe or the ingredient deletes the extra (done by the database).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlannerWeek week;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(length = 10, nullable = false)
    private PlannerPerson person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Ingredient ingredient;

    private Double amount;

    @Column(length = 32)
    private String unit;

    @Column(length = 128)
    private String name;

    @ElementCollection
    @CollectionTable(name = "planner_extra_nutrient", joinColumns = @JoinColumn(name = "extra_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @MapKeyColumn(name = "nutrient", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "amount")
    private Map<Nutrient, Double> nutrients = new EnumMap<>(Nutrient.class);

    public PlannerExtra(PlannerWeek week, DayOfWeek dayOfWeek, PlannerPerson person) {
        this.week = week;
        this.dayOfWeek = dayOfWeek;
        this.person = person;
    }
}
