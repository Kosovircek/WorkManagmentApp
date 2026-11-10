package si.sluzbataski.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Izvoz celotne baze v CSV datoteke.
 *
 * Uporabnik izbere mapo (npr. Prenosi ali Google Drive), aplikacija pa v njej ustvari
 * podmapo "SluzbaTaski_LLLL-MM-DD_HHMM" s štirimi datotekami:
 *   - delovna_mesta.csv
 *   - taski.csv           (vsi taski: odprti, opravljeni in izbrisani)
 *   - zgodovina_taskov.csv (vsaka sprememba vsakega taska)
 *   - nakupi.csv          (vse postavke, tudi kupljene in izbrisane)
 *
 * Format: ločilo ";" in UTF-8 z BOM – tako jih Excel v slovenskih nastavitvah odpre
 * pravilno (s šumniki in v ločenih stolpcih).
 */
class CsvExporter(private val context: Context, private val db: AppDatabase) {

    /** Rezultat izvoza za prikaz uporabniku. */
    data class Result(val folderName: String, val rows: Int)

    suspend fun export(treeUri: Uri): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Mape ni mogoče odpreti")
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val folderName = "SluzbaTaski_$stamp"
        val folder = root.createDirectory(folderName) ?: error("Mape ni mogoče ustvariti")

        val workplaces = db.workplaceDao().getAll()
        val wpName = workplaces.associate { it.id to it.name }
        var rows = 0

        // --- delovna mesta
        rows += write(folder, "delovna_mesta.csv",
            listOf("id", "ime", "ustvarjeno", "arhivirano"),
            workplaces.map { listOf(it.id, it.name, Format.iso(it.createdAt), Format.iso(it.archivedAt)) })

        // --- taski
        rows += write(folder, "taski.csv",
            listOf("id", "delovno_mesto", "opis", "nujnost", "rok", "rok_z_uro", "narocnik", "ima_sliko",
                "ustvarjen", "posodobljen", "opravljen", "izbrisan", "status", "trajanje_ur", "koledar_dogodek_id"),
            db.taskDao().getAll().map { t ->
                val status = when {
                    t.deletedAt != null -> "izbrisan"
                    t.completedAt != null -> "opravljen"
                    else -> "odprt"
                }
                val hours = t.completedAt?.let { "%.2f".format(Locale("sl"), (it - t.createdAt) / 3_600_000.0) } ?: ""
                listOf(t.id, wpName[t.workplaceId] ?: t.workplaceId, t.description, t.urgency.label,
                    Format.iso(t.dueAt), if (t.dueHasTime) "da" else "ne", t.client ?: "",
                    if (t.photoPath != null) "da" else "ne",
                    Format.iso(t.createdAt), Format.iso(t.updatedAt), Format.iso(t.completedAt),
                    Format.iso(t.deletedAt), status, hours, t.calendarEventId ?: "")
            })

        // --- zgodovina
        rows += write(folder, "zgodovina_taskov.csv",
            listOf("id", "task_id", "delovno_mesto", "dogodek", "cas", "podrobnosti"),
            db.taskEventDao().getAll().map {
                listOf(it.id, it.taskId, wpName[it.workplaceId] ?: it.workplaceId, it.type.label, Format.iso(it.at), it.detail ?: "")
            })

        // --- nakupi
        rows += write(folder, "nakupi.csv",
            listOf("id", "delovno_mesto", "izdelek", "kolicina", "trgovina", "dodano", "kupljeno", "izbrisano"),
            db.shoppingDao().getAll().map {
                listOf(it.id, wpName[it.workplaceId] ?: it.workplaceId, it.name, it.quantity ?: "", it.store ?: "",
                    Format.iso(it.createdAt), Format.iso(it.boughtAt), Format.iso(it.deletedAt))
            })

        Result(folderName, rows)
    }

    /** Zapiše eno CSV datoteko; vrne število podatkovnih vrstic. */
    private fun write(folder: DocumentFile, name: String, header: List<String>, data: List<List<Any>>): Int {
        val file = folder.createFile("text/csv", name) ?: error("Datoteke $name ni mogoče ustvariti")
        context.contentResolver.openOutputStream(file.uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
            w.write("﻿") // BOM, da Excel prepozna UTF-8
            w.write(header.joinToString(";") { escape(it) })
            w.write("\r\n")
            for (row in data) {
                w.write(row.joinToString(";") { escape(it.toString()) })
                w.write("\r\n")
            }
        } ?: error("Zapis v $name ni uspel")
        return data.size
    }

    /** CSV pravilo: polje z ločilom, narekovajem ali novo vrstico damo v narekovaje. */
    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
