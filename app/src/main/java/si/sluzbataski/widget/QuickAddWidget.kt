package si.sluzbataski.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.data.entity.Workplace

/**
 * WIDGET ZA HITRI VNOS.
 *
 * Videz:
 *   ● Služba ⇄                      5 odprtih
 *   [ ✎  Nov task ...                       ]
 *   [Ko bo čas] [Normalno] [Nujno] [Urgentno]
 *   [ 📅 Z datumom ]          [ 📷 S sliko ]
 *
 * Widget sam ne more sprejeti tipkanja (omejitev Androida), zato vsak gumb odpre
 * majhno okno [si.sluzbataski.ui.add.QuickAddActivity] z že nastavljeno nujnostjo.
 * Po shranjevanju se okno zapre; ob naslednjem kliku je spet prazno ("reset").
 */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Podatke naložimo PRED izrisom (Glance izriše statičen posnetek).
        val graph = AppGraph.get(context)
        val workplace = graph.workplaces.getActive()
        val canSwitch = graph.db.workplaceDao().getActive().size > 1
        val openCount = workplace?.let { graph.tasks.getOpenSorted(it.id).size } ?: 0

        provideContent { Content(context, workplace, canSwitch, openCount) }
    }

    @Composable
    private fun Content(context: Context, workplace: Workplace?, canSwitch: Boolean, openCount: Int) {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                .background(WidgetColors.background).padding(10.dp),
        ) {
            WorkplaceHeader(workplace, canSwitch) {
                Text("$openCount odprtih", style = textStyle(12, WidgetColors.textMuted))
            }

            // Velik "vnosni" gumb – izgleda kot polje za vnos
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(44.dp).cornerRadius(12.dp)
                    .background(WidgetColors.surface).padding(horizontal = 12.dp)
                    .clickable(actionStartActivity(quickAddIntent(context, Urgency.NORMALNO))),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("✎  Nov task …", style = textStyle(15, WidgetColors.textMuted))
            }

            Spacer(GlanceModifier.height(8.dp))

            // Gumbi za nujnost
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val levels = listOf(Urgency.KO_BO_CAS, Urgency.NORMALNO, Urgency.NUJNO, Urgency.URGENTNO)
                levels.forEachIndexed { i, u ->
                    Chip(u.label, Color(u.colorArgb),
                        actionStartActivity(quickAddIntent(context, u)),
                        GlanceModifier.defaultWeight())
                    if (i < levels.lastIndex) Spacer(GlanceModifier.width(4.dp))
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Chip("📅 Z datumom", Color(Urgency.DATUM.colorArgb),
                    actionStartActivity(quickAddIntent(context, Urgency.DATUM)), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(4.dp))
                Chip("📷 S sliko", WidgetColors.accent,
                    actionStartActivity(quickAddIntent(context, Urgency.NORMALNO, camera = true)), GlanceModifier.defaultWeight())
            }
        }
    }
}

/** Sprejemnik, ki ga Android pokliče za ta widget (registriran v AndroidManifest.xml). */
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
