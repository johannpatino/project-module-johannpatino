package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.PlannerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PlannerEntryRepository extends JpaRepository<PlannerEntry, Long> {

    List<PlannerEntry> findAllByWeekIdOrderByIdAsc(Long weekId);

    List<PlannerEntry> findAllByWeekStartDateBefore(LocalDate date);

    List<PlannerEntry> findAllByWeekIsNull();

    // Entries from before the two columns existed: on a day, but in nobody's column.
    List<PlannerEntry> findAllByDayOfWeekIsNotNullAndPersonIsNull();

    void deleteByWeekId(Long weekId);
}
