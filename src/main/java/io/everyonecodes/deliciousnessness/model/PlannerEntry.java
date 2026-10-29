package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.DayOfWeek;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlannerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Deleting a week deletes its cards (done by the database).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "week_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlannerWeek week;

    // One card is one serving. Null day = not placed yet.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DayOfWeek dayOfWeek;

    // Whose column the card sits in. Null while the card is unplaced.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PlannerPerson person;

    // Where the card sits in its column, from the top. Set whenever a card is placed.
    private Integer sortOrder;

    // Deleting a recipe also removes its cards (done by the database).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    public PlannerEntry(PlannerWeek week, DayOfWeek dayOfWeek, PlannerPerson person, Recipe recipe) {
        this.week = week;
        this.dayOfWeek = dayOfWeek;
        this.person = person;
        this.recipe = recipe;
    }
}
