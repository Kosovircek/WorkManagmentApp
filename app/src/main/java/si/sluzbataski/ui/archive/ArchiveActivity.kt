package si.sluzbataski.ui.archive

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import si.sluzbataski.AppGraph
import si.sluzbataski.ui.components.TaskCard
import si.sluzbataski.ui.detail.TaskDetailActivity
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.util.Format

/**
 * ARHIV – opravljeni in izbrisani taski aktivnega delovnega mesta.
 * Nič se ne izgubi: od tu lahko vsak task odpreš, ponovno odpreš ali obnoviš.
 */
class ArchiveActivity : ComponentActivity() {
    private val vm: ArchiveViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { ArchiveScreen(vm) { finish() } } } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)
    private val active = g.workplaces.observeActive().filterNotNull()
    val done = active.flatMapLatest { g.tasks.observeDone(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deleted = active.flatMapLatest { g.tasks.observeDeleted(it.id) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(vm: ArchiveViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val done by vm.done.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Arhiv") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Opravljeni (${done.size})") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Izbrisani (${deleted.size})") })
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Išči po opisu ali naročniku") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
            val list = (if (tab == 0) done else deleted).filter {
                query.isBlank() || it.description.contains(query, true) || (it.client?.contains(query, true) == true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { t ->
                    val info = if (tab == 0 && t.completedAt != null)
                        "opravljen ${Format.date(t.completedAt)} (${Format.duration(t.completedAt - t.createdAt)})"
                    else t.deletedAt?.let { "izbrisan ${Format.date(it)}" }
                    TaskCard(t, onClick = { context.startActivity(TaskDetailActivity.intent(context, t.id)) }, trailingInfo = info)
                }
            }
        }
    }
}
