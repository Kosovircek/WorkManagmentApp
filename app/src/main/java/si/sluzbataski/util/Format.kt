package si.sluzbataski.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pomožne funkcije za oblikovanje datumov in trajanj v slovenščini.
 */
object Format {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val sl = Locale("sl", "SI")
    private val dateFmt = DateTimeFormatter.ofPattern("d. M. yyyy", sl)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d. M. yyyy HH:mm", sl)
    private val shortDayFmt = DateTimeFormatter.ofPattern("EEE d. M.", sl)
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(dateFmt)
    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(dateTimeFmt)

    /** Za CSV: "2026-11-10 14:05:00" (Excel to prepozna kot datum). */
    fun iso(ms: Long?): String = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(isoFmt) } ?: ""

    /** Prikaz roka: "danes", "jutri 14:00", "pet 14. 11.", "zamuda 2 dni". */
    fun due(ms: Long, hasTime: Boolean): String {
        val zdt = Instant.ofEpochMilli(ms).atZone(zone)
        val today = LocalDate.now(zone)
        val days = ChronoUnit.DAYS.between(today, zdt.toLocalDate())
        val time = if (hasTime) " " + "%02d:%02d".format(zdt.hour, zdt.minute) else ""
        return when {
            days < 0 -> "zamuda ${-days} ${dni(-days)}"
            days == 0L -> "danes$time"
            days == 1L -> "jutri$time"
            days < 7 -> zdt.format(shortDayFmt) + time
            else -> zdt.format(dateFmt) + time
        }
    }

    /** Ali je rok že mimo (za rdečo oznako). */
    fun isOverdue(ms: Long, hasTime: Boolean): Boolean {
        return if (hasTime) ms < System.currentTimeMillis()
        else Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().isBefore(LocalDate.now(zone))
    }

    /** Starost taska: "danes", "1 dan", "5 dni", "3 tedne". */
    fun age(createdAt: Long): String {
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate(), LocalDate.now(zone))
        return when {
            days <= 0 -> "danes"
            days < 14 -> "$days ${dni(days)}"
            days < 60 -> "${days / 7} ted."
            else -> "${days / 30} mes."
        }
    }

    /** Trajanje v berljivi obliki: "45 min", "3 h 20 min", "2 dni 4 h". */
    fun duration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 60 -> "$min min"
            min < 60 * 24 -> "${min / 60} h ${min % 60} min"
            else -> "${min / (60 * 24)} ${dni(min / (60 * 24))} ${(min / 60) % 24} h"
        }
    }

    /** Slovenska dvojina/množina za "dan". */
    fun dni(n: Long): String = when (n % 100) {
        1L -> "dan"
        2L -> "dneva"
        else -> "dni"
    }
}
