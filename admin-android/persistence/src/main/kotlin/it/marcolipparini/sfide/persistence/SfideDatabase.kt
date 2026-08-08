package it.marcolipparini.sfide.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RoomTemplateEntity::class, MatchResultEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SfideDatabase : RoomDatabase() {

    abstract fun dao(): SfideDao

    companion object {
        @Volatile
        private var instance: SfideDatabase? = null

        fun get(context: Context): SfideDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SfideDatabase::class.java,
                "sfide.db",
            ).build().also { instance = it }
        }
    }
}
