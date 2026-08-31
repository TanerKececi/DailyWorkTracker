package com.example.dailyworktracker.di

import android.content.Context
import androidx.room.Room
import com.example.dailyworktracker.data.local.AppDatabase
import com.example.dailyworktracker.data.local.dao.HabitCompletionDao
import com.example.dailyworktracker.data.local.dao.HabitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAOs for the whole application. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideHabitDao(database: AppDatabase): HabitDao = database.habitDao()

    @Provides
    fun provideHabitCompletionDao(database: AppDatabase): HabitCompletionDao =
        database.habitCompletionDao()
}
