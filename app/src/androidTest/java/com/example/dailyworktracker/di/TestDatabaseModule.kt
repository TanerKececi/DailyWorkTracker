package com.example.dailyworktracker.di

import android.content.Context
import androidx.room.Room
import com.example.dailyworktracker.data.local.AppDatabase
import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import com.example.dailyworktracker.data.local.dao.HabitSkipDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Gives instrumented tests a database of their own.
 *
 * Without this they resolve [DatabaseModule] like the running app does, so every `@HiltAndroidTest`
 * reads and writes the real `daily_work_tracker.db` on the device. That is not a hypothetical: the
 * widget receiver test used to clear that table around each test, and running the suite destroyed
 * whatever habits were on the emulator - including the ones set up by hand to verify a feature.
 *
 * `@TestInstallIn` replaces the production module for every instrumented test at once, so this is
 * the single place the substitution lives and no test has to remember to opt in.
 *
 * The database is in-memory and [Singleton], and Hilt builds a fresh component per test, so each
 * test starts empty and nothing has to be cleaned up afterwards.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Provides
    fun provideHabitDao(database: AppDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: AppDatabase): HabitCompletionDao = database.habitCompletionDao()

    @Provides
    fun provideHabitSkipDao(database: AppDatabase): HabitSkipDao = database.habitSkipDao()
}
