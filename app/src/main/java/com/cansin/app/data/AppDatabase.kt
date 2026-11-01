package com.cansin.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, BeerEvent::class, WaterEvent::class, Note::class, FriendProfile::class, FriendResult::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun beerDao(): BeerDao
    abstract fun noteDao(): NoteDao
    abstract fun friendDao(): FriendDao
    abstract fun waterDao(): WaterDao
    abstract fun exportDao(): ExportDao
    abstract fun friendResultDao(): FriendResultDao
    abstract fun restoreDao(): RestoreDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cansin.db").build()
    }
}
