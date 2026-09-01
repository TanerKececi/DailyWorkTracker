package com.example.dailyworktracker.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules an amount habit is recorded by.
 *
 * These run against the fake because it is what every ViewModel test uses; if the fake and the real
 * repository disagree here, every screen test is measuring the wrong thing. The real
 * implementation's SQL is covered separately by the instrumented DAO tests.
 */
class FakeHabitRepositoryAmountTest {
    private val repository = FakeHabitRepository()
    private val date = FakeDateProvider.MONDAY

    @Test
    fun `any amount at all completes the day`() =
        runTest {
            repository.seed(habit(id = 1L))

            repository.setAmount(habitId = 1L, date = date, amount = 1)

            assertTrue(
                "One page is done, the same as twenty: the number is a record, not a target",
                repository.isCompletedOn(habitId = 1L, date = date),
            )
            assertEquals(1, repository.observeCompletions(1L).first()[date])
        }

    @Test
    fun `a later amount replaces the earlier one rather than adding a second day`() =
        runTest {
            repository.seed(habit(id = 1L))

            repository.setAmount(habitId = 1L, date = date, amount = 5)
            repository.setAmount(habitId = 1L, date = date, amount = 12)

            assertEquals(mapOf(date to 12), repository.observeCompletions(1L).first())
        }

    @Test
    fun `zero clears the day`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.setAmount(habitId = 1L, date = date, amount = 12)

            repository.setAmount(habitId = 1L, date = date, amount = 0)

            assertFalse(
                "Clearing the amount clears the day: there is no done-but-zero",
                repository.isCompletedOn(habitId = 1L, date = date),
            )
            assertTrue(repository.observeCompletions(1L).first().isEmpty())
        }

    @Test
    fun `a ticked-off habit records no amount`() =
        runTest {
            repository.seed(habit(id = 1L))

            repository.toggleCompletion(habitId = 1L, date = date)

            assertTrue(repository.isCompletedOn(habitId = 1L, date = date))
            assertEquals(mapOf(date to null), repository.observeCompletions(1L).first())
        }
}
