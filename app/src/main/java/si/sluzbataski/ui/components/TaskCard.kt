package si.sluzbataski.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.util.Format

/**
 * Kartica taska v seznamu.
 *
 *  ┃ Opis taska (do 3 vrstice)                    ▲
 *  ┃ Nujno · 3 dni · Naročnik · 📷                ▼   ✓
 *
 * @param onShift  premik nujnosti (true = bolj nujno); null skrije puščici
 * @param onDone   označi kot opravljeno; null skrije gumb
 */
@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onShift: ((up: Boolean) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    trailingInfo: String? = null,
) {
    val color = Color(task.urgency.colorArgb)
    val overdue = task.isOpen && task.hasDate && Format.isOverdue(task.dueAt!!, task.dueHasTime)
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(task.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                val sub = buildString {
                    if (task.hasDate) append("📅 ").append(Format.due(task.dueAt!!, task.dueHasTime))
                    else append(task.urgency.label)
                    if (task.isRecurring) append(" 🔁")
                    if (task.reminderMinutes != null && task.isOpen) append(" ⏰")
                    append(" · vnešen pred ").append(Format.age(task.createdAt))
                    task.client?.let { append(" · ").append(it) }
                    trailingInfo?.let { append(" · ").append(it) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub, style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (task.photoPath != null) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.PhotoCamera, "Ima sliko", Modifier.height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (onShift != null && task.urgency != Urgency.DATUM) {
                Column {
                    IconButton(onClick = { onShift(true) }, enabled = task.urgency != Urgency.URGENTNO, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Bolj nujno")
                    }
                    IconButton(onClick = { onShift(false) }, enabled = task.urgency != Urgency.KO_BO_CAS, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Manj nujno")
                    }
                }
            }
            if (onDone != null) {
                IconButton(onClick = onDone) {
                    Icon(Icons.Outlined.CheckCircle, "Opravljeno", tint = Color(0xFF43A047))
                }
            }
        }
    }
}
