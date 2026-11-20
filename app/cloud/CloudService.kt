package com.github.lightjunction.magicbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject

class CloudService : Service() {
    private var generation = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            CloudRuntime.request(applicationContext, "status", JSONObject()) { result ->
                val state = result.optJSONObject("data")
                if (result.optBoolean("ok") && state?.optBoolean("running", false) == false) stopSelf()
            }
            handler.postDelayed(this, 15000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("proxy", "代理服务", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, CloudActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, CloudService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "proxy").setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("MagicBox").setContentText("本地代理正在运行；点击可查看真实连接状态")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "断开", stop).build()).build()
        startForeground(271, notification)
        handler.postDelayed(tick, 15000)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("generation") == true) generation = intent.getLongExtra("generation", 0L)
        if (intent?.action == "stop") CloudRuntime.request(applicationContext, "stop", JSONObject()) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        CloudRuntime.serviceDestroyed(applicationContext, generation)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
