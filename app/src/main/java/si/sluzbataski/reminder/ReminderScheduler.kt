package si.sluzbataski.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import si.sluzbataski.data.entity.Task
import si.sluzbataski.ui.main.MainActivity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Nastavljanje opomnikov za taske z datumom.
 *
 * Opomnik se sproži [Task.reminderMinutes] minut pred rokom. Pri tasku brez ure se kot
 * "rok" šteje ob [ALL_DAY_HOUR]:00 tistega dne.
 *
 * Uporabljamo AlarmManager.setAlarmClock – to je enak mehanizem kot pri budilki:
 * sproži se točno, tudi ko je telefon v varčevalnem načinu, in v statusni vrstici
 * pokaže ikono budilke. Ob sprožitvi [ReminderReceiver] pokaže obvestilo in vibrira.
 *
 * Alarmi ne preživijo ponovnega zagona telefona, zato jih [BootReceiver] nastavi znova.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "opomniki"
    private const val ALL_DAY_HOUR = 9

    /** Možnosti opomnika za izbirnik (minute pred rokom -> opis). */
    val OPTIONS: List<Pair<Int, String>> = listOf(
        0 to "Ob roku",
        5 to "5 min prej",
        15 to "15 min prej",
        30 to "30 min prej",
        60 to "1 uro prej",
        120 to "2 uri prej",
        24 * 60 to "1 dan prej",
        2 * 24 * 60 to "2 dni prej",
        7 * 24 * 60 to "1 teden prej",
    )

    fun label(minutes: Int?): String =
        if (minutes == null) "Brez opomnika" else OPTIONS.firstOrNull { it.first == minutes }?.second ?: "$minutes min prej"

    /** Čas sprožitve opomnika (epoch millis) ali null, če task opomnika nima. */
    fun triggerAt(task: Task): Long? {
        val due = task.dueAt ?: return null
        val minutes = task.reminderMinutes ?: return null
        val zone = ZoneId.systemDefault()
        val base = if (task.dueHasTime) due
        else Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atTime(LocalTime.of(ALL_DAY_HOUR, 0))
            .atZone(zone).toInstant().toEpochMilli()
        return base - minutes * 60_000L
    }

    /** Nastavi (ali prekliče) alarm glede na trenutno stanje taska. */
    fun schedule(context: Context, task: Task) {
        cancel(context, task.id)
        if (!task.isOpen || !task.hasDate) return
        val at = triggerAt(task) ?: return
        if (at <= System.currentTimeMillis()) return
        setAlarm(context, task.id, at)
    }

    /** Odloži opomnik za [minutes] minut (gumb "Odloži" v obvestilu). */
    fun snooze(context: Context, taskId: Long, minutes: Int = 10) =
        setAlarm(context, taskId, System.currentTimeMillis() + minutes * 60_000L)

    fun cancel(context: Context, taskId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, taskId))
    }

    private fun setAlarm(context: Context, taskId: Long, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context, taskId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // "show" intent: kaj se odpre, ko uporabnik tapne ikono budilke v sistemu
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            // brez dovoljenja za točne alarme: približen alarm (lahko zamudi nekaj minut)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun alarmIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, taskId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .setData(Uri.parse("sluzbataski://reminder/$taskId"))
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Kanal obvestil z visoko pomembnostjo in vibriranjem kot budilka (kliče se ob zagonu). */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Opomniki taskov", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Opomniki pred rokom taska"
            enableVibration(true)
            vibrationPattern = ReminderReceiver.VIBRATION
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
