package si.sluzbataski.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** Preverja razčlenjevanje CSV, kot ga piše CsvExporter (ločilo ";", narekovaji, nove vrstice). */
class CsvParseTest {
    @Test
    fun parsesQuotedFieldsSeparatorsAndNewlines() {
        val csv = "id;opis;narocnik\r\n" +
            "1;\"Popravi \"\"tiskalnik\"\"; nujno\";Janez\r\n" +
            "2;\"Prva vrstica\nDruga vrstica\";\r\n"
        val rows = CsvImporter.parseCsv(csv)
        assertEquals(3, rows.size)
        assertEquals(listOf("id", "opis", "narocnik"), rows[0])
        assertEquals(listOf("1", "Popravi \"tiskalnik\"; nujno", "Janez"), rows[1])
        assertEquals(listOf("2", "Prva vrstica\nDruga vrstica", ""), rows[2])
    }

    @Test
    fun lastLineWithoutNewline() {
        val rows = CsvImporter.parseCsv("a;b\n1;2")
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows)
    }
}
