package com.cansin.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, BeerEvent::class, WaterEvent::class, Note::class, FriendProfile::class, FriendResult::class, GroupSeat::class, CustomQuestion::class],
    version = 8,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8)],
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
    abstract fun groupSeatDao(): GroupSeatDao
    abstract fun customQuestionDao(): CustomQuestionDao
    abstract fun storageDao(): StorageDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cansin.db").build()
    }
}
