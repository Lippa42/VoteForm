package it.marcolipparini.sfide.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import it.marcolipparini.sfide.engine.EngineJson
import it.marcolipparini.sfide.engine.model.RoomDefinition
import it.marcolipparini.sfide.engine.samples.SampleRooms

/**
 * Foreground service che tiene vivo il server anche a schermo bloccato: è ciò che
 * rende il telefono un host affidabile. Può avviare una stanza d'esempio (per
 * modalità) o una stanza costruita dall'admin (passata come JSON).
 */
class HostService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopHost()
            return START_NOT_STICKY
        }
        startHost(resolveRoom(intent))
        return START_STICKY
    }

    private fun resolveRoom(intent: Intent?): RoomDefinition {
        intent?.getStringExtra(EXTRA_ROOM_JSON)?.let { json ->
            runCatching { EngineJson.decodeFromString(RoomDefinition.serializer(), json) }.getOrNull()?.let { return it }
        }
        return if (intent?.getStringExtra(EXTRA_MODE) == MODE_VOTING) {
            SampleRooms.cookingVoting()
        } else {
            SampleRooms.quizTournament()
        }
    }

    private fun startHost(room: RoomDefinition) {
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        HostController.start(applicationContext, room)
    }

    private fun stopHost() {
        HostController.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Host Sfide", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setContentTitle("Sfide · host attivo")
            .setContentText("Gli spettatori possono collegarsi sulla rete locale")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL = "host"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "it.marcolipparini.sfide.STOP"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ROOM_JSON = "room_json"
        const val MODE_QUIZ = "quiz"
        const val MODE_VOTING = "voting"

        /** Avvia una stanza d'esempio per la modalità indicata. */
        fun start(context: Context, mode: String = MODE_QUIZ) =
            launch(context, Intent(context, HostService::class.java).putExtra(EXTRA_MODE, mode))

        /** Avvia una stanza costruita dall'admin. */
        fun startWith(context: Context, room: RoomDefinition) = launch(
            context,
            Intent(context, HostService::class.java)
                .putExtra(EXTRA_ROOM_JSON, EngineJson.encodeToString(RoomDefinition.serializer(), room)),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, HostService::class.java).setAction(ACTION_STOP))
        }

        private fun launch(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
