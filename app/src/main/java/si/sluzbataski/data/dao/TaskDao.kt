package si.sluzbataski.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import si.sluzbataski.data.entity.Task

/**
 * Dostop do tabele taskov.
 *
 * Sortiranje odprtih taskov se izvaja v Kotlinu ([si.sluzbataski.data.repo.TaskSorting]),
 * da je pravilo na enem mestu in enako v aplikaciji in widgetu.
 */
@Dao
interface TaskDao {
    /** Odprti taski delovnega mesta (neopravljeni, neizbrisani). */
    @Query("SELECT * FROM tasks WHERE workplaceId = :wp AND completedAt IS NULL AND deletedAt IS NULL")
    fun observeOpen(wp: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE workplaceId = :wp AND completedAt IS NULL AND deletedAt IS NULL")
    suspend fun getOpen(wp: Long): List<Task>

    /** Opravljeni taski (arhiv), najnovejši najprej. */
    @Query("SELECT * FROM tasks WHERE workplaceId = :wp AND completedAt IS NOT NULL AND deletedAt IS NULL ORDER BY completedAt DESC")
    fun observeDone(wp: Long): Flow<List<Task>>

    /** Izbrisani taski (koš), najnovejši najprej. */
    @Query("SELECT * FROM tasks WHERE workplaceId = :wp AND deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(wp: Long): Flow<List<Task>>

    /** Vsi taski delovnega mesta (za statistiko). */
    @Query("SELECT * FROM tasks WHERE workplaceId = :wp")
    fun observeAllForWorkplace(wp: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun getAll(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    /** Odprti taski z datumom, ki še niso v koledarju (za kasnejšo sinhronizacijo). */
    @Query("SELECT * FROM tasks WHERE urgency = 'DATUM' AND dueAt IS NOT NULL AND calendarEventId IS NULL AND completedAt IS NULL AND deletedAt IS NULL")
    suspend fun getDatedWithoutEvent(): List<Task>

    /** Seznam različnih naročnikov (za predloge pri vnosu). */
    @Query("SELECT DISTINCT client FROM tasks WHERE client IS NOT NULL AND client != '' ORDER BY client COLLATE NOCASE")
    fun observeClients(): Flow<List<String>>

    @Insert suspend fun insert(t: Task): Long
    @Update suspend fun update(t: Task)
}
