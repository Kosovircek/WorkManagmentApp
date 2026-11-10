package si.sluzbataski.ui.add

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.ui.components.TaskForm
import si.sluzbataski.ui.components.TaskFormState
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.components.rememberTaskFormState
import si.sluzbataski.ui.theme.SluzbaTaskiTheme

/**
 * OKNO ZA HITRI VNOS – odpre se s klikom na widget za hitri vnos.
 *
 * Aktivnost je prosojna (tema Theme.SluzbaTaski.QuickAdd), zato izgleda kot okno
 * čez domači zaslon. Nujnost je prednastavljena iz gumba na widgetu
 * ([EXTRA_URGENCY]), gumb "S sliko" pa takoj odpre kamero ([EXTRA_CAMERA]).
 *
 * Po shranjevanju se okno zapre; ker ga widget vedno odpre na novo
 * (FLAG_ACTIVITY_CLEAR_TASK), je ob naslednjem kliku obrazec spet prazen.
 */
class QuickAddActivity : ComponentActivity() {
    private val vm: AddTaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urgency = Urgency.fromName(intent.getStringExtra(EXTRA_URGENCY))
        val camera = intent.getBooleanExtra(EXTRA_CAMERA, false)
        setContent {
            SluzbaTaskiTheme {
                QuickAddScreen(vm, urgency, camera, onClose = { finish() }, onSaved = {
                    Toast.makeText(this, "Task shranjen ✓", Toast.LENGTH_SHORT).show()
                    finish()
                })
            }
        }
    }

    companion object {
        const val EXTRA_URGENCY = "urgency"
        const val EXTRA_CAMERA = "camera"
    }
}

@Composable
private fun QuickAddScreen(
    vm: AddTaskViewModel,
    presetUrgency: Urgency,
    startCamera: Boolean,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val state = rememberTaskFormState { TaskFormState(urgency = presetUrgency) }
    val active by vm.active.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    val clients by vm.clients.collectAsStateWithLifecycle()
    val cancel = { state.discard(); onClose() }
    BackHandler(onBack = cancel)

    // Temno ozadje; klik nanj zapre okno (kot pri običajnem dialogu)
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = cancel)
            .systemBarsPadding().imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp).heightIn(max = 640.dp)
                // klik na kartico ne sme zapreti okna
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hitri vnos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    WorkplaceSwitcher(active, workplaces, onSelect = vm::switchTo)
                }
                TaskForm(
                    state = state,
                    clients = clients,
                    autoFocus = true,
                    startCamera = startCamera,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = cancel) { Text("Prekliči") }
                    Button(onClick = { vm.save(state, onSaved) }, enabled = state.isValid) { Text("Shrani") }
                }
            }
        }
    }
}
