package si.sluzbataski.data.repo

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import si.sluzbataski.calendar.CalendarSync
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.TaskEventType
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.photo.PhotoStorage
import si.sluzbataski.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import si.sluzbataski.util.Format
import si.sluzbataski.widget.WidgetUpdater

/**
 * Vsa poslovna logika taskov.
 *
 * Vsaka sprememba gre skozi to razred, ki poskrbi za tri stvari hkrati:
 *  1. zapis v bazo (tabela tasks),
 *  2. zapis v zgodovino (tabela task_events),
 *  3. stranski učinki: koledar, opomnik (alarm), osvežitev widgetov in – pri
 *     ponavljajočih taskih – ustvarjanje naslednje ponovitve.
 *
 * Tako je nemogoče, da bi se task spremenil "mimo" zgodovine ali widgeta.
 */
class TaskRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val calendar: CalendarSync,
) {
    private val dao = db.taskDao()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val events = db.taskEventDao()

    // ---------------------------------------------------------------- branje

    /** Odprti taski delovnega mesta, že razvrščeni po pravilu iz [TaskSorting]. */
    fun observeOpenSorted(workplaceId: Long): Flow<List<Task>> =
        dao.observeOpen(workplaceId).map(TaskSorting::sort)

    suspend fun getOpenSorted(workplaceId: Long): List<Task> = TaskSorting.sort(dao.getOpen(workplaceId))

    fun observeDone(workplaceId: Long) = dao.observeDone(workplaceId)
    fun observeDeleted(workplaceId: Long) = dao.observeDeleted(workplaceId)
    fun observeAll(workplaceId: Long) = dao.observeAllForWorkplace(workplaceId)
    fun observe(id: Long) = dao.observe(id)
    fun observeHistory(taskId: Long) = events.observeForTask(taskId)
    fun observeClients() = dao.observeClients()
    suspend fun get(id: Long) = dao.get(id)

    // ---------------------------------------------------------------- pisanje

    /** Doda nov task in ga (če ima datum) vpiše v koledar. Vrne ID novega taska. */
    suspend fun add(
        workplaceId: Long,
        description: String,
        urgency: Urgency,
        dueAt: Long?,
        dueHasTime: Boolean,
        client: String?,
        photoPath: String?,
        recurrenceType: String? = null,
        recurrenceDays: String? = null,
        reminderMinutes: Int? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val dated = urgency == Urgency.DATUM
        val recurring = dated && recurrenceType != null && Recurrence.parse(recurrenceDays).isNotEmpty()
        // Ponavljajoč task brez izbranega datuma: prva ponovitev je prvi ustrezen dan od danes naprej.
        val firstDue = if (dated && dueAt == null && recurring)
            Recurrence.next(recurrenceType, recurrenceDays, LocalDate.now(zone).minusDays(1))
                ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        else dueAt
        val task = Task(
            workplaceId = workplaceId,
            description = description.trim(),
            urgency = urgency,
            dueAt = if (dated) firstDue else null,
            dueHasTime = dated && dueHasTime && firstDue != null,
            client = client?.trim()?.ifBlank { null },
            photoPath = photoPath,
            createdAt = now,
            updatedAt = now,
            recurrenceType = if (recurring) recurrenceType else null,
            recurrenceDays = if (recurring) Recurrence.join(Recurrence.parse(recurrenceDays)) else null,
            reminderMinutes = if (dated) reminderMinutes else null,
        )
        val id = dao.insert(task)
        log(task.copy(id = id), TaskEventType.CREATED, "${urgency.label}${dueText(task)}")
        syncCalendar(task.copy(id = id))
        afterChange()
        return id
    }

    /**
     * Shrani urejen task. Primerja staro in novo verzijo ter v zgodovino zapiše,
     * kaj točno se je spremenilo.
     */
    suspend fun update(edited: Task) {
        val old = dao.get(edited.id) ?: return
        val dated = edited.urgency == Urgency.DATUM
        val recurring = dated && edited.recurrenceType != null && Recurrence.parse(edited.recurrenceDays).isNotEmpty()
        val clean = edited.copy(
            description = edited.description.trim(),
            client = edited.client?.trim()?.ifBlank { null },
            dueAt = if (dated) edited.dueAt else null,
            dueHasTime = dated && edited.dueHasTime,
            updatedAt = System.currentTimeMillis(),
            recurrenceType = if (recurring) edited.recurrenceType else null,
            recurrenceDays = if (recurring) Recurrence.join(Recurrence.parse(edited.recurrenceDays)) else null,
            reminderMinutes = if (dated) edited.reminderMinutes else null,
        )
        dao.update(clean)

        // Zgodovina: posamezne spremembe
        if (old.urgency != clean.urgency || old.dueAt != clean.dueAt) {
            log(clean, TaskEventType.URGENCY_CHANGED,
                "${old.urgency.label}${dueText(old)} → ${clean.urgency.label}${dueText(clean)}")
        }
        if (old.photoPath != clean.photoPath) {
            log(clean, TaskEventType.PHOTO_CHANGED, if (clean.photoPath == null) "Slika odstranjena" else "Nova slika")
            // staro sliko pobrišemo samo, če je ne uporablja noben drug task (vsak task ima svojo)
            old.photoPath?.let { PhotoStorage.delete(it) }
        }
        if (old.workplaceId != clean.workplaceId) {
            log(clean, TaskEventType.MOVED_WORKPLACE, null)
        }
        val textChanges = buildList {
            if (old.description != clean.description) add("opis")
            if (old.client != clean.client) add("naročnik: ${old.client ?: "-"} → ${clean.client ?: "-"}")
            if (old.recurrenceType != clean.recurrenceType || old.recurrenceDays != clean.recurrenceDays)
                add("ponavljanje: " + Recurrence.describe(clean.recurrenceType, clean.recurrenceDays).ifEmpty { "brez" })
            if (old.reminderMinutes != clean.reminderMinutes)
                add("opomnik: " + ReminderScheduler.label(clean.reminderMinutes))
        }
        if (textChanges.isNotEmpty()) log(clean, TaskEventType.EDITED, textChanges.joinToString("; "))

        syncCalendar(clean)
        afterChange()
    }

    /** Premakne task za eno stopnjo nujnosti gor (bolj nujno) ali dol. Taskom z datumom ne spreminja. */
    suspend fun shiftUrgency(id: Long, up: Boolean) {
        val t = dao.get(id) ?: return
        val idx = Urgency.LADDER.indexOf(t.urgency)
        if (idx < 0) return
        val newIdx = (if (up) idx + 1 else idx - 1).coerceIn(0, Urgency.LADDER.lastIndex)
        if (newIdx == idx) return
        update(t.copy(urgency = Urgency.LADDER[newIdx]))
    }

    /** Označi task kot opravljen – izgine iz seznama in widgeta, ostane v arhivu. */
    suspend fun complete(id: Long) {
        val t = dao.get(id) ?: return
        if (t.completedAt != null) return
        val done = t.copy(completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        dao.update(done)
        log(done, TaskEventType.COMPLETED, "Trajanje: ${Format.duration(done.completedAt!! - done.createdAt)}")
        syncCalendar(done)
        if (done.isRecurring) createNextOccurrence(done)
        afterChange()
    }

    /**
     * Ustvari naslednjo ponovitev ponavljajočega taska (kliče se, ko je trenutna opravljena).
     * Naslednji datum je prvi ustrezen dan po roku trenutne ponovitve, vendar ne v preteklosti
     * (če zamujaš več ponovitev, se ustvari samo ena – naslednja od danes naprej).
     */
    private suspend fun createNextOccurrence(done: Task) {
        val due = done.dueAt ?: return
        val dueZdt = Instant.ofEpochMilli(due).atZone(zone)
        val yesterday = LocalDate.now(zone).minusDays(1)
        val after = maxOf(dueZdt.toLocalDate(), yesterday)
        val nextDate = Recurrence.next(done.recurrenceType, done.recurrenceDays, after) ?: return
        val now = System.currentTimeMillis()
        val next = done.copy(
            id = 0,
            dueAt = nextDate.atTime(dueZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            deletedAt = null,
            calendarEventId = null,
            photoPath = null, // slika ostane pri prvotnem tasku (vsaka datoteka pripada enemu tasku)
            seriesId = done.seriesId ?: done.id,
        )
        val id = dao.insert(next)
        val created = next.copy(id = id)
        log(done, TaskEventType.NEXT_OCCURRENCE, "Naslednja: ${Format.date(created.dueAt!!)}")
        log(created, TaskEventType.CREATED, "Ponovitev (${Recurrence.describe(next.recurrenceType, next.recurrenceDays)})")
        syncCalendar(created)
    }

    /** Ponovno nastavi alarme za vse odprte taske z opomnikom (po zagonu telefona). */
    suspend fun rescheduleAllReminders() {
        for (t in dao.getWithReminder()) ReminderScheduler.schedule(context, t)
    }

    /** Ponovno odpre opravljen task. */
    suspend fun reopen(id: Long) {
        val t = dao.get(id) ?: return
        val open = t.copy(completedAt = null, updatedAt = System.currentTimeMillis())
        dao.update(open)
        log(open, TaskEventType.REOPENED, null)
        syncCalendar(open)
        afterChange()
    }

    /** Mehko brisanje – task se skrije, a ostane v bazi in v CSV izvozu. */
    suspend fun delete(id: Long) {
        val t = dao.get(id) ?: return
        val del = t.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        dao.update(del)
        log(del, TaskEventType.DELETED, null)
        syncCalendar(del)
        afterChange()
    }

    suspend fun restore(id: Long) {
        val t = dao.get(id) ?: return
        val r = t.copy(deletedAt = null, updatedAt = System.currentTimeMillis())
        dao.update(r)
        log(r, TaskEventType.RESTORED, null)
        syncCalendar(r)
        afterChange()
    }

    /**
     * Vpiše v koledar vse taske z datumom, ki še nimajo dogodka (npr. ker dovoljenje
     * za koledar ob vnosu še ni bilo dano). Kliče se ob zagonu aplikacije.
     */
    suspend fun syncPendingCalendar() {
        for (t in dao.getDatedWithoutEvent()) syncCalendar(t)
    }

    // ---------------------------------------------------------------- pomožno

    /**
     * Stranski učinki spremembe taska: koledar in opomnik.
     * (Ime je ostalo syncCalendar, ker je bil koledar prvi tak učinek.)
     */
    private suspend fun syncCalendar(task: Task) {
        ReminderScheduler.schedule(context, task)
        val newEventId = calendar.sync(task) { dao.getUsedEventIds().toSet() }
        if (newEventId != task.calendarEventId) {
            // shranimo samo ID dogodka (brez spremembe updatedAt)
            dao.get(task.id)?.let { dao.update(it.copy(calendarEventId = newEventId)) }
        }
    }

    private suspend fun log(task: Task, type: TaskEventType, detail: String?) {
        events.insert(TaskEvent(taskId = task.id, workplaceId = task.workplaceId, type = type, detail = detail))
    }

    private fun dueText(t: Task): String =
        if (t.hasDate) " (${Format.due(t.dueAt!!, t.dueHasTime)})" else ""

    private suspend fun afterChange() = WidgetUpdater.updateAll(context)
}
