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
    private val skips = MutableStateFlow<Set<Skip>>(emptySet())

    private var nextId = 1L

    /** Records calls so tests can assert a screen delegated rather than reimplemented behaviour. */
    val archivedIds = mutableListOf<Long>()
    val unarchivedIds = mutableListOf<Long>()

    data class Completion(val habitId: Long, val date: LocalDate, val amount: Int? = null)

    data class Skip(val habitId: Long, val date: LocalDate)

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

    /** Seeds skipped days directly, for tests that need a history with rest days already in it. */
    fun skipOn(
        habitId: Long,
        vararg dates: LocalDate,
    ) {
        skips.value += dates.map { Skip(habitId, it) }
    }

    fun skipsFor(habitId: Long): List<LocalDate> = skips.value.filter { it.habitId == habitId }.map { it.date }

    override fun observeHabitsFor(date: LocalDate): Flow<List<TodayHabit>> =
        combine(habits, completions, skips) { allHabits, allCompletions, allSkips ->
            allHabits
                // Same predicate as the real repository, so the two cannot drift apart.
                .filter { HabitVisibility.isActiveOn(it, date) }
                .map { habit ->
                    val skipped =
                        allSkips.filter { it.habitId == habit.id }.map { it.date }.toSet()
                    TodayHabit(
                        habit = habit,
                        isCompleted = allCompletions.any { it.habitId == habit.id && it.date == date },
                        // Only the day being shown carries its amount, matching the real repository.
                        amount =
                            allCompletions
                                .find { it.habitId == habit.id && it.date == date }
                                ?.amount,
                        currentStreak =
                            StreakCalculator.currentStreak(
                                completedDates =
                                    allCompletions
                                        .filter { it.habitId == habit.id }
                                        .map { it.date }
                                        .toSet(),
                                scheduleDaysBitmask = habit.scheduleDaysBitmask,
                                asOf = date,
                                skippedDates = skipped,
                            ),
                        isSkipped = date in skipped,
                    )
                }
        }

    override fun observeActiveHabitCount(): Flow<Int> = habits.map { list -> list.count { !it.isArchived } }

    override fun observeAllHabits(): Flow<List<Habit>> =
        habits.map { list -> list.sortedWith(compareBy({ it.isArchived }, { it.createdAt })) }

    override fun observeCompletions(habitId: Long): Flow<Map<LocalDate, Int?>> =
        completions.map { all ->
            all.filter { it.habitId == habitId }.associateBy({ it.date }, { it.amount })
        }

    override fun observeSkips(habitId: Long): Flow<Set<LocalDate>> =
        skips.map { all -> all.filter { it.habitId == habitId }.map { it.date }.toSet() }

    override suspend fun getHabit(habitId: Long): Habit? = habits.value.find { it.id == habitId }

    override suspend fun isCompletedOn(
        habitId: Long,
        date: LocalDate,
    ): Boolean = completions.value.any { it.habitId == habitId && it.date == date }

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
        // Matched on habit and day rather than by value: a completion now carries an amount too,
        // so set membership would miss a row that differs only in what was logged.
        val existing = existing(habitId, date)
        completions.value =
            if (existing != null) {
                completions.value - existing
            } else {
                skips.value = skips.value - Skip(habitId, date)
                completions.value + Completion(habitId, date)
            }
    }

    /** Same rule as the real repository: zero clears the day, anything else records it. */
    override suspend fun setAmount(
        habitId: Long,
        date: LocalDate,
        amount: Int,
    ) {
        val existing = existing(habitId, date)
        val withoutDay = existing?.let { completions.value - it } ?: completions.value
        completions.value =
            if (amount <= 0) withoutDay else withoutDay + Completion(habitId, date, amount)
        if (amount > 0) skips.value = skips.value - Skip(habitId, date)
    }

    /** Same mutual exclusion as the real repository: a day is done, skipped, or neither. */
    override suspend fun toggleSkip(
        habitId: Long,
        date: LocalDate,
    ) {
        val skip = Skip(habitId, date)
        if (skip in skips.value) {
            skips.value = skips.value - skip
        } else {
            skips.value = skips.value + skip
            existing(habitId, date)?.let { completions.value = completions.value - it }
        }
    }

    private fun existing(
        habitId: Long,
        date: LocalDate,
    ): Completion? = completions.value.find { it.habitId == habitId && it.date == date }
}
