package si.sluzbataski.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.prefs.SettingsStore
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Sinhronizacija taskov z datumom v Google koledar.
 *
 * KAKO DELUJE
 * Android ima vgrajen "Calendar Provider" (CalendarContract), ki vsebuje vse koledarje
 * Google računov, prijavljenih na telefonu. Ko vanj vpišemo dogodek v koledar Google
 * računa, ga Android sam sinhronizira v Google Koledar (splet, drugi telefoni ...).
 * Zato aplikacija ne potrebuje Google prijave ali API ključa – le dovoljenje za koledar.
 *
 * Ciljni koledar je:
 *  1. ročno izbran v Nastavitvah ([SettingsStore.calendarId]), sicer
 *  2. koledar z imenom [CALENDAR_NAME] ("SluzbaTaski"), ki ga uporabnik enkrat ustvari
 *     na calendar.google.com (Android aplikacija ne more ustvariti koledarja v Google računu).
 *
 * PRAVILA
 *  - task z nujnostjo DATUM  -> dogodek obstaja (ustvari/posodobi)
 *  - task opravljen          -> dogodek ostane, naslov dobi predpono "✔ "
 *  - task izbrisan ali ni več DATUM -> dogodek se izbriše
 */
class CalendarSync(private val context: Context, private val settings: SettingsStore) {

    /** Opis koledarja za izbiro v nastavitvah. */
    data class CalendarInfo(val id: Long, val name: String, val account: String)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Vsi koledarji, v katere lahko pišemo. */
    suspend fun listWritableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val result = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val sel = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, sel, null, null)?.use { c ->
            while (c.moveToNext()) {
                result += CalendarInfo(c.getLong(0), c.getString(1) ?: "?", c.getString(2) ?: "")
            }
        }
        result
    }

    /** Poišče ciljni koledar (ročno izbran ali "SluzbaTaski"). Null = ni najden. */
    suspend fun resolveCalendar(): CalendarInfo? {
        val all = listWritableCalendars()
        val chosen = settings.calendarId
        if (chosen > 0) all.firstOrNull { it.id == chosen }?.let { return it }
        return all.firstOrNull { it.name.equals(CALENDAR_NAME, ignoreCase = true) }
    }

    /**
     * Uskladi koledarski dogodek s stanjem taska.
     * @return nov ID dogodka (ali null, če dogodka ni), ki ga je treba shraniti v task.
     */
    suspend fun sync(task: Task): Long? = withContext(Dispatchers.IO) {
        if (!settings.calendarEnabled || !hasPermission()) return@withContext task.calendarEventId
        try {
            val cr = context.contentResolver
            val shouldExist = task.hasDate && task.deletedAt == null

            if (!shouldExist) {
                task.calendarEventId?.let { id ->
                    cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
                }
                return@withContext null
            }

            val cal = resolveCalendar() ?: return@withContext task.calendarEventId
            val values = buildValues(task, cal.id)

            // Poskusi posodobiti obstoječ dogodek; če ga je uporabnik v koledarju izbrisal, ustvari novega.
            task.calendarEventId?.let { id ->
                val rows = cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null)
                if (rows > 0) return@withContext id
            }
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) }
        } catch (e: Exception) {
            // Koledar ni kritičen – napaka ne sme preprečiti shranjevanja taska.
            Log.w(TAG, "Sinhronizacija koledarja ni uspela", e)
            task.calendarEventId
        }
    }

    /** Sestavi vrednosti dogodka. Celodnevni dogodki morajo biti v UTC polnoči. */
    private fun buildValues(task: Task, calendarId: Long): ContentValues {
        val due = task.dueAt!!
        val title = (if (task.completedAt != null) "✔ " else "") + task.description.lineSequence().first().take(120)
        val desc = buildString {
            append(task.description)
            task.client?.let { append("\n\nNaročnik: ").append(it) }
            append("\n\n(SluzbaTaski #").append(task.id).append(")")
        }
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, desc)
            if (task.dueHasTime) {
                put(CalendarContract.Events.DTSTART, due)
                put(CalendarContract.Events.DTEND, due + 60 * 60 * 1000L) // privzeto 1 ura
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, 0)
            } else {
                val localDate = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
                val startUtc = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.DTEND, startUtc + 24 * 60 * 60 * 1000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.ALL_DAY, 1)
            }
        }
    }

    companion object {
        const val CALENDAR_NAME = "SluzbaTaski"
        private const val TAG = "CalendarSync"
    }
}
