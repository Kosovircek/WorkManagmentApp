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
}
