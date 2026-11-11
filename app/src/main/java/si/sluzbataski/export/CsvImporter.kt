package si.sluzbataski.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.entity.ShoppingItem
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.TaskEventType
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.photo.PhotoStorage
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Uvoz mape, ki jo je ustvaril [CsvExporter] (npr. za prenos na nov telefon).
 *
 * Uporabnik izbere mapo "SluzbaTaski_<datum>" (z delovna_mesta.csv, taski.csv,
 * zgodovina_taskov.csv, nakupi.csv in podmapo slike/).
 *
 * PRAVILA ZDRUŽEVANJA (uvoz nikoli ne briše obstoječih podatkov):
 *  - delovno mesto z enakim imenom se uporabi obstoječe, sicer se ustvari novo
 *  - task, ki že obstaja (enak čas vnosa + enak opis), se preskoči → ponoven uvoz iste
 *    mape ne podvoji ničesar
 *  - zgodovina se uvozi samo za na novo uvožene taske
 *  - postavka nakupa, ki že obstaja (enak čas vnosa + enako ime), se preskoči
 *  - slike se kopirajo v notranji pomnilnik aplikacije
 *  - ID-ji se dodelijo na novo (stari ID-ji iz CSV služijo le za povezovanje med datotekami)
 *  - ponavljanje in opomniki se prenesejo (alarmi se po uvozu nastavijo znova)
 *  - povezave na koledar se NE prenesejo; odprti taski z datumom se v koledar vpišejo
 *    na novo (klic syncPendingCalendar po uvozu)
 */
class CsvImporter(private val context: Context, private val db: AppDatabase) {

    data class Result(val workplaces: Int, val tasks: Int, val skippedTasks: Int, val events: Int, val shopping: Int, val photos: Int)

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun import(treeUri: Uri): Result = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: error("Mape ni mogoče odpreti")
        val tasksFile = folder.findFile("taski.csv")
            ?: error("V izbrani mapi ni datoteke taski.csv. Izberi mapo SluzbaTaski_… iz izvoza.")

        val wpRows = folder.findFile("delovna_mesta.csv")?.let { read(it) }.orEmpty()
        val taskRows = read(tasksFile)
        val eventRows = folder.findFile("zgodovina_taskov.csv")?.let { read(it) }.orEmpty()
        val shopRows = folder.findFile("nakupi.csv")?.let { read(it) }.orEmpty()
        val photoDir = folder.findFile("slike")

        var newWorkplaces = 0; var newTasks = 0; var skipped = 0; var newEvents = 0; var newShop = 0; var newPhotos = 0

