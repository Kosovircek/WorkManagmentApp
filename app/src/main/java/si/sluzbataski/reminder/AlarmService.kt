package si.sluzbataski.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.R
import si.sluzbataski.SluzbaTaskiApp
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.TaskEventType

/**
 * OPOMNIK KOT BUDILKA.
 *
 * Ko se sproži opomnik taska, [ReminderReceiver] zažene to storitev. Storitev:
 *  - predvaja zvok budilke telefona v zanki (glasnost budilke, sliši se tudi v tihem načinu),
 *  - ves čas vibrira,
 *  - pokaže obvestilo, ki ga ni mogoče kar tako spregledati, in čez cel zaslon odpre
 *    [AlarmActivity] (tudi na zaklenjenem zaslonu),
 *  - zvoni, DOKLER uporabnik ne pritisne "Sprejeto", "Opravljeno" ali "Odloži".
 *
 * Če se nihče ne odzove [RING_TIMEOUT_MS] (10 min), budilka utihne in se sama
 * odloži za [AUTO_SNOOZE_MIN] minut – opomnik se torej ponavlja, dokler ga ne potrdiš.
 *
 * Seznam taskov, ki trenutno zvonijo, je v [ringing] (lahko jih je več hkrati).
 * Ko je seznam prazen, se storitev ustavi sama.
 */
class AlarmService : Service() {

