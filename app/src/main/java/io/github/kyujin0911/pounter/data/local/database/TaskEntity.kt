package io.github.kyujin0911.pounter.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val revision: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
