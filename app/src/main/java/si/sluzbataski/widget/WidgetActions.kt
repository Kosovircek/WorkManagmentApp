package si.sluzbataski.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Urgency
import si.sluzbataski.ui.add.QuickAddActivity
import si.sluzbataski.ui.detail.TaskDetailActivity
import si.sluzbataski.ui.main.MainActivity

/*
 * Dejanja (akcije), ki jih sprožijo kliki na widgetih.
 *
 * - ActionCallback razredi se izvedejo v ozadju, BREZ odpiranja aplikacije
 *   (preklop delovnega mesta, označitev taska kot opravljenega).
 * - Funkcije *Intent() sestavijo Intent za odpiranje aktivnosti.
 *   Vsak Intent ima svoj unikaten "data" naslov, ker Android sicer Intente, ki se
 *   razlikujejo le po dodatkih (extras), obravnava kot enake in bi vsi gumbi
 *   odprli isto stvar.
 */

/** Klik na ime delovnega mesta na widgetu: preklopi na naslednje mesto. */
class SwitchWorkplaceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AppGraph.get(context).workplaces.cycleNext()
        WidgetUpdater.updateAll(context)
    }
}

/** Klik na kljukico ob tasku v widgetu: task je opravljen in izgine s seznama. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TASK_ID] ?: return
        AppGraph.get(context).tasks.complete(id)  // repozitorij sam osveži widgete
    }

    companion object {
        val TASK_ID = ActionParameters.Key<Long>("taskId")
    }
}

/** Intent za okno hitrega vnosa z izbrano nujnostjo in/ali takojšnjim zagonom kamere. */
fun quickAddIntent(context: Context, urgency: Urgency, camera: Boolean = false): Intent =
    Intent(context, QuickAddActivity::class.java).apply {
        data = Uri.parse("sluzbataski://quickadd/${urgency.name}/$camera")
        putExtra(QuickAddActivity.EXTRA_URGENCY, urgency.name)
        putExtra(QuickAddActivity.EXTRA_CAMERA, camera)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

fun taskDetailIntent(context: Context, taskId: Long): Intent =
    Intent(context, TaskDetailActivity::class.java).apply {
        data = Uri.parse("sluzbataski://task/$taskId")
        putExtra(TaskDetailActivity.EXTRA_TASK_ID, taskId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

fun mainIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        data = Uri.parse("sluzbataski://main")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/**
 * Zaprosi zaganjalnik (launcher), da widget postavi na domači zaslon.
 * To je zanesljivejše od iskanja v seznamu pripomočkov (npr. na Xiaomi/MIUI).
 * Uporabnik mora postavitev le potrditi v sistemskem oknu.
 *
 * @return false, če zaganjalnik tega ne podpira (takrat je treba widget dodati ročno)
 */
fun requestPinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(android.content.ComponentName(context, receiver), null, null)
}
