package si.sluzbataski.ui.components

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.data.repo.Recurrence
import si.sluzbataski.photo.PhotoStorage
import si.sluzbataski.util.Format
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Stanje obrazca za task (uporablja se pri dodajanju, hitrem vnosu in urejanju).
 * Vrednosti preživijo zasuk zaslona (rememberSaveable).
 */
class TaskFormState(
    description: String = "",
    urgency: Urgency = Urgency.NORMALNO,
    dueAt: Long? = null,
    dueHasTime: Boolean = false,
    client: String = "",
    photoPath: String? = null,
    /** Slika, ki je že shranjena v bazi (te obrazec ne sme sam pobrisati). */
    val originalPhotoPath: String? = null,
    recurrenceType: String? = null,
    recurrenceDays: String = "",
    reminderMinutes: Int? = null,
) {
    var description by mutableStateOf(description)
    var urgency by mutableStateOf(urgency)
    var dueAt by mutableStateOf(dueAt)
    var dueHasTime by mutableStateOf(dueHasTime)
    var client by mutableStateOf(client)
    var photoPath by mutableStateOf(photoPath)
    /** Vrsta ponavljanja (Recurrence.WEEKLY/MONTHLY/YEARLY) ali null. */
    var recurrenceType by mutableStateOf(recurrenceType)
    /** Izbrani dnevi ponavljanja, ločeni z vejico (glej Recurrence). */
    var recurrenceDays by mutableStateOf(recurrenceDays)
    /** Minute pred rokom za opomnik ali null. */
    var reminderMinutes by mutableStateOf(reminderMinutes)

    /** Ali je ponavljanje vklopljeno in ima izbran vsaj en dan. */
    val hasRecurrence: Boolean get() = recurrenceType != null && Recurrence.parse(recurrenceDays).isNotEmpty()

    /**
     * Obrazec je veljaven, ko je opis vpisan in ima "Datum" izbran datum
     * (pri ponavljajočem tasku se prvi datum lahko izračuna sam).
     */
    val isValid: Boolean get() = description.isNotBlank() &&
        (urgency != Urgency.DATUM || dueAt != null || hasRecurrence) &&
        (recurrenceType == null || hasRecurrence)

    /** Zamenja sliko; vmesne (še neshranjene) slike pobriše, da ne ostajajo smeti. */
    fun replacePhoto(newPath: String?) {
        val old = photoPath
        if (old != null && old != originalPhotoPath && old != newPath) PhotoStorage.delete(old)
        photoPath = newPath
    }

    /** Uporabnik je zavrgel obrazec – pobriši neshranjeno sliko. */
    fun discard() { photoPath?.let { if (it != originalPhotoPath) PhotoStorage.delete(it) } }

    /** Prenese vrednosti obrazca v obstoječ task (za urejanje). */
    fun applyTo(t: Task): Task = t.copy(
        description = description, urgency = urgency, dueAt = dueAt, dueHasTime = dueHasTime,
        client = client, photoPath = photoPath,
        recurrenceType = if (hasRecurrence) recurrenceType else null,
        recurrenceDays = if (hasRecurrence) recurrenceDays else null,
        reminderMinutes = reminderMinutes,
    )

    companion object {
        fun from(t: Task) = TaskFormState(t.description, t.urgency, t.dueAt, t.dueHasTime, t.client ?: "", t.photoPath, t.photoPath,
            t.recurrenceType, t.recurrenceDays ?: "", t.reminderMinutes)

        val Saver = listSaver<TaskFormState, Any?>(
            save = { listOf(it.description, it.urgency.name, it.dueAt, it.dueHasTime, it.client, it.photoPath, it.originalPhotoPath,
                it.recurrenceType, it.recurrenceDays, it.reminderMinutes) },
            restore = {
                TaskFormState(it[0] as String, Urgency.fromName(it[1] as String), it[2] as Long?, it[3] as Boolean,
                    it[4] as String, it[5] as String?, it[6] as String?, it[7] as String?, it[8] as String, it[9] as Int?)
            },
        )
    }
}

@Composable
fun rememberTaskFormState(init: () -> TaskFormState): TaskFormState =
    rememberSaveable(saver = TaskFormState.Saver, init = init)

