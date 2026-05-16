package com.elephenman.lifetrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.elephenman.lifetrack.data.dao.*
import com.elephenman.lifetrack.data.entity.*

@Database(
    entities = [
        LocationPoint::class,
        StayPoint::class,
        TripSegment::class,
        DailySummary::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LifeTrackDatabase : RoomDatabase() {
    abstract fun locationPointDao(): LocationPointDao
    abstract fun stayPointDao(): StayPointDao
    abstract fun tripSegmentDao(): TripSegmentDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: LifeTrackDatabase? = null

        fun getInstance(context: Context): LifeTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LifeTrackDatabase::class.java,
                    "lifetrack_database"
                )
                    .setJournalMode(JournalMode.TRUNCATE) // 更安全，断电不丢数据
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
