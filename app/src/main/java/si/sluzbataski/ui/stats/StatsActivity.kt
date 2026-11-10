package si.sluzbataski.ui.stats

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import si.sluzbataski.AppGraph
import si.sluzbataski.stats.Stats
import si.sluzbataski.stats.StatsCalculator
import si.sluzbataski.stats.StatsPeriod
import si.sluzbataski.ui.components.HorizontalBars
import si.sluzbataski.ui.components.ReceivedCompletedChart
import si.sluzbataski.ui.components.StatTile
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.util.Format

/**
 * STATISTIKA aktivnega delovnega mesta za izbrano obdobje:
 *  - ključne številke (prejeti, opravljeni, odprti, zamujeni, povprečni/mediani čas)
 *  - graf prejetih vs. opravljenih po dnevih/tednih/mesecih
 *  - odprti po nujnosti, povprečni čas izvedbe po nujnosti
 *  - najpogostejši naročniki, opravljeni po dnevih v tednu
 *  - nakupi po trgovinah
 */
class StatsActivity : ComponentActivity() {
    private val vm: StatsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { StatsScreen(vm) { finish() } } } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)
    val period = MutableStateFlow(StatsPeriod.MONTH)
    val active = g.workplaces.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val workplaces = g.workplaces.observeWorkplaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Statistika se preračuna ob vsaki spremembi podatkov, obdobja ali delovnega mesta. */
    val stats = active.filterNotNull().flatMapLatest { wp ->
        combine(g.tasks.observeAll(wp.id), g.shopping.observeAll(wp.id), period) { t, s, p -> StatsCalculator.compute(t, s, p) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun switchTo(id: Long) = g.workplaces.switchTo(id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val period by vm.period.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Statistika") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } },
            actions = { WorkplaceSwitcher(active, workplaces, onSelect = { vm.switchTo(it.id) }) },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsPeriod.entries.forEach { p -> FilterChip(p == period, { vm.period.value = p }, { Text(p.label) }) }
            }
            stats?.let { StatsContent(it) } ?: Text("Računam …")
        }
    }
}

@Composable
private fun StatsContent(s: Stats) {
    // ---- ključne številke
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Prejeti", s.received.toString(), Modifier.weight(1f))
        StatTile("Opravljeni", s.completed.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Odprti zdaj", s.openNow.toString(), Modifier.weight(1f),
            hint = s.oldestOpenAgeDays?.let { "najstarejši: $it ${Format.dni(it)}" })
        StatTile("Zamujeni", s.overdueNow.toString(), Modifier.weight(1f), hint = "taski z datumom")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Povprečni čas", s.avgLeadMs?.let { Format.duration(it) } ?: "—", Modifier.weight(1f), hint = "od vnosa do izvedbe")
        StatTile("Mediana", s.medianLeadMs?.let { Format.duration(it) } ?: "—", Modifier.weight(1f), hint = "polovica hitreje")
    }

    Section("Prejeti in opravljeni skozi čas") { ReceivedCompletedChart(s.series, Modifier.fillMaxWidth()) }
    Section("Odprti po nujnosti") { HorizontalBars(s.openByUrgency, { it.toInt().toString() }) }
    Section("Povprečni čas izvedbe po nujnosti") { HorizontalBars(s.avgLeadByUrgencyHours, { formatHours(it) }) }
    Section("Naročniki (prejeti taski)") { HorizontalBars(s.topClients, { it.toInt().toString() }) }
    Section("Opravljeni po dnevih v tednu") { HorizontalBars(s.completedByWeekday, { it.toInt().toString() }) }
    Section("Nakupi po trgovinah (${s.shoppingBought} kupljenih)") { HorizontalBars(s.shoppingByStore, { it.toInt().toString() }) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

private fun formatHours(h: Double): String = when {
    h < 1 -> "${(h * 60).toInt()} min"
    h < 48 -> "%.1f h".format(h)
    else -> "%.1f dni".format(h / 24)
}
