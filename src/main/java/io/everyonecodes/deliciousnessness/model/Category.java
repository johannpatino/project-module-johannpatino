package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Category {

    // Recipes in this category are seen only by the owner. Guests never see them or the category.
    public static final String PRIVATE = "private";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    public Category(String name) {
        this.name = name;
    }
}
