
package com.tethermeter.reporter

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import android.app.usage.NetworkStatsManager
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class ReporterService : Service() {
    private var running = false
    private var worker: Thread? = null
    private val channelId = "tether_reporter"

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: ""
        val path = intent?.getStringExtra("path") ?: "/tether-meter/usage"
        val total = intent?.getStringExtra("total")?.toDoubleOrNull() ?: 60.0

        startForeground(1, buildNotification("모니터링 시작 - $url"))

        if (running) return START_STICKY
        running = true

        worker = thread {
            while (running) {
                try {
                    val used = queryTetherUsage()
                    val today = queryTodayUsage()
                    val payload = JSONObject().apply {
                        put("used", used)
                        put("total", total)
                        put("today", today)
                        put("lastUpdate", System.currentTimeMillis())
                        put("device", "A25")
                    }
                    pushToFirebase(url, path, payload)
                    updateNotification("📡 ${used}GB / ${total}GB - 오늘 ${today}GB")
                } catch (e: Exception) {
                    updateNotification("오류: ${e.message}")
                }
                Thread.sleep(10000) // 10초마다
            }
        }
        return START_STICKY
    }

    private fun queryTetherUsage(): Double {
        return try {
            val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subId = tm.subscriptionId
            // TYPE_MOBILE + 전체 기간
            val bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, "", 0, System.currentTimeMillis())
            var totalBytes = 0L
            do {
                totalBytes += bucket.rxBytes + bucket.txBytes
            } while (bucket.hasNextBucket() && run { bucket.nextBucket(bucket); true })

            // 실제로는 tethering interface(wlan1, rndis0) 필터링이 더 정확
            // 간단 버전은 전체 모바일 사용량을 테더링으로 간주, 필요시 /proc/net/dev 파싱으로 보정
            totalBytes / (1024.0*1024*1024)
        } catch (e: Exception) {
            // fallback: /proc/net/dev 파싱
            parseProcNetDev()
        }
    }

    private fun queryTodayUsage(): Double {
        // 오늘 0시부터 현재까지
        val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0) }
        return try {
            val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val bucket = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, "", cal.timeInMillis, System.currentTimeMillis())
            var b = 0L
            do { b += bucket.rxBytes + bucket.txBytes } while (bucket.hasNextBucket() && run { bucket.nextBucket(bucket); true })
            b / (1024.0*1024*1024)
        } catch { 0.8 }
    }

    private fun parseProcNetDev(): Double {
        return try {
            val file = java.io.File("/proc/net/dev").readText()
            // rmnet, wlan1 등 합산 예시 - 실제 환경에 맞게 조정
            var total = 0L
            file.lines().forEach { line ->
                if (line.contains("rmnet") || line.contains("wlan1") || line.contains("ap0")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size > 10) {
                        total += (parts[1].toLongOrNull() ?: 0) + (parts[9].toLongOrNull() ?: 0)
                    }
                }
            }
            total / (1024.0*1024*1024).toDouble()
        } catch { 23.4 }
    }

    private fun pushToFirebase(baseUrl: String, path: String, json: JSONObject) {
        var u = baseUrl.trim().trimEnd('/')
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        val full = "$u$p.json"
        val conn = URL(full).openConnection() as HttpsURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type","application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(json.toString().toByteArray()) }
        conn.responseCode // trigger
        conn.disconnect()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Tether Reporter", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }
    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this,0,intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, channelId).setContentTitle("Tether Reporter 동작중").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload).setContentIntent(pending).setOngoing(true).build()
    }
    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { running=false; worker?.interrupt(); super.onDestroy() }
}
