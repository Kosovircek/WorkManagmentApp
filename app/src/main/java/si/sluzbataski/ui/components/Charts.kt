package si.sluzbataski.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.sluzbataski.stats.Bucket
import si.sluzbataski.stats.Slice

/*
 * Preprosti grafi, narisani neposredno s Compose Canvas (brez zunanje knjižnice).
 *
 * Pravila oblikovanja (berljivost):
 *  - tanki stolpci z zaobljenimi konci, 2 px razmak med sosednjima stolpcema
 *  - ena sama y-os, vrednosti so napisane z besedilom (ne samo z barvo)
 *  - barva sledi pomenu: "prejeti" je vedno modra, "opravljeni" vedno oranžna
 *  - za temni način so barve posebej izbrane (ne avtomatsko obrnjene)
 */

/** Barve serij: modra (prejeti) in oranžna (opravljeni), svetla/temna različica. */
object ChartColors {
    @Composable fun received(): Color = if (isSystemInDarkTheme()) Color(0xFF3987E5) else Color(0xFF2A78D6)
    @Composable fun completed(): Color = if (isSystemInDarkTheme()) Color(0xFFD95926) else Color(0xFFEB6834)
}

/** Ploščica z eno pomembno številko (KPI). */
@Composable
fun StatTile(title: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Legenda: barvni kvadratek + ime. */
@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Stolpčni graf "prejeti vs. opravljeni" po časovnih obdobjih.
 * Dotik stolpca izpiše natančne vrednosti nad grafom.
 */
@Composable
fun ReceivedCompletedChart(buckets: List<Bucket>, modifier: Modifier = Modifier) {
    val cReceived = ChartColors.received()
    val cCompleted = ChartColors.completed()
    val grid = MaterialTheme.colorScheme.outlineVariant
    var selected by remember(buckets) { mutableStateOf(buckets.lastIndex) }
    val maxV = (buckets.maxOfOrNull { maxOf(it.received, it.completed) } ?: 0).coerceAtLeast(1)

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(cReceived, "Prejeti")
            LegendItem(cCompleted, "Opravljeni")
        }
        val sel = buckets.getOrNull(selected)
        Text(
            if (sel != null) "${sel.label}: prejeti ${sel.received}, opravljeni ${sel.completed}" else "Ni podatkov",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).pointerInput(buckets) {
                detectTapGestures { pos ->
                    if (buckets.isNotEmpty()) {
                        selected = (pos.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)
                    }
                }
            },
        ) {
            if (buckets.isEmpty()) return@Canvas
            val slot = size.width / buckets.size
            val gap = 2.dp.toPx()
            val barW = ((slot - gap * 3) / 2).coerceIn(1f, 14.dp.toPx())
            val r = CornerRadius(minOf(4.dp.toPx(), barW / 2))
            // vodilne črte (0, polovica, maksimum)
            for (f in listOf(0f, 0.5f, 1f)) {
                val y = size.height * (1 - f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            buckets.forEachIndexed { i, b ->
                val cx = slot * i + slot / 2
                if (i == selected) drawRect(grid.copy(alpha = 0.35f), Offset(slot * i, 0f), Size(slot, size.height))
                val h1 = size.height * b.received / maxV
                val h2 = size.height * b.completed / maxV
                if (h1 > 0) drawRoundRect(cReceived, Offset(cx - gap / 2 - barW, size.height - h1), Size(barW, h1), r)
                if (h2 > 0) drawRoundRect(cCompleted, Offset(cx + gap / 2, size.height - h2), Size(barW, h2), r)
            }
        }
        // oznake x-osi: prva, srednja, zadnja (da se ne prekrivajo)
        Row(Modifier.fillMaxWidth()) {
            val labels = if (buckets.size <= 1) buckets.map { it.label }
            else listOf(buckets.first().label, buckets[buckets.size / 2].label, buckets.last().label)
            labels.forEachIndexed { i, l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = when (i) { 0 -> androidx.compose.ui.text.style.TextAlign.Start
                        labels.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center })
            }
        }
        Text("Najvišji stolpec = $maxV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Vodoravni stolpci z imenom levo in vrednostjo desno (npr. naročniki, nujnosti).
 * Če [Slice.colorArgb] ni podan, se uporabi modra.
 */
@Composable
fun HorizontalBars(slices: List<Slice>, format: (Double) -> String, modifier: Modifier = Modifier) {
    val default = ChartColors.received()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val maxV = slices.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (slices.isEmpty()) Text("Ni podatkov", style = MaterialTheme.typography.bodySmall)
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(110.dp))
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    drawRoundRect(track, size = size, cornerRadius = CornerRadius(4.dp.toPx()))
                    val w = (size.width * (s.value / maxV)).toFloat()
                    if (w > 0) drawRoundRect(s.colorArgb?.let { Color(it) } ?: default,
                        size = Size(w.coerceAtLeast(4.dp.toPx()), size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                }
                Text(format(s.value), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(64.dp).padding(start = 8.dp))
            }
        }
    }
}
