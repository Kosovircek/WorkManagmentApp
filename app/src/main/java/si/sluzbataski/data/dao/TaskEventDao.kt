package si.sluzbataski.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import si.sluzbataski.data.entity.TaskEvent

/** Dostop do zgodovine taskov (samo dodajanje in branje). */
@Dao
interface TaskEventDao {
    @Insert suspend fun insert(e: TaskEvent): Long

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY at DESC, id DESC")
    fun observeForTask(taskId: Long): Flow<List<TaskEvent>>

    @Query("SELECT * FROM task_events ORDER BY id")
    suspend fun getAll(): List<TaskEvent>
}
