package si.sluzbataski.ui.settings

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.calendar.CalendarSync
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.ui.components.CALENDAR_PERMISSIONS
import si.sluzbataski.ui.components.ColorDot
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.widget.QuickAddWidgetReceiver
import si.sluzbataski.widget.TaskListWidgetReceiver
import si.sluzbataski.widget.WidgetUpdater
import si.sluzbataski.widget.requestPinWidget

/**
 * NASTAVITVE:
 *  1. Delovna mesta – dodaj, preimenuj, barva, arhiviraj/obnovi
 *  2. Widgeti – postavitev na domači zaslon z enim klikom
 *  3. Google koledar – vklop, dovoljenje, izbira koledarja
 *  4. Izvoz baze v CSV
 */
class SettingsActivity : ComponentActivity() {
    private val vm: SettingsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { SettingsScreen(vm) { finish() } } } }
    }
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    val g = AppGraph.get(app)
    val workplaces = g.workplaces.observeAllWorkplaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun add(name: String, color: Long) = viewModelScope.launch {
        val id = g.workplaces.add(name, color)
        g.workplaces.switchTo(id)
        WidgetUpdater.updateAll(getApplication())
    }
    fun save(w: Workplace) = viewModelScope.launch { g.workplaces.update(w); WidgetUpdater.updateAll(getApplication()) }
    fun archive(w: Workplace, onFail: () -> Unit) = viewModelScope.launch {
        if (!g.workplaces.archive(w)) onFail()
        WidgetUpdater.updateAll(getApplication())
    }
    fun unarchive(w: Workplace) = viewModelScope.launch { g.workplaces.unarchive(w); WidgetUpdater.updateAll(getApplication()) }
}

/** Ponujene barve za delovna mesta. */
private val PALETTE = listOf(0xFF3949AB, 0xFF00897B, 0xFFE53935, 0xFFFB8C00, 0xFF8E24AA, 0xFF6D4C41, 0xFF546E7A, 0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Workplace?>(null) }
    var adding by remember { mutableStateOf(false) }

    // ---- izvoz: uporabnik izbere mapo, nato zapišemo CSV datoteke
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                val r = vm.g.exporter.export(uri)
                Toast.makeText(context, "Izvoženo v mapo ${r.folderName} (${r.rows} vrstic, ${r.photos} slik)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Izvoz ni uspel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Nastavitve") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ================= Delovna mesta
            Text("Delovna mesta", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    workplaces.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            ColorDot(Color(w.colorArgb), 14)
                            Spacer(Modifier.width(10.dp))
                            Text(w.name + if (w.archivedAt != null) " (arhivirano)" else "", Modifier.weight(1f))
                            IconButton(onClick = { editing = w }) { Icon(Icons.Outlined.Edit, "Uredi") }
                            if (w.archivedAt == null) IconButton(onClick = {
                                vm.archive(w) { Toast.makeText(context, "Zadnjega delovnega mesta ni mogoče arhivirati", Toast.LENGTH_SHORT).show() }
                            }) { Icon(Icons.Outlined.Archive, "Arhiviraj") }
                            else IconButton(onClick = { vm.unarchive(w) }) { Icon(Icons.Outlined.Unarchive, "Obnovi") }
                        }
                    }
                    TextButton(onClick = { adding = true }) { Text("+ Dodaj delovno mesto") }
                }
            }
            Text("Arhivirano delovno mesto se ne prikazuje pri preklopu, njegovi podatki pa ostanejo shranjeni.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            WidgetSection()

            HorizontalDivider()
            CalendarSection(vm.g.calendar, vm.g)

            HorizontalDivider()
            // ================= Izvoz
            Text("Izvoz podatkov", style = MaterialTheme.typography.titleMedium)
            Text("Izvozi vse (vsa delovna mesta, odprte, opravljene in izbrisane taske, zgodovino sprememb in nakupe) " +
                "v CSV datoteke, ki jih odpreš v Excelu ali Google Sheets. Slike taskov se kopirajo v podmapo slike/.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { exportLauncher.launch(null) }) { Text("Izvozi v CSV …") }
        }
    }

    if (adding) WorkplaceDialog(null, onDismiss = { adding = false }) { name, color -> vm.add(name, color); adding = false }
    editing?.let { w ->
        WorkplaceDialog(w, onDismiss = { editing = null }) { name, color -> vm.save(w.copy(name = name, colorArgb = color)); editing = null }
    }
}

