package com.example.dailyworktracker.reminder

import com.example.dailyworktracker.fake.FakeHabitReminderScheduler
import com.example.dailyworktracker.fake.FakeHabitRepository
import com.example.dailyworktracker.fake.habit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class HabitReminderSyncTest {
    private val repository = FakeHabitRepository()
    private val scheduler = FakeHabitReminderScheduler()
    private val sync = HabitReminderSync(repository, scheduler)

    @Test
    fun `re-arms every stored habit`() =
        runTest {
            repository.seed(
                habit(id = 1L, title = "Brush teeth", reminderTime = LocalTime.of(7, 30)),
                habit(id = 2L, title = "Do sport", reminderTime = LocalTime.of(18, 0)),
            )

            sync.resyncAll()

            assertEquals(listOf(1L, 2L), scheduler.scheduled.map { it.id })
        }

    @Test
    fun `hands over archived habits too, so a restore can cancel them`() =
        runTest {
            repository.seed(habit(id = 1L, reminderTime = LocalTime.of(7, 30), isArchived = true))

            sync.resyncAll()

            // The scheduler reads an archived habit as "cancel". Filtering it out here would leave
            // a restored archived habit reminding forever.
            assertEquals(listOf(1L), scheduler.scheduled.map { it.id })
        }

    @Test
    fun `does nothing when there are no habits`() =
        runTest {
            sync.resyncAll()

            assertTrue(scheduler.scheduled.isEmpty())
        }
}
