package com.example.dailyworktracker.fake

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.TodayHabit
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.HabitVisibility
import com.example.dailyworktracker.util.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory [HabitRepository] for unit tests.
 *
 * This is a working fake rather than a stub: it reuses [HabitVisibility] and [StreakCalculator] and
 * applies the same insert-or-delete toggle semantics as the real implementation, so tests exercise
 * realistic behaviour without Room or Android.
 *
 * It holds no clock. Like the real repository, every date arrives as a parameter.
 */
class FakeHabitRepository : HabitRepository {
    private val habits = MutableStateFlow<List<Habit>>(emptyList())
    private val completions = MutableStateFlow<Set<Completion>>(emptySet())

    private var nextId = 1L

    /** Records calls so tests can assert a screen delegated rather than reimplemented behaviour. */
    val archivedIds = mutableListOf<Long>()
    val unarchivedIds = mutableListOf<Long>()

    data class Completion(val habitId: Long, val date: LocalDate)

    /** Seeds habits directly, bypassing [addHabit], so tests can start from a known state. */
    fun seed(vararg seeded: Habit) {
        habits.value = seeded.toList()
        nextId = (seeded.maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun completionsFor(habitId: Long): List<LocalDate> = completions.value.filter { it.habitId == habitId }.map { it.date }

    /** Total stored habits, so tests can prove an edit updated in place rather than duplicating. */
    fun habitCount(): Int = habits.value.size

    /** Seeds completion history directly, for tests that need an existing streak. */
    fun completeOn(
        habitId: Long,
        vararg dates: LocalDate,
    ) {
        completions.value += dates.map { Completion(habitId, it) }
    }

    override fun observeHabitsFor(date: LocalDate): Flow<List<TodayHabit>> =
        combine(habits, completions) { allHabits, allCompletions ->
            allHabits
                // Same predicate as the real repository, so the two cannot drift apart.
                .filter { HabitVisibility.isActiveOn(it, date) }
                .map { habit ->
                    TodayHabit(
                        habit = habit,
                        isCompleted = Completion(habit.id, date) in allCompletions,
                        currentStreak =
                            StreakCalculator.currentStreak(
                                completedDates =
                                    allCompletions
                                        .filter { it.habitId == habit.id }
                                        .map { it.date }
                                        .toSet(),
                                scheduleDaysBitmask = habit.scheduleDaysBitmask,
                                asOf = date,
                            ),
                    )
                }
        }

    override fun observeActiveHabitCount(): Flow<Int> = habits.map { list -> list.count { !it.isArchived } }

    override fun observeAllHabits(): Flow<List<Habit>> =
        habits.map { list -> list.sortedWith(compareBy({ it.isArchived }, { it.createdAt })) }

    override fun observeCompletionDates(habitId: Long): Flow<List<LocalDate>> =
        completions.map { all ->
            all.filter { it.habitId == habitId }.map { it.date }.sortedDescending()
        }

    override suspend fun getHabit(habitId: Long): Habit? = habits.value.find { it.id == habitId }

    override fun observeHabit(habitId: Long): Flow<Habit?> = habits.map { list -> list.find { it.id == habitId } }

    override suspend fun addHabit(habit: Habit): Long {
        val id = nextId++
        habits.value += habit.copy(id = id)
        return id
    }

    override suspend fun updateHabit(habit: Habit) {
        habits.value = habits.value.map { if (it.id == habit.id) habit else it }
    }

    override suspend fun archiveHabit(habitId: Long) {
        archivedIds += habitId
        habits.value =
            habits.value.map {
                if (it.id == habitId) it.copy(isArchived = true) else it
            }
    }

    override suspend fun unarchiveHabit(habitId: Long) {
        unarchivedIds += habitId
        habits.value =
            habits.value.map {
                if (it.id == habitId) it.copy(isArchived = false) else it
            }
    }

    override suspend fun toggleCompletion(
        habitId: Long,
        date: LocalDate,
    ) {
        val completion = Completion(habitId, date)
        completions.value =
            if (completion in completions.value) {
                completions.value - completion
            } else {
                completions.value + completion
            }
    }
}