        db.withTransaction {
            // ---------------- delovna mesta: ime -> nov ID
            val wpDao = db.workplaceDao()
            val byName = wpDao.getAll().associateBy { it.name.trim().lowercase() }.toMutableMap()
            suspend fun workplaceId(name: String): Long {
                val key = name.trim().ifBlank { "Služba" }.lowercase()
                byName[key]?.let { return it.id }
                val row = wpRows.firstOrNull { it["ime"].trim().lowercase() == key }
                val w = Workplace(
                    name = name.trim().ifBlank { "Služba" },
                    colorArgb = row?.get("barva")?.let(::parseColor) ?: 0xFF3949AB,
                    createdAt = row?.get("ustvarjeno")?.let(::parseTime) ?: System.currentTimeMillis(),
                    archivedAt = row?.get("arhivirano")?.let(::parseTime),
                )
                val id = wpDao.insert(w)
                byName[key] = w.copy(id = id)
                newWorkplaces++
                return id
            }
            // tudi delovna mesta brez taskov
            wpRows.forEach { workplaceId(it["ime"]) }

            // ---------------- taski
            val taskDao = db.taskDao()
            val existing = taskDao.getAll().map { key(it.createdAt, it.description) }.toHashSet()
            val oldToNew = mutableMapOf<String, Pair<Long, Long>>() // stari id -> (nov id, workplaceId)
            val seriesOld = mutableMapOf<Long, String>()             // nov id -> stari serija_id
            for (r in taskRows) {
                val description = r["opis"]
                val created = parseTime(r["ustvarjen"]) ?: continue
                if (description.isBlank()) continue
                if (!existing.add(key(created, description))) { skipped++; continue }

                val wp = workplaceId(r["delovno_mesto"])
                val urgency = parseUrgency(r["nujnost"])
                val photo = importPhoto(photoDir, r["slika"], r["id"])
                if (photo != null) newPhotos++
                val task = Task(
                    workplaceId = wp,
                    description = description,
                    urgency = urgency,
                    dueAt = if (urgency == Urgency.DATUM) parseTime(r["rok"]) else null,
                    dueHasTime = urgency == Urgency.DATUM && r["rok_z_uro"] == "da",
                    client = r["narocnik"].ifBlank { null },
                    photoPath = photo,
                    createdAt = created,
                    updatedAt = parseTime(r["posodobljen"]) ?: created,
                    completedAt = parseTime(r["opravljen"]),
                    deletedAt = parseTime(r["izbrisan"]),
                    calendarEventId = null,
                    recurrenceType = r["ponavljanje"].ifBlank { null }?.takeIf { urgency == Urgency.DATUM },
                    recurrenceDays = r["ponavljanje_dnevi"].ifBlank { null }?.takeIf { urgency == Urgency.DATUM },
                    reminderMinutes = r["opomnik_min"].toIntOrNull()?.takeIf { urgency == Urgency.DATUM },
                )
                val newId = taskDao.insert(task)
                oldToNew[r["id"]] = newId to wp
                r["serija_id"].takeIf { it.isNotBlank() }?.let { seriesOld[newId] = it }
                newTasks++
            }

            // serije ponovitev: stari serija_id preslikamo v nov ID prvega taska
            for ((newId, oldSeries) in seriesOld) {
                val newSeries = oldToNew[oldSeries]?.first ?: continue
                taskDao.get(newId)?.let { taskDao.update(it.copy(seriesId = newSeries)) }
            }

            // ---------------- zgodovina (samo za na novo uvožene taske)
            val eventDao = db.taskEventDao()
            for (r in eventRows) {
                val (taskId, wp) = oldToNew[r["task_id"]] ?: continue
                eventDao.insert(TaskEvent(
                    taskId = taskId,
                    workplaceId = wp,
                    type = TaskEventType.entries.firstOrNull { it.label == r["dogodek"] } ?: TaskEventType.EDITED,
                    at = parseTime(r["cas"]) ?: continue,
                    detail = r["podrobnosti"].ifBlank { null },
                ))
                newEvents++
            }

            // ---------------- nakupi
            val shopDao = db.shoppingDao()
            val existingShop = shopDao.getAll().map { key(it.createdAt, it.name) }.toHashSet()
            for (r in shopRows) {
                val name = r["izdelek"]
                val created = parseTime(r["dodano"]) ?: continue
                if (name.isBlank() || !existingShop.add(key(created, name))) continue
                shopDao.insert(ShoppingItem(
                    workplaceId = workplaceId(r["delovno_mesto"]),
                    name = name,
                    quantity = r["kolicina"].ifBlank { null },
                    store = r["trgovina"].ifBlank { null },
                    createdAt = created,
                    boughtAt = parseTime(r["kupljeno"]),
                    deletedAt = parseTime(r["izbrisano"]),
                ))
                newShop++
            }
        }
        Result(newWorkplaces, newTasks, skipped, newEvents, newShop, newPhotos)
    }

    // ------------------------------------------------------------ pomožno

    /**
     * Ključ za prepoznavo podvojenih vnosov. Izvoz ima natančnost sekunde, zato
     * čas zaokrožimo na sekunde.
     */
    private fun key(createdAt: Long, text: String) = "${createdAt / 1000}|${text.trim()}"

    /** Kopira sliko iz izvoza v notranji pomnilnik; vrne novo pot ali null. */
    private fun importPhoto(photoDir: DocumentFile?, relative: String, oldId: String): String? {
        if (photoDir == null) return null
        // novejši izvozi: "slike/task_42.jpg"; starejši (brez stolpca) poskusimo po ID-ju
        val name = relative.substringAfterLast('/').ifBlank { "task_$oldId.jpg" }
        val src = photoDir.findFile(name) ?: return null
        return try {
            val target = PhotoStorage.newPhotoFile(context)
            context.contentResolver.openInputStream(src.uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse(s.trim(), isoFmt).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun parseColor(s: String?): Long? =
        s?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() }?.toLongOrNull(16)

    /** CSV vsebuje slovensko ime nujnosti ("Ko bo čas"); dovolimo tudi ime enuma. */
    private fun parseUrgency(s: String): Urgency =
        Urgency.entries.firstOrNull { it.label.equals(s.trim(), true) || it.name.equals(s.trim(), true) } ?: Urgency.NORMALNO

    /** Prebere CSV v seznam vrstic, kjer do vrednosti dostopamo po imenu stolpca. */
    private fun read(file: DocumentFile): List<Row> {
        val text = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return emptyList()
        val records = parseCsv(text.removePrefix("﻿"))
        if (records.isEmpty()) return emptyList()
        val header = records.first().map { it.trim() }
        return records.drop(1).filter { r -> r.any { it.isNotBlank() } }.map { Row(header, it) }
    }

    /** Ena vrstica CSV; neznan ali manjkajoč stolpec vrne prazen niz. */
    class Row(private val header: List<String>, private val values: List<String>) {
        operator fun get(column: String): String {
            val i = header.indexOf(column)
            return if (i in values.indices) values[i] else ""
        }
    }

    companion object {
        /**
         * Razčleni CSV z ločilom ";" (enako kot ga piše CsvExporter): podpira polja
         * v narekovajih, podvojene narekovaje ("") in nove vrstice znotraj polj.
         */
        fun parseCsv(text: String, sep: Char = ';'): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val field = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                    } else field.append(c)
                } else when (c) {
                    '"' -> inQuotes = true
                    sep -> { row.add(field.toString()); field.clear() }
                    '\r' -> {}
                    '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
            return rows
        }
    }
}
