package io.everyonecodes.deliciousnessness.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PlannerWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Monday this week starts on. Each new week is seven days after the last one.
    @Column(nullable = false, unique = true)
    private LocalDate startDate;

    public PlannerWeek(LocalDate startDate) {
        this.startDate = startDate;
    }
}
