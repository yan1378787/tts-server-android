package com.github.jing332.tts_server_android

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import tts_server_lib.LogCallback
import tts_server_lib.Tts_server_lib


class TtsIntentService(name: String = "TtsIntentService") : IntentService(name) {
    companion object {
        const val TAG = "TtsIntentService"
        var ACTION_SEND = "service.send" /* 广播ID */
        private var isWakeLock = false /* 是否使用唤醒锁 */
        var IsRunning = false /* 服务是否在运行 */
        var Isinited = false /* 已经初始化GoLib */
        var port: Int = 1233 /* 监听端口 */

        /*关闭服务，如有Http请求需要等待*/
        fun closeServer(context: Context): Boolean {
            val err = Tts_server_lib.closeServer(0)/* 5s */
            if (err.isNotEmpty()) {
                Toast.makeText(context, "关闭失败：$err", Toast.LENGTH_SHORT).show()
                return false
            }
            return true
        }
    }

    private lateinit var mWakeLock: PowerManager.WakeLock /* 唤醒锁 */

    @Deprecated("Deprecated in Java")
    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        IsRunning = true
        port = intent?.getIntExtra("port", 1233)!!
        isWakeLock = intent.getBooleanExtra("isWakeLock", false)

        val notification: Notification
        /*Android 12(S)+ 必须指定PendingIntent.FLAG_*/
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        /*点击通知跳转*/
        val pendingIntent =
            PendingIntent.getActivity(
                this, 0, Intent(
                    this,
                    MainActivity::class.java
                ), pendingIntentFlags
            )

        /*当点击退出按钮时发送广播*/
        val quitIntent = Intent(this, Receiver::class.java).apply { action = "quit_action" }
        val closePendingIntent: PendingIntent =
            PendingIntent.getBroadcast(this, 0, quitIntent, pendingIntentFlags)

        val chanId = "server_status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {/*Android 8.0+ 要求必须设置通知信道*/
            val chan = NotificationChannel(chanId, "前台服务", NotificationManager.IMPORTANCE_NONE)
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            service.createNotificationChannel(chan)

            val builder = Notification.Builder(applicationContext, chanId)
            notification =
                builder
                    .setContentTitle("TTS Server正在运行中...")
                    .setContentText("监听地址: localhost:$port")
                    .setSmallIcon(R.drawable.ic_app_notification)
                    .setContentIntent(pendingIntent)
                    .addAction(R.mipmap.notification_ic, "退出", closePendingIntent)
                    .build()

        } else { /*SDK < Android 8*/
            val action = Notification.Action(0, "退出", closePendingIntent)
            val builder = Notification.Builder(applicationContext)
            notification = builder
                .setContentTitle("TTS Server正在运行中...")
                .setContentText("监听地址: localhost:$port")
                .setSmallIcon(R.mipmap.ic_app_notification)
                .setContentIntent(pendingIntent)
                .addAction(action)
                .build()
        }
        startForeground(1, notification) //启动前台服务

        if (isWakeLock) { /* 启动唤醒锁 */
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            mWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "tts_server:ttsTag"
            )
            mWakeLock.acquire()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        IsRunning = false
        if (isWakeLock) { /* 释放唤醒锁 */
            mWakeLock.release()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onHandleIntent(intent: Intent?) {
        if (!Isinited) { /*初始化Go: 设置日志转发，注册Http.Server*/
            Tts_server_lib.init()
            Isinited = true
        }
        /*来自Go的日志*/
        val cb = LogCallback { s ->
            Log.d("LogCallback", s)
            sendLog(s)
        }
        /*启动Go服务并阻塞等待,直到关闭*/
        Tts_server_lib.runServer(port.toLong(), cb)
        sendClosedMsg()
    }

    //发送日志给MainActivity
    private fun sendLog(msg: String) {
        val i = Intent(ACTION_SEND)
        i.putExtra("sendLog", msg)
        sendBroadcast(i)
    }

    //发送关闭消息给MainActivity
    private fun sendClosedMsg() {
        val i = Intent(ACTION_SEND)
        i.putExtra("isClosed", true)
        sendBroadcast(i)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {/*点击通知上的退出按钮*/
            Log.d("TtsIntentService", "onReceive")
            closeServer(ctx!!)
        }
    }
}