/**
 * Obrazec za vnos/urejanje taska.
 *
 * @param clients        znani naročniki (predlogi pod poljem)
 * @param autoFocus      fokus in tipkovnica na opisu takoj ob odprtju
 * @param startCamera    ob odprtju takoj zaženi kamero (gumb "S sliko" na widgetu)
 *
 * Ko uporabnik izbere "Datum", obrazec po potrebi sam zaprosi za dovoljenje za koledar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskForm(
    state: TaskFormState,
    clients: List<String>,
    autoFocus: Boolean = false,
    startCamera: Boolean = false,
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }

    // ---- dovoljenje za koledar (vprašamo samo, če še ni dano)
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    fun ensureCalendarPermission() {
        if (!AppGraph.get(context).calendar.hasPermission()) calendarPermission.launch(CALENDAR_PERMISSIONS)
    }

    // ---- kamera: v pendingPhoto hranimo pot do datoteke, kamor kamera piše
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPhoto
        pendingPhoto = null
        if (path != null) {
            if (ok && File(path).length() > 0) state.replacePhoto(path) else PhotoStorage.delete(path)
        }
    }
    fun launchCamera() {
        val file = PhotoStorage.newPhotoFile(context)
        pendingPhoto = file.absolutePath
        runCatching { cameraLauncher.launch(PhotoStorage.uriFor(context, file)) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) PhotoStorage.importFromUri(context, uri)?.let { state.replacePhoto(it.absolutePath) }
    }

    // ---- glasovni vnos (Googlov prepoznavalnik govora)
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                state.description = if (state.description.isBlank()) text.replaceFirstChar { it.uppercase() }
                else state.description.trimEnd() + " " + text
            }
        }
    }

    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startCamera && !cameraStarted) { cameraStarted = true; launchCamera() }
        else if (state.urgency == Urgency.DATUM && state.dueAt == null) {
            // widget "Z datumom": takoj ponudi izbiro datuma
            ensureCalendarPermission()
            pickDate(context, state)
        }
        if (autoFocus && !startCamera) runCatching { focus.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---------------- Opis
        OutlinedTextField(
            value = state.description,
            onValueChange = { state.description = it },
            label = { Text("Opis taska") },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            minLines = 2,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = {
                IconButton(onClick = {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sl-SI")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Povej opis taska")
                    runCatching { speechLauncher.launch(i) }
                }) { Icon(Icons.Outlined.Mic, "Narek") }
            },
        )

        // ---------------- Nujnost
        Text("Nujnost", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Urgency.KO_BO_CAS, Urgency.NORMALNO, Urgency.NUJNO, Urgency.URGENTNO, Urgency.DATUM).forEach { u ->
                FilterChip(
                    selected = state.urgency == u,
                    onClick = {
                        state.urgency = u
                        if (u == Urgency.DATUM) {
                            ensureCalendarPermission()
                            if (state.dueAt == null) pickDate(context, state)
                        }
                    },
                    label = { Text(if (u == Urgency.DATUM) "📅 Datum" else u.label) },
                    leadingIcon = { ColorDot(Color(u.colorArgb)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }

        // ---------------- Ponavljanje (podmeni se pokaže šele, ko ga vklopiš)
        RecurrenceEditor(state, onEnable = { ensureCalendarPermission() })

        // ---------------- Datum (samo pri nujnosti "Datum")
        if (state.urgency == Urgency.DATUM) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, state) }) {
                    Icon(Icons.Outlined.CalendarMonth, null); Spacer(Modifier.width(6.dp))
                    Text(state.dueAt?.let { Format.date(it) } ?: if (state.hasRecurrence) "Prvi: samodejno" else "Izberi datum")
                }
                OutlinedButton(onClick = { pickTime(context, state) }, enabled = state.dueAt != null) {
                    Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(6.dp))
                    Text(if (state.dueHasTime && state.dueAt != null) Format.dateTime(state.dueAt!!).substringAfterLast(' ') else "Ura (opc.)")
                }
                if (state.dueHasTime) IconButton(onClick = { state.dueHasTime = false; state.dueAt = state.dueAt?.let(::startOfDay) }) {
                    Icon(Icons.Default.Close, "Brez ure")
                }
            }
            ReminderPicker(state)
            Text("Task bo dodan v koledar SluzbaTaski.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---------------- Naročnik + predlogi
        OutlinedTextField(
            value = state.client,
            onValueChange = { state.client = it },
            label = { Text("Naročnik (opcijsko)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        val suggestions = remember(state.client, clients) {
            val q = state.client.trim()
            clients.filter { q.isEmpty() || (it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true)) }.take(6)
        }
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { c -> SuggestionChip(onClick = { state.client = c }, label = { Text(c) }) }
            }
        }

        // ---------------- Slika
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { launchCamera() }, label = { Text("Kamera") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) })
            AssistChip(onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Galerija") }, leadingIcon = { Icon(Icons.Outlined.Image, null) })
            if (state.photoPath != null) {
                AssistChip(onClick = { state.replacePhoto(null) }, label = { Text("Odstrani") },
                    leadingIcon = { Icon(Icons.Default.Close, null) })
            }
        }
        state.photoPath?.let { PhotoThumb(it) }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- izbira datuma / ure

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun startOfDay(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/** Odpre sistemski izbirnik datuma. Ura, če je bila izbrana, se ohrani. */
private fun pickDate(context: android.content.Context, state: TaskFormState) {
    val current = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val d = current?.toLocalDate() ?: LocalDate.now(zone)
    DatePickerDialog(context, { _, y, m, day ->
        val date = LocalDate.of(y, m + 1, day)
        val time = if (state.dueHasTime && current != null) current.toLocalTime() else LocalTime.MIDNIGHT
        state.dueAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

/** Odpre sistemski izbirnik ure (24-urni format). */
private fun pickTime(context: android.content.Context, state: TaskFormState) {
    val base = state.dueAt ?: return
    val zdt = Instant.ofEpochMilli(base).atZone(zone)
    val initial = if (state.dueHasTime) zdt.toLocalTime() else LocalTime.of(9, 0)
    TimePickerDialog(context, { _, h, min ->
        state.dueAt = zdt.toLocalDate().atTime(h, min).atZone(zone).toInstant().toEpochMilli()
        state.dueHasTime = true
    }, initial.hour, initial.minute, true).show()
}

/** Dovoljenji, potrebni za pisanje v koledar. */
val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