/**
 * Razdelek "Widgeti": gumba, ki widget postavita na domači zaslon (brez iskanja
 * v seznamu pripomočkov, kar je na nekaterih telefonih težko).
 */
@Composable
private fun WidgetSection() {
    val context = LocalContext.current
    fun pin(receiver: Class<*>) {
        if (!requestPinWidget(context, receiver)) {
            Toast.makeText(context, "Zaganjalnik tega ne podpira. Dolgo pritisni na domači zaslon → Pripomočki → Službeni taski.",
                Toast.LENGTH_LONG).show()
        }
    }
    Text("Widgeti na domačem zaslonu", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pin(QuickAddWidgetReceiver::class.java) }) { Text("+ Hitri vnos") }
        OutlinedButton(onClick = { pin(TaskListWidgetReceiver::class.java) }) { Text("+ Seznam taskov") }
    }
    Text("Telefon te bo vprašal za potrditev. Na Xiaomi telefonih po potrebi v nastavitvah aplikacije " +
        "(Dovoljenja → Druga dovoljenja) vklopi \"Bližnjice na začetnem zaslonu\".",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Razdelek za Google koledar. */
@Composable
private fun CalendarSection(calendar: CalendarSync, g: AppGraph) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(g.settings.calendarEnabled) }
    var calendars by remember { mutableStateOf<List<CalendarSync.CalendarInfo>>(emptyList()) }
    var target by remember { mutableStateOf<CalendarSync.CalendarInfo?>(null) }
    var chosenId by remember { mutableLongStateOf(g.settings.calendarId) }
    val hasPermission = remember(refresh) { calendar.hasPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        scope.launch { g.tasks.syncPendingCalendar() }
    }
    LaunchedEffect(refresh, chosenId) {
        calendars = calendar.listWritableCalendars()
        target = calendar.resolveCalendar()
    }

    Text("Google koledar", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Taske z datumom vpisuj v koledar", Modifier.weight(1f))
        Switch(enabled, { enabled = it; g.settings.calendarEnabled = it })
    }
    if (!hasPermission) {
        Text("Aplikacija še nima dovoljenja za koledar.", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { permissionLauncher.launch(CALENDAR_PERMISSIONS) }) { Text("Dovoli dostop do koledarja") }
        return
    }
    val t = target
    if (t != null) {
        Text("✓ Ciljni koledar: ${t.name} (${t.account})", fontWeight = FontWeight.Medium)
    } else {
        Text("Koledarja \"${CalendarSync.CALENDAR_NAME}\" ni najti.", color = MaterialTheme.colorScheme.error)
        Text("Na calendar.google.com ustvari nov koledar z imenom ${CalendarSync.CALENDAR_NAME} (Drugi koledarji → + → Ustvari nov koledar). " +
            "Na telefonu ga v aplikaciji Google Koledar vklopi za sinhronizacijo, nato pritisni Osveži. Lahko pa spodaj izbereš drug koledar.",
            style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { refresh++; scope.launch { g.tasks.syncPendingCalendar() } }) { Text("Osveži") }
    }
    Text("Izbira koledarja", style = MaterialTheme.typography.labelLarge)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenId = -1; g.settings.calendarId = -1 }) {
            RadioButton(chosenId <= 0, { chosenId = -1; g.settings.calendarId = -1 })
            Text("Samodejno (\"${CalendarSync.CALENDAR_NAME}\")")
        }
        calendars.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenId = c.id; g.settings.calendarId = c.id }) {
                RadioButton(chosenId == c.id, { chosenId = c.id; g.settings.calendarId = c.id })
                Column { Text(c.name); Text(c.account, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Dialog za novo / urejanje delovnega mesta: ime in barva. */
@Composable
private fun WorkplaceDialog(w: Workplace?, onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var name by remember { mutableStateOf(w?.name ?: "") }
    var color by remember { mutableLongStateOf(w?.colorArgb ?: PALETTE.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (w == null) "Novo delovno mesto" else "Uredi delovno mesto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Ime") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PALETTE.forEach { c ->
                        Box(Modifier.size(28.dp).background(Color(c), CircleShape)
                            .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = c })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Shrani") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Prekliči") } },
    )
}
