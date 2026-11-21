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
        val recurring = dated && Recurrence.isValid(recurrenceType, recurrenceDays)
        // Ponavljajoč task brez izbranega datuma: prva ponovitev je prvi ustrezen dan od danes naprej.
        val firstDue = if (dated && dueAt == null && recurring)
            (if (recurrenceType == Recurrence.INTERVAL) LocalDate.now(zone)
            else Recurrence.next(recurrenceType, recurrenceDays, LocalDate.now(zone).minusDays(1)))
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
            recurrenceDays = if (recurring) Recurrence.normalize(recurrenceType, recurrenceDays) else null,
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
        val recurring = dated && Recurrence.isValid(edited.recurrenceType, edited.recurrenceDays)
        val clean = edited.copy(
            description = edited.description.trim(),
            client = edited.client?.trim()?.ifBlank { null },
            dueAt = if (dated) edited.dueAt else null,
            dueHasTime = dated && edited.dueHasTime,
            updatedAt = System.currentTimeMillis(),
            recurrenceType = if (recurring) edited.recurrenceType else null,
            recurrenceDays = if (recurring) Recurrence.normalize(edited.recurrenceType, edited.recurrenceDays) else null,
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

    /**
     * Označi task kot opravljen – izgine iz seznama in widgeta, ostane v arhivu.
     *
     * Ponavljajoč task pa NE izgine: je en sam task, ki se ponavlja v nedogled. Njegov rok se
     * premakne na naslednjo ponovitev (glej [advanceRecurring]), opravljena ponovitev pa se
     * shrani kot ločen zapis v arhivu, da ostane v zgodovini in statistiki.
     */
    suspend fun complete(id: Long) {
        val t = dao.get(id) ?: return
        if (t.completedAt != null) return
        if (t.isRecurring && t.dueAt != null && t.deletedAt == null) {
            advanceRecurring(t)
            afterChange()
            return
        }
        val done = t.copy(completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        dao.update(done)
        log(done, TaskEventType.COMPLETED, "Trajanje: ${Format.duration(done.completedAt!! - done.createdAt)}")
        syncCalendar(done)
        afterChange()
    }

    /**
     * Opravljena ponovitev ponavljajočega taska:
     *  1. v arhiv se zapiše kopija "opravljeno na dan X" (brez ponavljanja, koledarja, opomnika in slike),
     *     povezana z izvirnikom prek seriesId – zato štejejo statistika, arhiv in CSV izvoz;
     *  2. izvirni task ostane odprt, rok se premakne na naslednjo ponovitev (od danes naprej, ura ostane),
     *     koledarski dogodek (ponavljajoč) in budilka se premakneta skupaj z njim.
     */
    private suspend fun advanceRecurring(t: Task) {
        val due = t.dueAt ?: return
        val dueZdt = Instant.ofEpochMilli(due).atZone(zone)
        val nextDate = Recurrence.nextAfterDone(t.recurrenceType, t.recurrenceDays,
            dueZdt.toLocalDate(), LocalDate.now(zone)) ?: return
        val now = System.currentTimeMillis()

        // 1. zapis opravljene ponovitve (za arhiv in statistiko)
        val record = t.copy(
            id = 0,
            createdAt = minOf(now, dueZdt.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()),
            updatedAt = now,
            completedAt = now,
            calendarEventId = null,
            photoPath = null, // slika pripada ponavljajočemu tasku
            recurrenceType = null,
            recurrenceDays = null,
            reminderMinutes = null,
            seriesId = t.id,
        )
        val recordId = dao.insert(record)
        log(record.copy(id = recordId), TaskEventType.COMPLETED, "Ponovitev ${Format.date(due)}")

        // 2. isti task dobi nov rok
        val next = t.copy(
            dueAt = nextDate.atTime(dueZdt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
            updatedAt = now,
        )
        dao.update(next)
        log(next, TaskEventType.NEXT_OCCURRENCE, "Opravljeno ${Format.date(due)}, naslednjič ${Format.date(next.dueAt!!)}")
        syncCalendar(next)
    }

    /**
     * Razveljavi zadnji "Opravljeno" (gumb Razveljavi na seznamu).
     * Pri ponavljajočem tasku vrne rok na prejšnjo ponovitev in pobriše njen zapis v arhivu.
     */
    suspend fun undoComplete(id: Long) {
        val t = dao.get(id) ?: return
        if (t.completedAt != null) { reopen(id); return }
        if (!t.isRecurring) return
        val record = dao.getLastDoneInSeries(id) ?: return
        if (System.currentTimeMillis() - (record.completedAt ?: 0) > 10 * 60_000L) return // samo sveže
        dao.delete(record)
        events.deleteForTask(record.id)
        val back = t.copy(dueAt = record.dueAt, updatedAt = System.currentTimeMillis())
        dao.update(back)
        log(back, TaskEventType.REOPENED, "Ponovitev ${record.dueAt?.let { Format.date(it) } ?: ""} spet odprta")
        syncCalendar(back)
        afterChange()
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
