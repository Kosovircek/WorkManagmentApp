package si.sluzbataski.data.repo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pravila ponavljanja taskov.
 *
 * Ponavljajoč task ima vedno datum (nujnost DATUM) in je EN task, ki se ponavlja v nedogled.
 * Ko ga označiš kot opravljenega, [si.sluzbataski.data.repo.TaskRepository.complete] s pomočjo
 * [nextAfterDone] premakne njegov rok na naslednjo ponovitev (opravljena ponovitev gre v arhiv).
 * V Google Koledarju je en ponavljajoč dogodek ([toRRule]).
 *
 * Vrste in zapis izbranih dni (polje Task.recurrenceDays):
 *  - [WEEKLY]  "1,3,5"     ponedeljek, sreda, petek (1 = pon ... 7 = ned)
 *  - [MONTHLY] "1,15,31"   1., 15. in 31. v mesecu (krajši meseci: zadnji dan)
 *  - [YEARLY]  "0315,1224" 15. marec in 24. december (MMdd)
 *  - [INTERVAL] "10"       vsakih 10 dni od roka prejšnje ponovitve (po meri)
 */
object Recurrence {
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
    /** Po meri: vsakih N dni (N je edina vrednost v recurrenceDays). */
    const val INTERVAL = "INTERVAL"
    /** Največji dovoljen razmik pri [INTERVAL] (10 let). */
    const val MAX_INTERVAL = 3650

    /** Razmik v dneh pri [INTERVAL] ali null, če ni veljaven. */
    fun interval(days: String?): Int? = parse(days).firstOrNull()?.takeIf { it in 1..MAX_INTERVAL }

    /** Ali pravilo ima vse potrebne podatke (vsaj en dan oz. veljaven razmik). */
    fun isValid(type: String?, days: String?): Boolean = when (type) {
        null -> false
        INTERVAL -> interval(days) != null
        else -> parse(days).isNotEmpty()
    }

    /**
     * Privzeta izbira ob preklopu vrste, da je pravilo takoj veljavno:
     * dan v tednu / dan v mesecu / datum izhodiščnega dne, pri INTERVAL pa 2 dni.
     */
    fun defaultDays(type: String, base: LocalDate): String = when (type) {
        WEEKLY -> base.dayOfWeek.value.toString()
        MONTHLY -> base.dayOfMonth.toString()
        YEARLY -> (base.monthValue * 100 + base.dayOfMonth).toString()
        INTERVAL -> "2"
        else -> ""
    }

    /** Zapis dni za shranjevanje (urejen, brez podvojenih; pri INTERVAL samo razmik). */
    fun normalize(type: String?, days: String?): String? = when {
        !isValid(type, days) -> null
        type == INTERVAL -> interval(days).toString()
        else -> join(parse(days))
    }

    /**
     * Datum naslednje ponovitve po opravljeni, ki je imela rok [due]; nikoli pred [today].
     * Pri [INTERVAL] se šteje od roka (ne od dneva, ko si task opravil), da se ritem ne zamika;
     * če si zamudil več ponovitev, se preskoči na prvo od danes naprej.
     */
    fun nextAfterDone(type: String?, days: String?, due: LocalDate, today: LocalDate): LocalDate? {
        if (type == INTERVAL) {
            val n = interval(days) ?: return null
            var d = due.plusDays(n.toLong())
            if (d.isBefore(today)) {
                val missed = java.time.temporal.ChronoUnit.DAYS.between(d, today)
                d = d.plusDays(((missed + n - 1) / n) * n)
            }
            return d
        }
        return next(type, days, maxOf(due, today.minusDays(1)))
    }

    private val sl = Locale("sl", "SI")

    fun parse(days: String?): List<Int> =
        days.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()

    fun join(values: Collection<Int>): String = values.distinct().sorted().joinToString(",")

    /** Ali datum ustreza pravilu. */
    fun matches(type: String, values: List<Int>, d: LocalDate): Boolean = when (type) {
        WEEKLY -> d.dayOfWeek.value in values
        MONTHLY -> d.dayOfMonth in values ||
            // npr. "31." v mesecu s 30 dnevi -> zadnji dan v mesecu
            (d.dayOfMonth == d.lengthOfMonth() && values.any { it > d.lengthOfMonth() })
        YEARLY -> (d.monthValue * 100 + d.dayOfMonth) in values ||
            // 29. februar v neprestopnem letu -> 28. februar
            (d.monthValue == 2 && d.dayOfMonth == 28 && !d.isLeapYear && 229 in values)
        else -> false
    }

    /**
     * Prvi datum, STROGO PO [after], ki ustreza pravilu (išče največ 3 leta naprej).
     * Vrne null, če pravilo nima izbranih dni.
     */
    fun next(type: String?, days: String?, after: LocalDate): LocalDate? {
        if (type == null) return null
        if (type == INTERVAL) return interval(days)?.let { after.plusDays(it.toLong()) }
        val values = parse(days)
        if (values.isEmpty()) return null
        var d = after.plusDays(1)
        repeat(366 * 3) {
            if (matches(type, values, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /**
     * Pravilo ponavljanja v obliki RRULE (RFC 5545) za Google Koledar, da koledar pokaže
     * vse prihodnje ponovitve. Null, kadar pravila ni mogoče zapisati z enim RRULE
     * (letno na več različnih datumov) – takrat koledar pokaže samo naslednjo ponovitev.
     */
    fun toRRule(type: String?, days: String?): String? {
        if (!isValid(type, days)) return null
        val v = parse(days)
        return when (type) {
            WEEKLY -> "FREQ=WEEKLY;BYDAY=" + v.joinToString(",") { listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[it - 1] }
            // 31. v mesecu pomeni "zadnji dan", kar je v RRULE -1
            MONTHLY -> "FREQ=MONTHLY;BYMONTHDAY=" + v.map { if (it >= 31) -1 else it }.distinct().joinToString(",")
            YEARLY -> v.singleOrNull()?.let { "FREQ=YEARLY;BYMONTH=${it / 100};BYMONTHDAY=${it % 100}" }
            INTERVAL -> "FREQ=DAILY;INTERVAL=${interval(days)}"
            else -> null
        }
    }

    /** Kratek opis za prikaz, npr. "vsak pon, sre" ali "vsak mesec 1., 15.". */
    fun describe(type: String?, days: String?): String {
        val v = parse(days)
        if (type == null || v.isEmpty()) return ""
        return when (type) {
            WEEKLY -> "vsak " + v.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, sl) }
            MONTHLY -> "vsak mesec " + v.joinToString(", ") { "$it." }
            YEARLY -> "vsako leto " + v.joinToString(", ") { "${it % 100}. ${it / 100}." }
            INTERVAL -> interval(days)?.let { n ->
                when {
                    n == 1 -> "vsak dan"
                    n % 100 == 2 -> "vsaka $n dneva"
                    n % 100 == 3 || n % 100 == 4 -> "vsake $n dni"
                    else -> "vsakih $n dni"
                }
            } ?: ""
            else -> ""
        }
    }
}
