package com.fashionstudio.aipro.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fashionstudio.aipro.core.database.dao.*
import com.fashionstudio.aipro.core.database.entity.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ProjectEntity::class,
        ReferenceImageEntity::class,
        ProjectVersionEntity::class,
        BrandProfileEntity::class,
        ApiUsageLogEntity::class,
        BatchJobEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun referenceImageDao(): ReferenceImageDao
    abstract fun projectVersionDao(): ProjectVersionDao
    abstract fun brandProfileDao(): BrandProfileDao
    abstract fun apiUsageDao(): ApiUsageDao
    abstract fun batchJobDao(): BatchJobDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fashion_studio_ai_pro.db"
        ).fallbackToDestructiveMigration(dropAllTables = false).build()
    }

    @Provides fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()
    @Provides fun provideReferenceImageDao(db: AppDatabase): ReferenceImageDao = db.referenceImageDao()
    @Provides fun provideProjectVersionDao(db: AppDatabase): ProjectVersionDao = db.projectVersionDao()
    @Provides fun provideBrandProfileDao(db: AppDatabase): BrandProfileDao = db.brandProfileDao()
    @Provides fun provideApiUsageDao(db: AppDatabase): ApiUsageDao = db.apiUsageDao()
    @Provides fun provideBatchJobDao(db: AppDatabase): BatchJobDao = db.batchJobDao()
}
