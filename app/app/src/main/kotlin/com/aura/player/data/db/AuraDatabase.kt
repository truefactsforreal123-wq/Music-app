package com.aura.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class, PlaylistTrackEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistTrackDao(): PlaylistTrackDao

    companion object {
        @Volatile
        private var instance: AuraDatabase? = null

        fun get(context: Context): AuraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AuraDatabase::class.java, "aura.db")
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { instance = it }
            }
    }
}
