package io.everyonecodes.deliciousnessness.repository;

import io.everyonecodes.deliciousnessness.model.PlannerExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface PlannerExtraRepository extends JpaRepository<PlannerExtra, Long> {

    List<PlannerExtra> findAllByWeekIdAndDayOfWeekOrderByIdAsc(Long weekId, DayOfWeek day);
}
