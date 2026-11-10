package si.sluzbataski.stats

import si.sluzbataski.data.entity.ShoppingItem
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Urgency
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Obdobje, za katerega se računa statistika. */
enum class StatsPeriod(val label: String, val days: Long?) {
    WEEK("7 dni", 7), MONTH("30 dni", 30), QUARTER("3 mesece", 91), YEAR("Leto", 365), ALL("Vse", null)
}

/** Ena točka v časovni vrsti (stolpec v grafu). */
data class Bucket(val label: String, val received: Int, val completed: Int)

/** Vrednost s pripadajočim imenom (za vodoravne stolpce / kolač). */
data class Slice(val label: String, val value: Double, val colorArgb: Long? = null)

/** Vse izračunane številke za zaslon statistike. */
data class Stats(
    val received: Int,
    val completed: Int,
    val openNow: Int,
    val overdueNow: Int,
    val avgLeadMs: Long?,
    val medianLeadMs: Long?,
    val oldestOpenAgeDays: Long?,
    val series: List<Bucket>,
    val openByUrgency: List<Slice>,
    val avgLeadByUrgencyHours: List<Slice>,
    val topClients: List<Slice>,
    val completedByWeekday: List<Slice>,
    val shoppingBought: Int,
    val shoppingByStore: List<Slice>,
)

/**
 * Izračun statistike iz surovih podatkov. Čisti Kotlin (brez Androida), da je enostavno
 * preverljiv in spremenljiv.
 *
 * Definicije:
 *  - "prejeti"  = taski, USTVARJENI v obdobju (neizbrisani)
 *  - "opravljeni" = taski, OPRAVLJENI v obdobju
 *  - "čas izvedbe" = opravljen − ustvarjen, za taske, opravljene v obdobju
 */
object StatsCalculator {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val sl = Locale("sl", "SI")

    fun compute(allTasks: List<Task>, shopping: List<ShoppingItem>, period: StatsPeriod, now: Long = System.currentTimeMillis()): Stats {
        val tasks = allTasks.filter { it.deletedAt == null }
        val today = LocalDate.now(zone)
        val from: Long = period.days?.let { today.minusDays(it - 1).atStartOfDay(zone).toInstant().toEpochMilli() }
            ?: (tasks.minOfOrNull { it.createdAt } ?: now)

        val received = tasks.filter { it.createdAt >= from }
        val completed = tasks.filter { (it.completedAt ?: -1) >= from }
        val open = tasks.filter { it.completedAt == null }
        val leads = completed.map { it.completedAt!! - it.createdAt }.sorted()

        val overdue = open.count { it.hasDate && isOverdue(it, now) }

        return Stats(
            received = received.size,
            completed = completed.size,
            openNow = open.size,
            overdueNow = overdue,
            avgLeadMs = if (leads.isEmpty()) null else leads.average().toLong(),
            medianLeadMs = leads.getOrNull(leads.size / 2),
            oldestOpenAgeDays = open.minOfOrNull { it.createdAt }?.let { ChronoUnit.DAYS.between(toDate(it), today) },
            series = series(received, completed, from, today),
            openByUrgency = Urgency.entries.map { u -> Slice(u.label, open.count { it.urgency == u }.toDouble(), u.colorArgb) }
                .filter { it.value > 0 },
            avgLeadByUrgencyHours = Urgency.entries.mapNotNull { u ->
                val l = completed.filter { it.urgency == u }.map { it.completedAt!! - it.createdAt }
                if (l.isEmpty()) null else Slice(u.label, l.average() / 3_600_000.0, u.colorArgb)
            },
            topClients = received.groupBy { it.client?.takeIf { c -> c.isNotBlank() } ?: "(brez naročnika)" }
                .map { (k, v) -> Slice(k, v.size.toDouble()) }
                .sortedByDescending { it.value }.take(8),
            completedByWeekday = DayOfWeek.values().map { d ->
                Slice(d.getDisplayName(TextStyle.SHORT, sl), completed.count { toDate(it.completedAt!!).dayOfWeek == d }.toDouble())
            },
            shoppingBought = shopping.count { it.deletedAt == null && (it.boughtAt ?: -1) >= from },
            shoppingByStore = shopping.filter { it.deletedAt == null && (it.boughtAt ?: -1) >= from }
                .groupBy { it.store ?: "(brez trgovine)" }
                .map { (k, v) -> Slice(k, v.size.toDouble()) }
                .sortedByDescending { it.value }.take(8),
        )
    }

    /**
     * Razdeli obdobje na stolpce: po dnevih (≤ 31 dni), po tednih (≤ ~6 mesecev)
     * ali po mesecih (daljše obdobje).
     */
    private fun series(received: List<Task>, completed: List<Task>, from: Long, today: LocalDate): List<Bucket> {
        val start = toDate(from)
        val spanDays = ChronoUnit.DAYS.between(start, today) + 1
        val unit = when {
            spanDays <= 31 -> ChronoUnit.DAYS
            spanDays <= 190 -> ChronoUnit.WEEKS
            else -> ChronoUnit.MONTHS
        }
        fun keyOf(d: LocalDate): LocalDate = when (unit) {
            ChronoUnit.DAYS -> d
            ChronoUnit.WEEKS -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            else -> d.withDayOfMonth(1)
        }
        val keys = generateSequence(keyOf(start)) { it.plus(1, unit) }.takeWhile { !it.isAfter(today) }.toList()
        val rec = received.groupingBy { keyOf(toDate(it.createdAt)) }.eachCount()
        val comp = completed.groupingBy { keyOf(toDate(it.completedAt!!)) }.eachCount()
        return keys.map { k ->
            val label = when (unit) {
                ChronoUnit.DAYS -> "${k.dayOfMonth}."
                ChronoUnit.WEEKS -> "${k.dayOfMonth}.${k.monthValue}."
                else -> k.month.getDisplayName(TextStyle.SHORT, sl)
            }
            Bucket(label, rec[k] ?: 0, comp[k] ?: 0)
        }
    }

    private fun toDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun isOverdue(t: Task, now: Long): Boolean =
        if (t.dueHasTime) t.dueAt!! < now else toDate(t.dueAt!!).isBefore(LocalDate.now(zone))
}
