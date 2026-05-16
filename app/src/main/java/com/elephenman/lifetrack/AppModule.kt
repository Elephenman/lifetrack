package com.elephenman.lifetrack

import android.content.Context
import com.elephenman.lifetrack.data.dao.*
import com.elephenman.lifetrack.data.LifeTrackDatabase
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.util.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LifeTrackDatabase {
        return LifeTrackDatabase.getInstance(context)
    }

    @Provides
    fun provideLocationPointDao(db: LifeTrackDatabase) = db.locationPointDao()

    @Provides
    fun provideStayPointDao(db: LifeTrackDatabase) = db.stayPointDao()

    @Provides
    fun provideTripSegmentDao(db: LifeTrackDatabase) = db.tripSegmentDao()

    @Provides
    fun provideDailySummaryDao(db: LifeTrackDatabase) = db.dailySummaryDao()

    @Provides
    @Singleton
    fun provideLocationRepository(
        locationPointDao: LocationPointDao,
        stayPointDao: StayPointDao,
        tripSegmentDao: TripSegmentDao,
        dailySummaryDao: DailySummaryDao
    ): LocationRepository {
        return LocationRepository(locationPointDao, stayPointDao, tripSegmentDao, dailySummaryDao)
    }

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }
}
