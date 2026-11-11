package si.sluzbataski

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import si.sluzbataski.reminder.ReminderScheduler

/**
 * Razred aplikacije – zažene se pred katerokoli aktivnostjo ali widgetom.
 *
 * Ob zagonu:
 *  - poskrbi, da obstaja vsaj eno delovno mesto,
 *  - v koledar vpiše taske z datumom, ki tja še niso bili vpisani,
 *  - ustvari kanal obvestil za opomnike in znova nastavi alarme.
 */
class SluzbaTaskiApp : Application() {
    /** Obseg za ozadna opravila, ki morajo preživeti posamezno aktivnost. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val graph = AppGraph.get(this)
        ReminderScheduler.createChannel(this)
        appScope.launch {
            graph.workplaces.ensureDefault()
            graph.tasks.syncPendingCalendar()
            graph.tasks.rescheduleAllReminders()
        }
    }
}
