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

/**
 * Barve widgetov. Večina ima svetlo (day) in temno (night) različico – widget sledi
 * temnemu načinu telefona samodejno (Android izbere pravo barvo).
 */
object WidgetColors {
    val background = DayNight(day = Color(0xF2FFFFFF), night = Color(0xF21E1F24))
    val surface = DayNight(day = Color(0xFFF1F3F8), night = Color(0xFF2C2E35))
    val text = DayNight(day = Color(0xFF1B1B1F), night = Color(0xFFE6E6EA))
    val textMuted = DayNight(day = Color(0xFF6B6F7A), night = Color(0xFFA5A8B3))
    val danger = DayNight(day = Color(0xFFD32F2F), night = Color(0xFFFF6B66))
    /** Poudarek (indigo) – dovolj kontrasten na obeh ozadjih. */
    val accent = Color(0xFF5C6BC0)
}

private fun DayNight(day: Color, night: Color): ColorProvider =
    androidx.glance.color.ColorProvider(day = day, night = night)

fun textStyle(size: Int = 14, color: ColorProvider = WidgetColors.text, bold: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

/** Različica za fiksno barvo (enaka v svetlem in temnem načinu). */
fun textStyle(size: Int, color: Color, bold: Boolean = false) = textStyle(size, ColorProvider(color), bold)

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
