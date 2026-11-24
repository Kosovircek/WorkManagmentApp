package si.sluzbataski.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph

/**
 * Po ponovnem zagonu telefona (ali posodobitvi aplikacije) Android pozabi vse alarme,
 * zato jih tu nastavimo znova za vse odprte taske z opomnikom.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.get(context).tasks.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
