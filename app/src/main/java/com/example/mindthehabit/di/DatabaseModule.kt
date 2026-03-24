package com.example.mindthehabit.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mindthehabit.data.local.AppDatabase
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Migration from version 4 to 5: Add sleep quality and energy metrics
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new columns for comprehensive sleep and energy metrics
            db.execSQL("ALTER TABLE daily_summaries ADD COLUMN sleepQualityScore INTEGER")
            db.execSQL("ALTER TABLE daily_summaries ADD COLUMN samsungSleepScore INTEGER")
            db.execSQL("ALTER TABLE daily_summaries ADD COLUMN energyLevelScore INTEGER")
            db.execSQL("ALTER TABLE daily_summaries ADD COLUMN energyLevel TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "behavior_lens.db"
        )
            .createFromAsset("databases/behavior_lens.db") // Use pre-populated database
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration() // Only as last resort after migrations
            .build()
    }

    @Provides
    fun provideBehaviorDao(database: AppDatabase): BehaviorDao {
        return database.behaviorDao()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
}