    /** En task, ki trenutno zvoni. */
    data class Ringing(val taskId: Long, val title: String, val text: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var started = false

    /** Po 10 minutah brez odziva: utihni in vse odloži. */
    private val timeout = Runnable {
        val app = applicationContext
        _ringing.value.forEach { if (it.taskId >= 0) ReminderScheduler.snooze(app, it.taskId, AUTO_SNOOZE_MIN) }
        _ringing.value = emptyList()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Storitev sledi seznamu: ob vsaki spremembi osveži obvestilo, ob praznem seznamu se ustavi.
        scope.launch {
            _ringing.collect { list ->
                if (!started) return@collect
                if (list.isEmpty()) stopEverything()
                else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(list))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, Long.MIN_VALUE) ?: Long.MIN_VALUE
        if (taskId == Long.MIN_VALUE) {
            if (_ringing.value.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        val item = Ringing(taskId, intent?.getStringExtra(EXTRA_TITLE) ?: "Opomnik",
            intent?.getStringExtra(EXTRA_TEXT) ?: "")
        val list = _ringing.value.filter { it.taskId != taskId } + item
        _ringing.value = list

        // Android zahteva, da storitev v nekaj sekundah pokaže obvestilo.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(list),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        started = true

        startSound()
        startVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_TIMEOUT_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopSoundAndVibration()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopEverything() {
        handler.removeCallbacks(timeout)
        stopSoundAndVibration()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        started = false
        stopSelf()
    }

    // ------------------------------------------------------------------ zvok in vibriranje

    /** Zvok budilke v zanki. Če budilka nima zvoka, uporabi melodijo zvonjenja ali obvestila. */
    private fun startSound() {
        if (player != null) return
        val candidates = listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)
            .mapNotNull { RingtoneManager.getDefaultUri(it) }
        for (uri in candidates) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    setDataSource(this@AlarmService, uri)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK) // zvoni tudi ob ugasnjenem zaslonu
                    isLooping = true
                    prepare()
                    start()
                }
                return
            } catch (e: Exception) {
                player?.release(); player = null
            }
        }
    }

    private fun startVibration() {
        if (vibrator != null) return
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        // indeks 0 = ponavljaj vzorec v nedogled
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(VIBRATION, 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        vibrator = v
    }

    private fun stopSoundAndVibration() {
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    // ------------------------------------------------------------------ obvestilo

    private fun buildNotification(list: List<Ringing>): Notification {
        val first = list.first()
        val more = if (list.size > 1) " (+${list.size - 1} še)" else ""
        val fullScreen = PendingIntent.getActivity(this, 0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ ${first.title}$more")
            .setContentText(first.text.ifEmpty { "Tapni za potrditev" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                list.joinToString("\n") { it.title + if (it.text.isNotEmpty()) " – ${it.text}" else "" }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            // Android 14 dovoli odstranitev tudi "stalnih" obvestil – to štejemo kot "Sprejeto"
            .setDeleteIntent(ReminderReceiver.action(this, ReminderReceiver.ACTION_ACK, first.taskId))
            .addAction(0, "✓ Sprejeto", ReminderReceiver.action(this, ReminderReceiver.ACTION_ACK, first.taskId))
            .apply {
                if (first.taskId >= 0) {
                    addAction(0, "Opravljeno", ReminderReceiver.action(this@AlarmService, ReminderReceiver.ACTION_DONE, first.taskId))
                    addAction(0, "Odloži 10 min", ReminderReceiver.action(this@AlarmService, ReminderReceiver.ACTION_SNOOZE, first.taskId))
                }
            }
            .build()
    }

    companion object {
        const val CHANNEL_ID = "alarm_opomniki"
        private const val NOTIFICATION_ID = 0x5A1A
        private const val EXTRA_TASK_ID = "taskId"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        /** ID za "Preizkusi alarm" v nastavitvah (ni pravi task). */
        const val TEST_TASK_ID = -100L

        const val RING_TIMEOUT_MS = 10 * 60_000L
        const val AUTO_SNOOZE_MIN = 10

        /** Vzorec vibriranja (premor, vibriranje, ...) v ms. */
        val VIBRATION = longArrayOf(0, 800, 500, 800, 500, 800, 1200)

        private val _ringing = MutableStateFlow<List<Ringing>>(emptyList())

        /** Taski, ki trenutno zvonijo (prvi je prikazan na zaslonu). */
        val ringing: StateFlow<List<Ringing>> = _ringing

        /**
         * Zaženi budilko za task. Vrne false, če je Android ni dovolil zagnati
         * (takrat [ReminderReceiver] pokaže navadno obvestilo).
         */
        fun start(context: Context, taskId: Long, title: String, text: String): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java)
                .setData(Uri.parse("sluzbataski://alarm/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text))
            true
        } catch (e: Exception) {
            false
        }

        /** Utišaj budilko za ta task (storitev se ustavi, ko ne zvoni noben task več). */
        fun silence(taskId: Long) = _ringing.update { list -> list.filter { it.taskId != taskId } }

        /** "Sprejeto": utišaj in v zgodovino taska zapiši, da je bil opomnik potrjen. */
        fun acknowledge(context: Context, taskId: Long) {
            silence(taskId)
            if (taskId < 0) return
            val app = context.applicationContext as SluzbaTaskiApp
            app.appScope.launch {
                val graph = AppGraph.get(app)
                val task = graph.tasks.get(taskId) ?: return@launch
                graph.db.taskEventDao().insert(TaskEvent(taskId = task.id, workplaceId = task.workplaceId,
                    type = TaskEventType.REMINDER, detail = "Opomnik potrjen"))
            }
        }

        /** "Opravljeno": utišaj in označi task kot opravljen. */
        fun complete(context: Context, taskId: Long) {
            silence(taskId)
            if (taskId < 0) return
            val app = context.applicationContext as SluzbaTaskiApp
            app.appScope.launch { AppGraph.get(app).tasks.complete(taskId) }
        }

        /** "Odloži": utišaj in nastavi nov alarm čez [minutes] minut. */
        fun snooze(context: Context, taskId: Long, minutes: Int = 10) {
            silence(taskId)
            if (taskId >= 0) ReminderScheduler.snooze(context, taskId, minutes)
        }

        /**
         * Kanal za budilko. Zvok predvaja storitev sama (v zanki), zato kanal nima
         * svojega zvoka; ima pa najvišjo pomembnost, da se pokaže čez cel zaslon.
         */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Opomniki – budilka", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Opomnik taska, ki zvoni kot budilka, dokler ga ne potrdiš"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
