package com.example.pipecounter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(@PrimaryKey val id: String, val payload: String, val revision: Long, val updatedAt: Long, val deleted: Boolean = false)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY updatedAt DESC")
    fun observe(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getAny(id: String): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: TaskEntity)
    @Query("UPDATE tasks SET payload = :payload, revision = :nextRevision, updatedAt = :time, deleted = :deleted WHERE id = :id AND revision = :expected AND deleted = 0")
    suspend fun update(id: String, payload: String, expected: Long, nextRevision: Long, time: Long, deleted: Boolean): Int
    @Query("SELECT * FROM tasks") suspend fun all(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE deleted = 1") suspend fun tombstones(): List<TaskEntity>
    @Query("DELETE FROM tasks WHERE id = :id AND deleted = 1") suspend fun purge(id: String)
}

@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
abstract class CountDatabase : RoomDatabase() { abstract fun tasks(): TaskDao }
