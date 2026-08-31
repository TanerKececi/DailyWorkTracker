package com.example.dailyworktracker.di

import com.example.dailyworktracker.data.repository.HabitRepository
import com.example.dailyworktracker.data.repository.HabitRepositoryImpl
import com.example.dailyworktracker.util.DateProvider
import com.example.dailyworktracker.util.SystemDateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds abstractions to their production implementations, keeping consumers testable. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    @Singleton
    abstract fun bindDateProvider(impl: SystemDateProvider): DateProvider
}
