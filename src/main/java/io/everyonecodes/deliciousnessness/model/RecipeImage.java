package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

// A picture uploaded from the recipe form. Kept in the database, so it lives with the rest of the data
// and the backup. A recipe points to it by its link, /images/{id}.
@Entity
@Getter
@NoArgsConstructor
public class RecipeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    public RecipeImage(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }
}
