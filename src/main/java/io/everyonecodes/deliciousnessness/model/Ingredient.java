package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String canonicalName;

    /** Nutrition per 100 g. A nutrient with no value is simply not in the map. */
    @ElementCollection
    @CollectionTable(name = "ingredient_nutrient", joinColumns = @JoinColumn(name = "ingredient_id"))
    @MapKeyColumn(name = "nutrient", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "per_100g")
    private Map<Nutrient, Double> nutrients = new HashMap<>();

    public Ingredient(String canonicalName) {
        this.canonicalName = canonicalName;
    }
}
