package si.sluzbataski.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.R
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.TaskEventType
import si.sluzbataski.util.Format
import si.sluzbataski.widget.taskDetailIntent

/**
 * Sprejemnik alarmov in gumbov v obvestilu opomnika.
 *
 *  - [ACTION_FIRE]   alarm se je sprožil → obvestilo + močno vibriranje (kot budilka)
 *  - [ACTION_DONE]   gumb "Opravljeno" → task je opravljen
 *  - [ACTION_SNOOZE] gumb "Odloži 10 min" → nov alarm čez 10 minut
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val pending = goAsync() // delo v ozadju (baza) – sprejemnik ostane živ do finish()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = AppGraph.get(context)
                when (intent.action) {
                    ACTION_FIRE -> {
                        val task = graph.tasks.get(taskId)
                        if (task != null && task.isOpen) {
                            notify(context, task.id, task.description,
                                task.dueAt?.let { "Rok: " + Format.due(it, task.dueHasTime) } ?: "")
                            vibrate(context)
                            graph.db.taskEventDao().insert(TaskEvent(taskId = task.id, workplaceId = task.workplaceId,
                                type = TaskEventType.REMINDER, detail = ReminderScheduler.label(task.reminderMinutes)))
                        }
                    }
                    ACTION_DONE -> {
                        cancelNotification(context, taskId)
                        graph.tasks.complete(taskId)
                    }
                    ACTION_SNOOZE -> {
                        cancelNotification(context, taskId)
                        ReminderScheduler.snooze(context, taskId, 10)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, taskId: Long, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val open = PendingIntent.getActivity(context, taskId.toInt(), taskDetailIntent(context, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title + if (text.isNotEmpty()) "\n$text" else ""))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "✓ Opravljeno", action(context, ACTION_DONE, taskId))
            .addAction(0, "Odloži 10 min", action(context, ACTION_SNOOZE, taskId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(taskId.toInt(), n)
    }

    /** Dodatno vibriranje kot pri budilki (deluje tudi v tihem načinu, ker je "alarm"). */
    private fun vibrate(context: Context) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        val effect = VibrationEffect.createWaveform(VIBRATION, -1)
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private fun cancelNotification(context: Context, taskId: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(taskId.toInt())

    private fun action(context: Context, action: String, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(context, taskId.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(action)
                .setData(Uri.parse("sluzbataski://reminder/$action/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val ACTION_FIRE = "si.sluzbataski.REMINDER_FIRE"
        const val ACTION_DONE = "si.sluzbataski.REMINDER_DONE"
        const val ACTION_SNOOZE = "si.sluzbataski.REMINDER_SNOOZE"
        const val EXTRA_TASK_ID = "taskId"

        /** Vzorec vibriranja (premor, vibriranje, ...) v ms – približno 6 sekund, kot budilka. */
        val VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800, 400, 800, 400, 800, 400)
    }
}
