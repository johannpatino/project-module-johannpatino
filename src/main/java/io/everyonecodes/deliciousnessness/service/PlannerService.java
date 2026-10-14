package io.everyonecodes.deliciousnessness.service;

import io.everyonecodes.deliciousnessness.dto.PastRecipeDto;
import io.everyonecodes.deliciousnessness.dto.PlannerDayDto;
import io.everyonecodes.deliciousnessness.dto.PlannerEntryDto;
import io.everyonecodes.deliciousnessness.dto.PlannerRecipeDto;
import io.everyonecodes.deliciousnessness.dto.PlannerWeekDto;
import io.everyonecodes.deliciousnessness.model.PlannerEntry;
import io.everyonecodes.deliciousnessness.model.PlannerPerson;
import io.everyonecodes.deliciousnessness.model.PlannerWeek;
import io.everyonecodes.deliciousnessness.model.Recipe;
import io.everyonecodes.deliciousnessness.repository.PlannerEntryRepository;
import io.everyonecodes.deliciousnessness.repository.PlannerWeekRepository;
import io.everyonecodes.deliciousnessness.repository.RecipeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlannerService {

    private final PlannerEntryRepository plannerEntryRepository;
    private final PlannerWeekRepository plannerWeekRepository;
    private final RecipeRepository recipeRepository;

    public PlannerService(PlannerEntryRepository plannerEntryRepository,
                          PlannerWeekRepository plannerWeekRepository,
                          RecipeRepository recipeRepository) {
        this.plannerEntryRepository = plannerEntryRepository;
        this.plannerWeekRepository = plannerWeekRepository;
        this.recipeRepository = recipeRepository;
    }

    // The Monday of the current week. Weeks that start before this are past.
    public static LocalDate currentWeekStart() {
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    // A recipe gets one card per serving, and at least one.
    public static int servingsOf(Recipe recipe) {
        Integer servings = recipe.getServings();
        return servings == null || servings < 1 ? 1 : servings;
    }

    // Upcoming weeks (this week and later), oldest first.
    @Transactional(readOnly = true)
    public List<PlannerWeekDto> findWeeks() {
        return plannerWeekRepository.findAllByStartDateGreaterThanEqualOrderByStartDateAsc(currentWeekStart()).stream()
                .map(this::weekDto)
                .toList();
    }

    // Upcoming weeks without their cards, for the week picker in the popups.
    @Transactional(readOnly = true)
    public List<PlannerWeekDto> findWeekOptions() {
        return plannerWeekRepository.findAllByStartDateGreaterThanEqualOrderByStartDateAsc(currentWeekStart()).stream()
                .map(week -> new PlannerWeekDto(week.getId(), week.getStartDate(), List.of(), List.of()))
                .toList();
    }

    // Past weeks, newest first.
    @Transactional(readOnly = true)
    public List<PlannerWeekDto> findPastWeeks() {
        return plannerWeekRepository.findAllByStartDateBeforeOrderByStartDateDesc(currentWeekStart()).stream()
                .map(this::weekDto)
                .toList();
    }

    // Every recipe placed on a day in a past week, with the most recent date it was on.
    @Transactional(readOnly = true)
    public List<PastRecipeDto> findLastPlanned() {
        Map<Long, PastRecipeDto> lastByRecipe = new HashMap<>();
        for (PlannerEntry card : plannerEntryRepository.findAllByWeekStartDateBefore(currentWeekStart())) {
            if (card.getDayOfWeek() == null) continue; // never placed on a day
            LocalDate date = card.getWeek().getStartDate().plusDays(card.getDayOfWeek().ordinal());
            Recipe recipe = card.getRecipe();
            lastByRecipe.merge(recipe.getId(),
                    new PastRecipeDto(recipe.getId(), recipe.getRecipeName(), date),
                    (current, candidate) -> candidate.lastPlanned().isAfter(current.lastPlanned()) ? candidate : current);
        }
        return lastByRecipe.values().stream()
                .sorted(Comparator.comparing(PastRecipeDto::lastPlanned).reversed()
                        .thenComparing(PastRecipeDto::recipeName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlannerRecipeDto> findRecipeOptions() {
        return recipeRepository.findAll(Sort.by("recipeName")).stream()
                .map(recipe -> new PlannerRecipeDto(
                        recipe.getId(),
                        recipe.getRecipeName(),
                        recipe.getImageUrl(),
                        recipe.getCategories().stream().map(category -> category.getId()).toList(),
                        List.copyOf(recipe.getSeasons())))
                .toList();
    }

    // Adds one unplaced card per serving of each recipe, to each upcoming week.
    @Transactional
    public void addEntries(Collection<Long> weekIds, Collection<Long> recipeIds) {
        if (weekIds == null || recipeIds == null) return;
        List<PlannerWeek> weeks = plannerWeekRepository.findAllById(weekIds).stream()
                .filter(PlannerService::isUpcoming)
                .toList();
        List<Recipe> recipes = recipeRepository.findAllById(recipeIds);
        for (PlannerWeek week : weeks) {
            for (Recipe recipe : recipes) {
                for (int serving = 0; serving < servingsOf(recipe); serving++) {
                    plannerEntryRepository.save(new PlannerEntry(week, null, null, recipe));
                }
            }
        }
    }

    // Drag and drop. Puts a card on a day in one person's column, or among the unplaced cards when
    // day and person are empty. weekId moves the card to another upcoming week, so a leftover portion
    // can move from one week to the next. Only upcoming weeks can change.
    @Transactional
    public void moveEntry(Long entryId, Long weekId, DayOfWeek day, PlannerPerson person) {
        if (day != null && person == null) return;
        PlannerWeek targetWeek = weekId == null
                ? null
                : plannerWeekRepository.findById(weekId).filter(PlannerService::isUpcoming).orElse(null);
        if (weekId != null && targetWeek == null) return;
        plannerEntryRepository.findById(entryId)
                .filter(card -> card.getWeek() != null && isUpcoming(card.getWeek()))
                .ifPresent(card -> {
                    if (targetWeek != null) card.setWeek(targetWeek);
                    card.setDayOfWeek(day);
                    card.setPerson(day == null ? null : person);
                });
    }

    // ifPresent, so a double-click on × doesn't error on the second press. Past cards stay.
    @Transactional
    public void removeEntry(Long entryId) {
        plannerEntryRepository.findById(entryId)
                .filter(card -> card.getWeek() != null && isUpcoming(card.getWeek()))
                .ifPresent(plannerEntryRepository::delete);
    }

    @Transactional
    public void clearWeek(Long weekId) {
        plannerWeekRepository.findById(weekId)
                .filter(PlannerService::isUpcoming)
                .ifPresent(week -> plannerEntryRepository.deleteByWeekId(weekId));
    }

    // The new week starts seven days after the latest one, but never in the past.
    @Transactional
    public void addWeek() {
        LocalDate fromLatest = plannerWeekRepository.findFirstByOrderByStartDateDesc()
                .map(latest -> latest.getStartDate().plusWeeks(1))
                .orElse(currentWeekStart());
        LocalDate start = fromLatest.isBefore(currentWeekStart()) ? currentWeekStart() : fromLatest;
        plannerWeekRepository.save(new PlannerWeek(start));
    }

    // Keeps at least one upcoming week. Past weeks can't be removed.
    @Transactional
    public void removeWeek(Long weekId) {
        if (plannerWeekRepository.findAllByStartDateGreaterThanEqualOrderByStartDateAsc(currentWeekStart()).size() <= 1) {
            return;
        }
        plannerWeekRepository.findById(weekId)
                .filter(PlannerService::isUpcoming)
                .ifPresent(plannerWeekRepository::delete);
    }

    // At startup: make sure there is an upcoming week, move cards from before weeks existed into
    // the first week, and turn cards from before the two columns into unplaced cards.
    @Transactional
    public void ensureDefaultWeek() {
        boolean noUpcomingWeek = plannerWeekRepository
                .findAllByStartDateGreaterThanEqualOrderByStartDateAsc(currentWeekStart()).isEmpty();
        if (noUpcomingWeek) {
            plannerWeekRepository.save(new PlannerWeek(currentWeekStart()));
        }
        PlannerWeek first = plannerWeekRepository.findFirstByOrderByStartDateAsc().orElseThrow();
        for (PlannerEntry card : plannerEntryRepository.findAllByWeekIsNull()) {
            card.setWeek(first);
        }
        // Before the two columns, one entry meant one whole recipe on a day. Make it unplaced, plus
        // one more card per extra serving, so the week keeps the same amount of food.
        for (PlannerEntry old : plannerEntryRepository.findAllByDayOfWeekIsNotNullAndPersonIsNull()) {
            old.setDayOfWeek(null);
            for (int serving = 1; serving < servingsOf(old.getRecipe()); serving++) {
                plannerEntryRepository.save(new PlannerEntry(old.getWeek(), null, null, old.getRecipe()));
            }
        }
    }

    private PlannerWeekDto weekDto(PlannerWeek week) {
        List<PlannerEntry> cards = plannerEntryRepository.findAllByWeekIdOrderByIdAsc(week.getId());
        List<PlannerDayDto> days = Arrays.stream(DayOfWeek.values())
                .map(day -> new PlannerDayDto(day,
                        cardsOf(cards, day, PlannerPerson.ELA),
                        cardsOf(cards, day, PlannerPerson.JOHANN)))
                .toList();
        List<PlannerEntryDto> unplaced = cards.stream()
                .filter(card -> card.getDayOfWeek() == null)
                .map(this::cardOf)
                .toList();
        return new PlannerWeekDto(week.getId(), week.getStartDate(), days, unplaced);
    }

    private List<PlannerEntryDto> cardsOf(List<PlannerEntry> cards, DayOfWeek day, PlannerPerson person) {
        return cards.stream()
                .filter(card -> card.getDayOfWeek() == day && card.getPerson() == person)
                .map(this::cardOf)
                .toList();
    }

    private PlannerEntryDto cardOf(PlannerEntry card) {
        Recipe recipe = card.getRecipe();
        return new PlannerEntryDto(card.getId(), recipe.getId(), recipe.getRecipeName(), recipe.getImageUrl());
    }

    private static boolean isUpcoming(PlannerWeek week) {
        return !week.getStartDate().isBefore(currentWeekStart());
    }
}
