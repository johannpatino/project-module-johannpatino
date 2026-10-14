package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.PlannerWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlannerWeekRepository extends JpaRepository<PlannerWeek, Long> {

    List<PlannerWeek> findAllByOrderByStartDateAsc();

    // Weeks that have not started yet, plus this week.
    List<PlannerWeek> findAllByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate date);

    // Weeks that are over, newest first.
    List<PlannerWeek> findAllByStartDateBeforeOrderByStartDateDesc(LocalDate date);

    Optional<PlannerWeek> findFirstByOrderByStartDateAsc();

    Optional<PlannerWeek> findFirstByOrderByStartDateDesc();
}
