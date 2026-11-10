package si.sluzbataski.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import si.sluzbataski.data.entity.ShoppingItem

/** Dostop do nakupovalnega seznama. */
@Dao
interface ShoppingDao {
    /** Še nekupljene postavke, urejene po trgovini in času vnosa. */
    @Query("SELECT * FROM shopping_items WHERE workplaceId = :wp AND boughtAt IS NULL AND deletedAt IS NULL ORDER BY store COLLATE NOCASE, createdAt")
    fun observeOpen(wp: Long): Flow<List<ShoppingItem>>

    /** Kupljene postavke (zgodovina), najnovejše najprej. */
    @Query("SELECT * FROM shopping_items WHERE workplaceId = :wp AND boughtAt IS NOT NULL AND deletedAt IS NULL ORDER BY boughtAt DESC LIMIT :limit")
    fun observeBought(wp: Long, limit: Int): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE workplaceId = :wp")
    fun observeAllForWorkplace(wp: Long): Flow<List<ShoppingItem>>

    /** Vse znane trgovine (za predloge). */
    @Query("SELECT DISTINCT store FROM shopping_items WHERE store IS NOT NULL AND store != '' ORDER BY store COLLATE NOCASE")
    fun observeStores(): Flow<List<String>>

    /** Vsa imena izdelkov (za predloge pri vnosu). */
    @Query("SELECT name FROM shopping_items WHERE deletedAt IS NULL GROUP BY name COLLATE NOCASE ORDER BY COUNT(*) DESC LIMIT 300")
    fun observeKnownNames(): Flow<List<String>>

    @Query("SELECT * FROM shopping_items ORDER BY id")
    suspend fun getAll(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun get(id: Long): ShoppingItem?

    @Query("SELECT COUNT(*) FROM shopping_items WHERE workplaceId = :wp AND boughtAt IS NULL AND deletedAt IS NULL")
    suspend fun countOpen(wp: Long): Int

    @Insert suspend fun insert(i: ShoppingItem): Long
    @Update suspend fun update(i: ShoppingItem)
}
