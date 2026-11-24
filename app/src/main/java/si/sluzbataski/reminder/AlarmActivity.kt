package si.sluzbataski.reminder

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.widget.taskDetailIntent

/**
 * Celozaslonsko okno budilke (odpre se tudi na zaklenjenem zaslonu in prižge zaslon).
 *
 * Pokaže task, ki zvoni, in tri velike gumbe:
 *  - "Sprejeto"       → budilka utihne, task ostane odprt
 *  - "Opravljeno"     → budilka utihne, task je opravljen
 *  - "Odloži 10 min"  → budilka utihne in zazvoni znova čez 10 minut
 *
 * Gumb Nazaj budilke ne utiša – zvoni naprej, dokler se ne odločiš.
 * Ko ne zvoni noben task več, se okno zapre samo.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            SluzbaTaskiTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    val list by AlarmService.ringing.collectAsStateWithLifecycle()
                    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) finish() }
                    BackHandler { moveTaskToBack(true) }   // nazaj = skrij okno, budilka zvoni naprej
                    list.firstOrNull()?.let { AlarmScreen(it, list.size - 1) }
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Composable
    private fun AlarmScreen(r: AlarmService.Ringing, others: Int) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
                Icon(Icons.Outlined.Alarm, null, Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Opomnik", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(r.title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (r.text.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(r.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
                if (others > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Za tem še $others opomnik(ov)", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { AlarmService.acknowledge(this@AlarmActivity, r.taskId) },
                    modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text("✓ Sprejeto", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                if (r.taskId >= 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { AlarmService.complete(this@AlarmActivity, r.taskId) },
                            modifier = Modifier.weight(1f).height(56.dp)) { Text("Opravljeno") }
                        OutlinedButton(onClick = { AlarmService.snooze(this@AlarmActivity, r.taskId) },
                            modifier = Modifier.weight(1f).height(56.dp)) { Text("Odloži 10 min") }
                    }
                    TextButton(onClick = { openTask(r.taskId) }, modifier = Modifier.fillMaxWidth()) { Text("Sprejmi in odpri task") }
                }
            }
        }
    }

    /** Potrdi opomnik in odpre podrobnosti taska; na zaklenjenem zaslonu najprej prosi za odklep. */
    private fun openTask(taskId: Long) {
        val km = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    startActivity(taskDetailIntent(this@AlarmActivity, taskId))
                    AlarmService.acknowledge(this@AlarmActivity, taskId)
                }
            })
        } else {
            startActivity(taskDetailIntent(this, taskId))
            AlarmService.acknowledge(this, taskId)
        }
    }
}
