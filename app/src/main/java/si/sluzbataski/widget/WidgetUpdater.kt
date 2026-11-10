package si.sluzbataski.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

/**
 * Osveži vse widgete aplikacije. Kliče se po VSAKI spremembi podatkov
 * (glej TaskRepository.afterChange) in po preklopu delovnega mesta.
 */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            QuickAddWidget().updateAll(context)
            TaskListWidget().updateAll(context)
        } catch (e: Exception) {
            Log.w("WidgetUpdater", "Osvežitev widgetov ni uspela", e)
        }
    }
}
