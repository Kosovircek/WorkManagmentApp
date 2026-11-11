package si.sluzbataski.data.repo

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pravila ponavljanja taskov.
 *
 * Ponavljajoč task ima vedno datum (nujnost DATUM). Ko ga označiš kot opravljenega,
 * [si.sluzbataski.data.repo.TaskRepository.complete] s pomočjo [next] izračuna datum
 * naslednje ponovitve in ustvari nov task z enakimi podatki.
 *
 * Vrste in zapis izbranih dni (polje Task.recurrenceDays):
 *  - [WEEKLY]  "1,3,5"     ponedeljek, sreda, petek (1 = pon ... 7 = ned)
 *  - [MONTHLY] "1,15,31"   1., 15. in 31. v mesecu (krajši meseci: zadnji dan)
 *  - [YEARLY]  "0315,1224" 15. marec in 24. december (MMdd)
 */
object Recurrence {
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"

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
        val values = parse(days)
        if (values.isEmpty()) return null
        var d = after.plusDays(1)
        repeat(366 * 3) {
            if (matches(type, values, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** Kratek opis za prikaz, npr. "vsak pon, sre" ali "vsak mesec 1., 15.". */
    fun describe(type: String?, days: String?): String {
        val v = parse(days)
        if (type == null || v.isEmpty()) return ""
        return when (type) {
            WEEKLY -> "vsak " + v.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, sl) }
            MONTHLY -> "vsak mesec " + v.joinToString(", ") { "$it." }
            YEARLY -> "vsako leto " + v.joinToString(", ") { "${it % 100}. ${it / 100}." }
            else -> ""
        }
    }
}
