package com.example.dailyworktracker.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a skipped day is recorded by.
 *
 * Like the amount rules, these run against the fake because it is what every ViewModel test uses.
 * A day has three states and only three: done, deliberately skipped, or unresolved. Nothing may
 * ever be two of them at once, which is what most of these tests are here to hold.
 */
class FakeHabitRepositorySkipTest {
    private val repository = FakeHabitRepository()
    private val date = FakeDateProvider.MONDAY

    @Test
    fun `skipping the same day twice clears it`() =
        runTest {
            repository.seed(habit(id = 1L))

            repository.toggleSkip(habitId = 1L, date = date)
            assertEquals(listOf(date), repository.skipsFor(1L))

            repository.toggleSkip(habitId = 1L, date = date)
            assertTrue("A second swipe undoes the first", repository.skipsFor(1L).isEmpty())
        }

    @Test
    fun `skipping a completed day clears the completion`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.toggleCompletion(habitId = 1L, date = date)

            repository.toggleSkip(habitId = 1L, date = date)

            assertFalse(
                "A day cannot be both done and deliberately not done",
                repository.isCompletedOn(habitId = 1L, date = date),
            )
            assertEquals(listOf(date), repository.skipsFor(1L))
        }

    @Test
    fun `completing a skipped day clears the skip`() =
        runTest {
            repository.seed(habit(id = 1L))
            repository.toggleSkip(habitId = 1L, date = date)

            repository.toggleCompletion(habitId = 1L, date = date)

            assertTrue("Doing it settles the day", repository.skipsFor(1L).isEmpty())
            assertTrue(repository.isCompletedOn(habitId = 1L, date = date))
        }

    @Test
    fun `logging an amount on a skipped day clears the skip`() =
        runTest {
            // The amount path is a second way to complete a day, so it needs the same rule or an
            // amount habit could sit there logged and skipped at the same time.
            repository.seed(habit(id = 1L))
            repository.toggleSkip(habitId = 1L, date = date)

            repository.setAmount(habitId = 1L, date = date, amount = 12)

            assertTrue(repository.skipsFor(1L).isEmpty())
            assertEquals(mapOf(date to 12), repository.observeCompletions(1L).first())
        }

    @Test
    fun `clearing an amount does not resurrect a skip`() =
        runTest {
            // Zero clears the day back to unresolved, which is not the same as choosing to skip it.
            repository.seed(habit(id = 1L))
            repository.setAmount(habitId = 1L, date = date, amount = 12)

            repository.setAmount(habitId = 1L, date = date, amount = 0)

            assertTrue(repository.skipsFor(1L).isEmpty())
            assertFalse(repository.isCompletedOn(habitId = 1L, date = date))
        }

    @Test
    fun `skips are reported per habit`() =
        runTest {
            repository.seed(habit(id = 1L), habit(id = 2L))

            repository.toggleSkip(habitId = 1L, date = date)

            assertEquals(setOf(date), repository.observeSkips(1L).first())
            assertTrue(repository.observeSkips(2L).first().isEmpty())
        }

    @Test
    fun `history for every habit arrives keyed by habit`() =
        runTest {
            // The Progress screen judges a month across every habit at once, so it needs one
            // emission covering all of them rather than a flow per habit.
            repository.seed(habit(id = 1L), habit(id = 2L))
            repository.completeOn(1L, date)
            repository.skipOn(2L, date)

            assertEquals(mapOf(1L to setOf(date)), repository.observeAllCompletionDates().first())
            assertEquals(mapOf(2L to setOf(date)), repository.observeAllSkipDates().first())
        }
}
