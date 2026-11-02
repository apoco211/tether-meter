package com.tethermeter.reporter

import android.app.*
import android.content.*
import android.net.*
import android.os.*
import android.app.usage.NetworkStatsManager
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.net.URL
import java.util.Calendar
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
        val path = intent?.getStringExtra("path") ?: "/tethering"
        val total = intent?.getStringExtra("total")?.toDoubleOrNull() ?: 80.0

        startForeground(1, buildNotification("모니터링 시작 - $url"))

        if (running) return START_STICKY
        running = true

        worker = thread {
            while (running) {
                try {
                    // 매월 1일 00:00:00부터 현재까지의 사용량 집계
                    val used = queryMonthlyUsage()
                    // 오늘 00:00:00부터 현재까지의 사용량 집계
                    val today = queryTodayUsage()
                    val left = (total - used).coerceAtLeast(0.0)

                    val payload = JSONObject().apply {
                        put("used", used)
                        put("left", left)
                        put("total", total)
                        put("today", today)
                        put("month", getCurrentMonthKey())
                        put("lastUpdate", System.currentTimeMillis())
                        put("device", "A25")
                    }

                    if (url.isNotBlank()) {
                        pushToFirebase(url, path, payload)
                    }
                    updateNotification("📡 당월 잔여 ${String.format("%.1f", left)}GB / ${total}GB (사용 ${String.format("%.1f", used)}GB)")
                } catch (e: Exception) {
                    updateNotification("오류: ${e.message}")
                }
                Thread.sleep(10000) // 10초 주기
            }
        }
        return START_STICKY
    }

    /**
     * 당월 1일 00:00:00.000 타임스탬프 계산
     * 매월 1일이 되는 순간 자동으로 시작 시점이 변경되어 사용량이 0GB로 리셋됩니다.
     */
    private fun getStartOfMonthMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getCurrentMonthKey(): String {
        val cal = Calendar.getInstance()
        return String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    private fun queryMonthlyUsage(): Double {
        return try {
            val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val startOfMonth = getStartOfMonthMillis()
            // 당월 1일 0시부터 현재 시각까지 모바일 데이터 사용량 쿼리
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                "",
                startOfMonth,
                System.currentTimeMillis()
            )
            val totalBytes = bucket.rxBytes + bucket.txBytes
            totalBytes / (1024.0 * 1024 * 1024)
        } catch (e: Exception) {
            parseProcNetDev()
        }
    }

    private fun queryTodayUsage(): Double {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            val nsm = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val bucket = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                "",
                cal.timeInMillis,
                System.currentTimeMillis()
            )
            val totalBytes = bucket.rxBytes + bucket.txBytes
            totalBytes / (1024.0 * 1024 * 1024)
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseProcNetDev(): Double {
        return try {
            val file = java.io.File("/proc/net/dev").readText()
            var total = 0L
            file.lines().forEach { line ->
                if (line.contains("rmnet") || line.contains("wlan1") || line.contains("ap0")) {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size > 10) {
                        total += (parts[1].toLongOrNull() ?: 0) + (parts[9].toLongOrNull() ?: 0)
                    }
                }
            }
            total / (1024.0 * 1024 * 1024).toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun pushToFirebase(baseUrl: String, path: String, json: JSONObject) {
        var u = baseUrl.trim().trimEnd('/')
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        val full = "$u$p.json"
        val conn = URL(full).openConnection() as HttpsURLConnection
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.doOutput = true
        conn.outputStream.use { it.write(json.toString().toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Tether Reporter", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Tether Reporter 동작중")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }
}