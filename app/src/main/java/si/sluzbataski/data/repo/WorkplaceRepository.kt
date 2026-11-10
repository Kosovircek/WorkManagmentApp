package si.sluzbataski.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.data.prefs.SettingsStore

/**
 * Logika delovnih mest: seznam, aktivno mesto in preklapljanje.
 *
 * Aktivno delovno mesto je shranjeno v [SettingsStore]. Če shranjeni ID ne obstaja več
 * (ali še ni nastavljen), se kot aktivno vzame prvo delovno mesto.
 */
class WorkplaceRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    private val dao = db.workplaceDao()

    fun observeWorkplaces(): Flow<List<Workplace>> = dao.observeActive()
    fun observeAllWorkplaces(): Flow<List<Workplace>> = dao.observeAll()

    /** Trenutno aktivno delovno mesto (posodobi se ob preklopu ali spremembi imena). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActive(): Flow<Workplace?> =
        combine(dao.observeActive(), settings.observeActiveWorkplaceId()) { list, id ->
            list.firstOrNull { it.id == id } ?: list.firstOrNull()
        }

    /** Enkratno branje aktivnega delovnega mesta (npr. za widget). */
    suspend fun getActive(): Workplace? {
        ensureDefault()
        val list = dao.getActive()
        return list.firstOrNull { it.id == settings.activeWorkplaceId } ?: list.firstOrNull()
    }

    /** Ob prvem zagonu ustvari privzeto delovno mesto "Služba". */
    suspend fun ensureDefault() {
        if (dao.getActive().isEmpty()) {
            val id = dao.insert(Workplace(name = "Služba"))
            settings.activeWorkplaceId = id
        }
    }

    fun switchTo(id: Long) { settings.activeWorkplaceId = id }

    /** Preklopi na naslednje delovno mesto v krogu (uporablja widget). */
    suspend fun cycleNext(): Workplace? {
        val list = dao.getActive()
        if (list.isEmpty()) return null
        val idx = list.indexOfFirst { it.id == settings.activeWorkplaceId }
        val next = list[(idx + 1).mod(list.size)]
        settings.activeWorkplaceId = next.id
        return next
    }

    suspend fun add(name: String, colorArgb: Long): Long =
        dao.insert(Workplace(name = name.trim(), colorArgb = colorArgb))

    suspend fun update(w: Workplace) = dao.update(w)

    /** Arhivira delovno mesto (podatki ostanejo). Zadnjega aktivnega ni mogoče arhivirati. */
    suspend fun archive(w: Workplace): Boolean {
        val active = dao.getActive()
        if (active.size <= 1) return false
        dao.update(w.copy(archivedAt = System.currentTimeMillis()))
        if (settings.activeWorkplaceId == w.id) {
            settings.activeWorkplaceId = active.first { it.id != w.id }.id
        }
        return true
    }

    suspend fun unarchive(w: Workplace) = dao.update(w.copy(archivedAt = null))

    suspend fun get(id: Long): Workplace? = dao.get(id)
}
