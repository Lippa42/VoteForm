package it.marcolipparini.sfide.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import it.marcolipparini.sfide.engine.samples.SampleRooms

/**
 * Foreground service che tiene vivo il server anche a schermo bloccato: è ciò che
 * rende il telefono un host affidabile. Delega l'avvio/arresto a [HostController].
 */
class HostService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopHost()
            return START_NOT_STICKY
        }
        startHost(intent?.getStringExtra(EXTRA_MODE) ?: MODE_QUIZ)
        return START_STICKY
    }

    private fun startHost(mode: String) {
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        val room = if (mode == MODE_VOTING) SampleRooms.cookingVoting() else SampleRooms.quizTournament()
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
        const val MODE_QUIZ = "quiz"
        const val MODE_VOTING = "voting"

        fun start(context: Context, mode: String = MODE_QUIZ) {
            val intent = Intent(context, HostService::class.java).putExtra(EXTRA_MODE, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, HostService::class.java).setAction(ACTION_STOP))
        }
    }
}
