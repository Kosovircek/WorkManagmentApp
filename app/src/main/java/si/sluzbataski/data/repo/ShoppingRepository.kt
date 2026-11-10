package si.sluzbataski.data.repo

import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.entity.ShoppingItem

/**
 * Logika nakupovalnega seznama. Postavke se nikoli ne brišejo fizično:
 * odkljukane dobijo čas nakupa, izbrisane čas brisanja.
 */
class ShoppingRepository(db: AppDatabase) {
    private val dao = db.shoppingDao()

    fun observeOpen(wp: Long) = dao.observeOpen(wp)
    fun observeBought(wp: Long) = dao.observeBought(wp, 200)
    fun observeAll(wp: Long) = dao.observeAllForWorkplace(wp)
    fun observeStores() = dao.observeStores()
    fun observeKnownNames() = dao.observeKnownNames()

    suspend fun add(wp: Long, name: String, quantity: String?, store: String?) {
        if (name.isBlank()) return
        dao.insert(ShoppingItem(
            workplaceId = wp,
            name = name.trim(),
            quantity = quantity?.trim()?.ifBlank { null },
            store = store?.trim()?.ifBlank { null },
        ))
    }

    /** Odkljuka (kupljeno) ali odkljuka nazaj (ni kupljeno). */
    suspend fun setBought(item: ShoppingItem, bought: Boolean) {
        dao.update(item.copy(boughtAt = if (bought) System.currentTimeMillis() else null))
    }

    suspend fun update(item: ShoppingItem) = dao.update(item)

    suspend fun delete(item: ShoppingItem) = dao.update(item.copy(deletedAt = System.currentTimeMillis()))
}
