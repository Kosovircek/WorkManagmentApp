package si.sluzbataski.ui.components

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.data.repo.Recurrence
import si.sluzbataski.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Stikalo "🔁 Ponavljaj" in podmeni ponavljanja.
 *
 * Podmeni je skrit, dokler stikalo ni vklopljeno (redko uporabljena možnost ne sme
 * zasesti prostora). Vklop samodejno nastavi nujnost na "Datum", ker ima ponavljajoč
 * task vedno rok.
 *
 *  - Tedensko: izbira enega ali več dni v tednu
 *  - Mesečno:  izbira enega ali več dni v mesecu (1–31)
 *  - Letno:    seznam datumov (dan + mesec), dodajaš jih z izbirnikom datuma
 *  - Po meri:  vsakih N dni (šteje se od roka prejšnje ponovitve)
 *
 * Ob izbiri vrste je takoj predizbran dan izbranega datuma (ali današnji), tako da
 * "vsak teden / mesec / leto" deluje z enim klikom; dneve lahko nato še spremeniš.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceEditor(state: TaskFormState, onEnable: () -> Unit) {
    val context = LocalContext.current
    val enabled = state.recurrenceType != null
    // izhodišče za predizbiro: izbrani datum taska ali danes
    fun base(): LocalDate = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        ?: LocalDate.now()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Repeat, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Ponavljajoč task", Modifier.weight(1f))
        Switch(enabled, { on ->
            if (on) {
                state.recurrenceType = Recurrence.WEEKLY
                state.recurrenceDays = Recurrence.defaultDays(Recurrence.WEEKLY, base())
                state.urgency = Urgency.DATUM
                onEnable()
            } else {
                state.recurrenceType = null
                state.recurrenceDays = ""
            }
        })
    }
    if (!enabled) return

    val selected = Recurrence.parse(state.recurrenceDays)
    fun toggle(v: Int) {
        state.recurrenceDays = Recurrence.join(if (v in selected) selected - v else selected + v)
    }

    Card {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Recurrence.WEEKLY to "Tedensko", Recurrence.MONTHLY to "Mesečno",
                    Recurrence.YEARLY to "Letno", Recurrence.INTERVAL to "Po meri").forEach { (t, label) ->
                    FilterChip(state.recurrenceType == t, {
                        if (state.recurrenceType != t) {
                            state.recurrenceType = t
                            state.recurrenceDays = Recurrence.defaultDays(t, base())
                        }
                    }, { Text(label) })
                }
            }
            Box {
                when (state.recurrenceType) {
                    Recurrence.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Po", "To", "Sr", "Če", "Pe", "So", "Ne").forEachIndexed { i, d ->
                            FilterChip(i + 1 in selected, { toggle(i + 1) }, { Text(d) })
                        }
                    }
                    Recurrence.MONTHLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..31).forEach { d -> FilterChip(d in selected, { toggle(d) }, { Text("$d.") }) }
                    }
                    Recurrence.INTERVAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vsakih ")
                        OutlinedTextField(
                            value = state.recurrenceDays,
                            onValueChange = { v -> state.recurrenceDays = v.filter { it.isDigit() }.take(4) },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text(" dni")
                    }
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        selected.forEach { md ->
                            InputChip(true, { toggle(md) }, { Text("${md % 100}. ${md / 100}.") },
                                trailingIcon = { Icon(Icons.Default.Close, "Odstrani", Modifier.size(16.dp)) })
                        }
                        AssistChip(onClick = {
                            val today = LocalDate.now()
                            DatePickerDialog(context, { _, _, m, d -> toggle((m + 1) * 100 + d) },
                                today.year, today.monthValue - 1, today.dayOfMonth).show()
                        }, label = { Text("+ dodaj datum") })
                    }
                }
            }
            val desc = Recurrence.describe(state.recurrenceType, state.recurrenceDays)
            Text(if (desc.isEmpty()) (if (state.recurrenceType == Recurrence.INTERVAL)
                    "Vpiši število dni (1–${Recurrence.MAX_INTERVAL})." else "Izberi vsaj en dan.") else "Ponavlja se $desc. Ko task opraviš, se ustvari naslednji.",
                style = MaterialTheme.typography.bodySmall,
                color = if (desc.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Izbira opomnika s klikom: Brez · Ob roku · 10 min · 30 min · 45 min · 1 h prej.
 * Privzeto je "Brez" – opomnik (budilka) se doda samo, če ga izbereš.
 * Opomnik je mogoč samo pri tasku z uro. Ob prvi izbiri na Android 13+ zaprosi za dovoljenje za obvestila.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderPicker(state: TaskFormState) {
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun choose(minutes: Int?) {
        state.reminderMinutes = minutes
        if (minutes != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Alarm, null); Spacer(Modifier.width(6.dp))
        Text("Opomnik (budilka)", style = MaterialTheme.typography.labelLarge)
    }
    if (!state.dueHasTime) {
        Text("Za opomnik izberi še uro.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(state.reminderMinutes == null, { choose(null) }, { Text("Brez") })
        ReminderScheduler.OPTIONS.forEach { (m, _) ->
            FilterChip(state.reminderMinutes == m, { choose(m) }, { Text(ReminderScheduler.shortLabel(m)) })
        }
    }
}
