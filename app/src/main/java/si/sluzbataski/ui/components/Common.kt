package si.sluzbataski.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.sluzbataski.data.entity.Workplace

/** Majhna barvna pika (barva delovnega mesta ali nujnosti). */
@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

/**
 * Izbirnik delovnega mesta v naslovni vrstici: prikaže ime aktivnega mesta,
 * klik odpre seznam za preklop.
 */
@Composable
fun WorkplaceSwitcher(
    active: Workplace?,
    all: List<Workplace>,
    onSelect: (Workplace) -> Unit,
    onManage: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(Color(active?.colorArgb ?: 0xFF3949AB), 12)
                Spacer(Modifier.width(8.dp))
                Text(active?.name ?: "…", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.forEach { w ->
                DropdownMenuItem(
                    text = { Text(w.name) },
                    leadingIcon = { ColorDot(Color(w.colorArgb), 12) },
                    trailingIcon = { if (w.id == active?.id) Icon(Icons.Default.Check, null) },
                    onClick = { open = false; onSelect(w) },
                )
            }
            if (onManage != null) {
                DropdownMenuItem(text = { Text("Uredi delovna mesta …") }, onClick = { open = false; onManage() })
            }
        }
    }
}

/** Pomožna funkcija: odpri aktivnost. */
inline fun <reified T : Any> Context.open(configure: Intent.() -> Unit = {}) {
    startActivity(Intent(this, T::class.java).apply(configure))
}
