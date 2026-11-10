package si.sluzbataski.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.util.Format

/**
 * WIDGET S SEZNAMOM TASKOV.
 *
 * Prikazuje odprte taske aktivnega delovnega mesta v istem vrstnem redu kot glavna
 * aplikacija (najprej z datumom, nato po nujnosti, nato najstarejši).
 *
 *  - klik na ime delovnega mesta  -> preklop na naslednje mesto
 *  - klik na "+"                  -> hitri vnos
 *  - klik na task                 -> odpre podrobnosti taska
 *  - klik na "✓"                  -> task je opravljen in izgine s seznama
 */
class TaskListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = AppGraph.get(context)
        val initial = WidgetData.load(graph)

        provideContent {
            // Podatki se opazujejo (glej WidgetData.observe), zato se seznam takoj
            // posodobi ob preklopu delovnega mesta ali spremembi taskov.
            val data = WidgetData.observe(graph, initial)
            Content(context, data.workplace, data.canSwitch, data.tasks.take(MAX_ITEMS))
        }
    }

    @Composable
    private fun Content(context: Context, workplace: Workplace?, canSwitch: Boolean, tasks: List<Task>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                .background(WidgetColors.background).padding(10.dp),
        ) {
            WorkplaceHeader(workplace, canSwitch) {
                Text("☰", style = textStyle(18, WidgetColors.accent, bold = true),
                    modifier = GlanceModifier.padding(horizontal = 8.dp).clickable(actionStartActivity(mainIntent(context))))
                Box(
                    modifier = GlanceModifier.size(30.dp).cornerRadius(15.dp).background(WidgetColors.accent)
                        .clickable(actionStartActivity(quickAddIntent(context, Urgency.NORMALNO))),
                    contentAlignment = Alignment.Center,
                ) { Text("+", style = textStyle(18, Color.White, bold = true)) }
            }

            if (tasks.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ni odprtih taskov 🎉", style = textStyle(14, WidgetColors.textMuted))
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(tasks, itemId = { it.id }) { task -> TaskRow(context, task) }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(context: Context, task: Task) {
        val urgencyColor = Color(task.urgency.colorArgb)
        val overdue = task.hasDate && Format.isOverdue(task.dueAt!!, task.dueHasTime)
        Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(WidgetColors.surface)
                    .padding(start = 0.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Barvni trak nujnosti
                Box(GlanceModifier.width(5.dp).height(34.dp).cornerRadius(3.dp).background(urgencyColor)) {}
                Spacer(GlanceModifier.width(8.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionStartActivity(taskDetailIntent(context, task.id))),
                ) {
                    Text(task.description, style = textStyle(14), maxLines = 2)
                    val sub = buildString {
                        if (task.hasDate) append("📅 ").append(Format.due(task.dueAt!!, task.dueHasTime))
                        else append(task.urgency.label).append(" · ").append(Format.age(task.createdAt))
                        task.client?.let { append(" · ").append(it) }
                        if (task.photoPath != null) append(" · 📷")
                    }
                    Text(sub, style = textStyle(11, if (overdue) WidgetColors.danger else WidgetColors.textMuted), maxLines = 1)
                }
                // Gumb "opravljeno"
                Box(
                    modifier = GlanceModifier.size(34.dp).cornerRadius(17.dp)
                        .clickable(actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(CompleteTaskAction.TASK_ID to task.id))),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", style = textStyle(18, Color(0xFF43A047), bold = true)) }
            }
        }
    }

    private companion object { const val MAX_ITEMS = 60 }
}

class TaskListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskListWidget()
}
