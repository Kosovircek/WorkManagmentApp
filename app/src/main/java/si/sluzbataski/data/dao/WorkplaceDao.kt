package si.sluzbataski.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import si.sluzbataski.data.entity.Workplace

/** Dostop do tabele delovnih mest. */
@Dao
interface WorkplaceDao {
    /** Vsa nearhivirana delovna mesta, urejena po vrstnem redu ustvarjanja. */
    @Query("SELECT * FROM workplaces WHERE archivedAt IS NULL ORDER BY id")
    fun observeActive(): Flow<List<Workplace>>

    @Query("SELECT * FROM workplaces WHERE archivedAt IS NULL ORDER BY id")
    suspend fun getActive(): List<Workplace>

    @Query("SELECT * FROM workplaces ORDER BY id")
    fun observeAll(): Flow<List<Workplace>>

    @Query("SELECT * FROM workplaces ORDER BY id")
    suspend fun getAll(): List<Workplace>

    @Query("SELECT * FROM workplaces WHERE id = :id")
    suspend fun get(id: Long): Workplace?

    @Insert suspend fun insert(w: Workplace): Long
    @Update suspend fun update(w: Workplace)
}
