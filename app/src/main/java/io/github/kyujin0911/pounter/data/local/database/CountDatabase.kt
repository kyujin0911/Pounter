package io.github.kyujin0911.pounter.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class CountDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
}
