package id.xydesk.remote.sessions

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = false)
abstract class XyDeskDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val NAME = "xydesk.db"

        @Volatile
        private var instance: XyDeskDatabase? = null

        fun get(context: Context): XyDeskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    XyDeskDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
