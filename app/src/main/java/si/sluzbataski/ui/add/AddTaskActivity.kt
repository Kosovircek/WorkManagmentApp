package si.sluzbataski.ui.add

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.sluzbataski.ui.components.TaskForm
import si.sluzbataski.ui.components.TaskFormState
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.components.rememberTaskFormState
import si.sluzbataski.ui.theme.SluzbaTaskiTheme

/**
 * ZASLON ZA DODAJANJE TASKA (polni zaslon, odpre se iz glavnega zaslona).
 * Za hitri vnos z domačega zaslona glej [QuickAddActivity].
 */
class AddTaskActivity : ComponentActivity() {
    private val vm: AddTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SluzbaTaskiTheme {
                Surface {
                    AddTaskScreen(vm, onClose = { finish() }, onSaved = {
                        Toast.makeText(this, "Task shranjen", Toast.LENGTH_SHORT).show()
                        finish()
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskScreen(vm: AddTaskViewModel, onClose: () -> Unit, onSaved: () -> Unit) {
    val state = rememberTaskFormState { TaskFormState() }
    val active by vm.active.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    val clients by vm.clients.collectAsStateWithLifecycle()

    val cancel = { state.discard(); onClose() }
    BackHandler(onBack = cancel)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nov task") },
            navigationIcon = { IconButton(onClick = cancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text("Delovno mesto", style = MaterialTheme.typography.labelLarge)
            WorkplaceSwitcher(active, workplaces, onSelect = vm::switchTo)
            TaskForm(
                state = state,
                clients = clients,
                autoFocus = true,
            )
            Button(onClick = { vm.save(state, onSaved) }, enabled = state.isValid, modifier = Modifier.fillMaxWidth()) {
                Text("Shrani")
            }
        }
    }
}
