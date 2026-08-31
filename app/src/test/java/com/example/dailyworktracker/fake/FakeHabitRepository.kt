package com.example.dailyworktracker.fake

import com.example.dailyworktracker.data.local.entity.Habit
import com.example.dailyworktracker.data.model.HabitWithStatus
import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.util.WeekdaySchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory [HabitRepository] for unit tests.
 *
 * This is a working fake rather than a stub: it applies the same schedule filtering and
 * insert-or-delete toggle semantics as the real implementation, so tests exercise realistic
 * behaviour without Room or Android.
 */
class FakeHabitRepository(
    // Defaults to a Monday, so weekday-sensitive tests start from a known day.
    private var today: LocalDate = LocalDate.of(2026, 8, 31),
) : HabitRepository {
    private val habits = MutableStateFlow<List<Habit>>(emptyList())
    private val completions = MutableStateFlow<Set<Completion>>(emptySet())

    private var nextId = 1L

    /** Records calls so tests can assert a screen delegated rather than reimplemented behaviour. */
    val archivedIds = mutableListOf<Long>()
    val unarchivedIds = mutableListOf<Long>()

    data class Completion(val habitId: Long, val date: LocalDate)

    fun setToday(date: LocalDate) {
        today = date
    }

    /** Seeds habits directly, bypassing [addHabit], so tests can start from a known state. */
    fun seed(vararg seeded: Habit) {
        habits.value = seeded.toList()
        nextId = (seeded.maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun completionsFor(habitId: Long): List<LocalDate> = completions.value.filter { it.habitId == habitId }.map { it.date }

    /** Total stored habits, so tests can prove an edit updated in place rather than duplicating. */
    fun habitCount(): Int = habits.value.size

    override fun observeTodaysHabits(): Flow<List<HabitWithStatus>> =
        combine(habits, completions) { allHabits, allCompletions ->
            allHabits
                .filter { !it.isArchived }
                .filter { WeekdaySchedule.isScheduledOn(it.scheduleDaysBitmask, today.dayOfWeek) }
                .map { habit ->
                    HabitWithStatus(
                        habit = habit,
                        isCompleted = Completion(habit.id, today) in allCompletions,
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

    override suspend fun toggleCompletionToday(habitId: Long) = toggleCompletion(habitId, today)
}
