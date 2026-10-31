package com.cansin.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, BeerEvent::class, WaterEvent::class, Note::class, FriendProfile::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun beerDao(): BeerDao
    abstract fun noteDao(): NoteDao
    abstract fun friendDao(): FriendDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cansin.db").build()
    }
}
