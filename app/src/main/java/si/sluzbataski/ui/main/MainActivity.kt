package si.sluzbataski.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.ui.add.AddTaskActivity
import si.sluzbataski.ui.archive.ArchiveActivity
import si.sluzbataski.ui.components.TaskCard
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.components.open
import si.sluzbataski.ui.detail.TaskDetailActivity
import si.sluzbataski.ui.settings.SettingsActivity
import si.sluzbataski.ui.shopping.ShoppingActivity
import si.sluzbataski.ui.stats.StatsActivity
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.util.Format

/**
 * GLAVNI ZASLON – seznam odprtih taskov aktivnega delovnega mesta.
 *
 *  - naslovna vrstica: preklop delovnega mesta, Nakupi, Statistika, meni (Arhiv, Nastavitve)
 *  - seznam: razdeljen na skupine (Z datumom, Urgentno, Nujno, Normalno, Ko bo čas)
 *  - na kartici: ▲▼ premik nujnosti, ✓ opravljeno (z možnostjo razveljavitve)
 *  - klik na kartico: podrobnosti / urejanje
 *  - gumb "Nov task": zaslon za dodajanje
 */
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { MainScreen(vm) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val active by vm.active.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }

    fun done(t: Task) {
        vm.complete(t.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Opravljeno: ${t.description.take(40)}", "Razveljavi", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undoComplete(t.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    WorkplaceSwitcher(active, workplaces, onSelect = vm::switchTo,
                        onManage = { context.open<SettingsActivity>() })
                },
                actions = {
                    IconButton(onClick = { context.open<ShoppingActivity>() }) { Icon(Icons.Outlined.ShoppingCart, "Nakupi") }
                    IconButton(onClick = { context.open<StatsActivity>() }) { Icon(Icons.Outlined.BarChart, "Statistika") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Meni") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Arhiv (opravljeni)") }, onClick = { menu = false; context.open<ArchiveActivity>() })
                            DropdownMenuItem(text = { Text("Dodaj widget na domači zaslon") }, onClick = { menu = false; context.open<SettingsActivity>() })
                            DropdownMenuItem(text = { Text("Nastavitve in izvoz") }, onClick = { menu = false; context.open<SettingsActivity>() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { context.open<AddTaskActivity>() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nov task") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = tasks
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text("Ni odprtih taskov 🎉\nDodaj novega z gumbom spodaj ali prek widgeta.", textAlign = TextAlign.Center)
            }
            else -> {
                // Razdelitev na skupine po nujnosti (vrstni red že določa TaskSorting)
                val groups = list.groupBy { it.urgency }
                val overdue = list.count { it.hasDate && Format.isOverdue(it.dueAt!!, it.dueHasTime) }
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Text(
                            "${list.size} odprtih" + if (overdue > 0) " · $overdue zamujenih" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    Urgency.entries.sortedBy { it.rank }.forEach { u ->
                        val g = groups[u] ?: return@forEach
                        stickyHeader(key = "h_$u") {
                            Surface(Modifier.fillMaxWidth()) {
                                Text(
                                    (if (u == Urgency.DATUM) "Z datumom" else u.label) + " (${g.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }
                        items(g, key = { it.id }) { t ->
                            TaskCard(
                                task = t,
                                onClick = { context.startActivity(TaskDetailActivity.intent(context, t.id)) },
                                onShift = { up -> vm.shift(t.id, up) },
                                onDone = { done(t) },
                            )
                        }
                    }
                }
            }
        }
    }
}
