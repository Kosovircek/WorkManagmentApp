package si.sluzbataski.ui.detail

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.ui.components.TaskForm
import si.sluzbataski.ui.components.TaskFormState
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.components.rememberTaskFormState
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.util.Format

/**
 * ZASLON PODROBNOSTI TASKA.
 *
 *  - vsa polja so urejljiva (isti obrazec kot pri dodajanju) – "Shrani spremembe"
 *  - časovni podatki: vnešen, spremenjen, opravljen, trajanje
 *  - gumbi: Opravljeno / Ponovno odpri, Izbriši / Obnovi
 *  - premik na drugo delovno mesto
 *  - zgodovina vseh sprememb (iz tabele task_events)
 */
class TaskDetailActivity : ComponentActivity() {
    private val vm: TaskDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { DetailScreen(vm, onClose = { finish() }) } } }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        fun intent(context: Context, taskId: Long) =
            Intent(context, TaskDetailActivity::class.java).putExtra(EXTRA_TASK_ID, taskId)
    }
}

/** ViewModel: ID taska dobi iz Intenta prek SavedStateHandle. */
class TaskDetailViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)
    val taskId: Long = handle.get<Long>(TaskDetailActivity.EXTRA_TASK_ID) ?: -1L

    val task = g.tasks.observe(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val history = g.tasks.observeHistory(taskId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val clients = g.tasks.observeClients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workplaces = g.workplaces.observeWorkplaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(t: Task, done: () -> Unit) = viewModelScope.launch { g.tasks.update(t); done() }
    fun complete() = viewModelScope.launch { g.tasks.complete(taskId) }
    fun reopen() = viewModelScope.launch { g.tasks.reopen(taskId) }
    fun delete(done: () -> Unit) = viewModelScope.launch { g.tasks.delete(taskId); done() }
    fun restore() = viewModelScope.launch { g.tasks.restore(taskId) }
    fun moveTo(w: Workplace) = viewModelScope.launch {
        task.value?.let { g.tasks.update(it.copy(workplaceId = w.id)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(vm: TaskDetailViewModel, onClose: () -> Unit) {
    val task by vm.task.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val clients by vm.clients.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Task #${vm.taskId}") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } },
            actions = {
                val t = task
                if (t != null && t.deletedAt == null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Izbriši") }
                if (t != null && t.deletedAt != null) IconButton(onClick = { vm.restore() }) { Icon(Icons.Outlined.RestoreFromTrash, "Obnovi") }
            },
        )
    }) { padding ->
        val t = task
        if (t == null) {
            Text("Task ne obstaja.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        // Obrazec se ponovno ustvari, ko se task v bazi spremeni (npr. po shranjevanju)
        key(t.updatedAt) {
            val form = rememberTaskFormState { TaskFormState.from(t) }
            val changed = form.applyTo(t) != t
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                StatusCard(t)

                // Glavni gumb: opravljeno / ponovno odpri
                if (t.deletedAt == null) {
                    if (t.completedAt == null) {
                        Button(onClick = { vm.complete(); onClose() }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF43A047))) {
                            Text("✓ Označi kot opravljeno")
                        }
                    } else {
                        OutlinedButton(onClick = { vm.reopen() }, modifier = Modifier.fillMaxWidth()) { Text("Ponovno odpri") }
                    }
                }

                TaskForm(state = form, clients = clients)

                Button(onClick = { vm.save(form.applyTo(t)) { Toast.makeText(context, "Shranjeno", Toast.LENGTH_SHORT).show() } },
                    enabled = changed && form.isValid, modifier = Modifier.fillMaxWidth()) { Text("Shrani spremembe") }

                if (workplaces.size > 1) {
                    Row { Text("Delovno mesto:", Modifier.padding(top = 12.dp)); Spacer(Modifier.height(1.dp))
                        WorkplaceSwitcher(workplaces.firstOrNull { it.id == t.workplaceId }, workplaces, onSelect = { vm.moveTo(it) }) }
                }

                HorizontalDivider()
                Text("Zgodovina", style = MaterialTheme.typography.titleMedium)
                history.forEach { HistoryRow(it) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Izbrišem task?") },
            text = { Text("Task bo skrit, ostane pa v bazi (zgodovina, statistika, CSV). Obnoviš ga lahko v Arhivu.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onClose) }) { Text("Izbriši") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Prekliči") } },
        )
    }
}

/** Povzetek časovnih podatkov taska. */
@Composable
private fun StatusCard(t: Task) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val status = when {
                t.deletedAt != null -> "Izbrisan ${Format.dateTime(t.deletedAt)}"
                t.completedAt != null -> "Opravljen ${Format.dateTime(t.completedAt)}"
                else -> "Odprt že ${Format.age(t.createdAt)}"
            }
            Text(status, fontWeight = FontWeight.Bold)
            Text("Vnešen: ${Format.dateTime(t.createdAt)}", style = MaterialTheme.typography.bodySmall)
            Text("Zadnja sprememba: ${Format.dateTime(t.updatedAt)}", style = MaterialTheme.typography.bodySmall)
            t.completedAt?.let { Text("Čas do izvedbe: ${Format.duration(it - t.createdAt)}", style = MaterialTheme.typography.bodySmall) }
            if (t.calendarEventId != null) Text("📅 V koledarju", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryRow(e: TaskEvent) {
    Column {
        Text("${Format.dateTime(e.at)} · ${e.type.label}", style = MaterialTheme.typography.bodyMedium)
        e.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
