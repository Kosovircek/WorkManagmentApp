package si.sluzbataski.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Preverja izračun naslednje ponovitve. */
class RecurrenceTest {
    @Test
    fun weeklySeveralDays() {
        // 2026-11-11 je sreda; ponavljanje pon (1) in pet (5)
        assertEquals(LocalDate.of(2026, 11, 13), Recurrence.next(Recurrence.WEEKLY, "1,5", LocalDate.of(2026, 11, 11)))
        assertEquals(LocalDate.of(2026, 11, 16), Recurrence.next(Recurrence.WEEKLY, "1,5", LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun monthlyClampsToLastDay() {
        // 31. v mesecu: v novembru (30 dni) je to 30. november
        assertEquals(LocalDate.of(2026, 11, 30), Recurrence.next(Recurrence.MONTHLY, "31", LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 12, 15), Recurrence.next(Recurrence.MONTHLY, "1,15", LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun yearlySeveralDates() {
        assertEquals(LocalDate.of(2026, 12, 24), Recurrence.next(Recurrence.YEARLY, "0315,1224", LocalDate.of(2026, 11, 11)))
        assertEquals(LocalDate.of(2027, 3, 15), Recurrence.next(Recurrence.YEARLY, "0315,1224", LocalDate.of(2026, 12, 24)))
        // 29. februar v neprestopnem letu -> 28. februar
        assertEquals(LocalDate.of(2027, 2, 28), Recurrence.next(Recurrence.YEARLY, "0229", LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun emptyRuleHasNoNext() {
        assertNull(Recurrence.next(Recurrence.WEEKLY, "", LocalDate.of(2026, 11, 11)))
        assertNull(Recurrence.next(null, "1", LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun intervalCountsFromDueDate() {
        val due = LocalDate.of(2026, 11, 10)
        // opravljeno pravočasno: naslednji rok je rok + N
        assertEquals(LocalDate.of(2026, 11, 20), Recurrence.nextAfterDone(Recurrence.INTERVAL, "10", due, LocalDate.of(2026, 11, 10)))
        // opravljeno z zamudo (prejšnji rok + 10 je že mimo): ritem ostane, preskoči na prvo ponovitev od danes naprej
        assertEquals(LocalDate.of(2026, 11, 30), Recurrence.nextAfterDone(Recurrence.INTERVAL, "10", due, LocalDate.of(2026, 11, 25)))
        assertEquals(LocalDate.of(2026, 11, 20), Recurrence.nextAfterDone(Recurrence.INTERVAL, "10", due, LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun intervalValidation() {
        assertNull(Recurrence.interval("0"))
        assertNull(Recurrence.interval(""))
        assertEquals(7, Recurrence.interval("7"))
        assertEquals("vsaka 2 dneva", Recurrence.describe(Recurrence.INTERVAL, "2"))
        assertEquals("vsakih 14 dni", Recurrence.describe(Recurrence.INTERVAL, "14"))
    }

    @Test
    fun weeklyAfterDoneSkipsMissed() {
        // rok v pon 2.11., opravljeno v sredo 11.11.; ponavljanje ob ponedeljkih -> 16.11.
        assertEquals(LocalDate.of(2026, 11, 16),
            Recurrence.nextAfterDone(Recurrence.WEEKLY, "1", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun defaultsAreValid() {
        val d = LocalDate.of(2026, 11, 20) // petek
        assertEquals("5", Recurrence.defaultDays(Recurrence.WEEKLY, d))
        assertEquals("1120", Recurrence.defaultDays(Recurrence.YEARLY, d))
        listOf(Recurrence.WEEKLY, Recurrence.MONTHLY, Recurrence.YEARLY, Recurrence.INTERVAL).forEach {
            assert(Recurrence.isValid(it, Recurrence.defaultDays(it, d)))
        }
    }

    @Test
    fun rrules() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO,FR", Recurrence.toRRule(Recurrence.WEEKLY, "1,5"))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=1,-1", Recurrence.toRRule(Recurrence.MONTHLY, "1,31"))
        assertEquals("FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=24", Recurrence.toRRule(Recurrence.YEARLY, "1224"))
        assertNull(Recurrence.toRRule(Recurrence.YEARLY, "0315,1224"))
        assertEquals("FREQ=DAILY;INTERVAL=10", Recurrence.toRRule(Recurrence.INTERVAL, "10"))
        assertNull(Recurrence.toRRule(null, "1"))
    }
}
