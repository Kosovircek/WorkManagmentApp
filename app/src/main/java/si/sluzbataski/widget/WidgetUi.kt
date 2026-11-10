package si.sluzbataski.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import si.sluzbataski.data.entity.Workplace

/*
 * Skupni gradniki in barve za oba widgeta (Glance je "Compose za widgete",
 * vendar z omejenim naborom elementov: Box, Row, Column, Text, Image, LazyColumn ...).
 */

object WidgetColors {
    val background = Color(0xF2FFFFFF)
    val surface = Color(0xFFF1F3F8)
    val text = Color(0xFF1B1B1F)
    val textMuted = Color(0xFF6B6F7A)
    val accent = Color(0xFF3949AB)
    val danger = Color(0xFFD32F2F)
}

fun textStyle(size: Int = 14, color: Color = WidgetColors.text, bold: Boolean = false) = TextStyle(
    color = ColorProvider(color),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

/**
 * Glava widgeta: barvna pika + ime delovnega mesta + gumb "⇄".
 *
 * Klik kamorkoli na ime ali gumb preklopi na naslednje delovno mesto. Preklop velja
 * povsod (aplikacija in oba widgeta). Če obstaja samo eno delovno mesto, gumb
 * pokaže "+ mesto" in odpre nastavitve, kjer dodaš novo.
 */
@Composable
fun WorkplaceHeader(workplace: Workplace?, canSwitch: Boolean, trailing: @Composable () -> Unit = {}) {
    val color = Color(workplace?.colorArgb ?: 0xFF3949AB)
    val context = LocalContext.current
    // Več mest: preklop v ozadju. Eno mesto: odpri nastavitve (dodaj novo mesto).
    val switch: Action = if (canSwitch) actionRunCallbackSwitch()
    else androidx.glance.appwidget.action.actionStartActivity(settingsIntent(context))
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().clickable(switch),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(color)) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(workplace?.name ?: "—", style = textStyle(15, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier.cornerRadius(10.dp).background(WidgetColors.surface)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(if (canSwitch) "⇄ zamenjaj" else "+ mesto", style = textStyle(11, WidgetColors.accent, bold = true), maxLines = 1)
            }
        }
        trailing()
    }
}

/** Majhen zaobljen gumb z besedilom. */
@Composable
fun Chip(label: String, color: Color, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .cornerRadius(14.dp)
            .background(color)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = textStyle(12, Color.White, bold = true), maxLines = 1)
    }
}

private fun actionRunCallbackSwitch(): Action =
    androidx.glance.appwidget.action.actionRunCallback<SwitchWorkplaceAction>()
