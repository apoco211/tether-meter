
package com.tethermeter.reporter

import android.app.*
import android.content.*
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var totalInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        val title = TextView(this).apply { text="Tether Reporter - A25"; textSize=22f; setPadding(0,0,0,24) }
        urlInput = EditText(this).apply { hint="https://your-project.firebaseio.com"; setText(getPref("url")) }
        pathInput = EditText(this).apply { hint="/tether-meter/usage"; setText(getPref("path","/tether-meter/usage")) }
        totalInput = EditText(this).apply { hint="요금제 GB (예: 60)"; setText(getPref("total","60")) }
        val startBtn = Button(this).apply { text="▶ 모니터링 시작 (포그라운드)" }
        val stopBtn = Button(this).apply { text="■ 중지" }
        statusText = TextView(this).apply { text="대기중"; setPadding(0,24,0,0) }

        startBtn.setOnClickListener {
            savePrefs()
            val intent = Intent(this, ReporterService::class.java).apply {
                putExtra("url", urlInput.text.toString())
                putExtra("path", pathInput.text.toString())
                putExtra("total", totalInput.text.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            statusText.text = "🟢 모니터링 시작됨 - 알림바 확인"
        }
        stopBtn.setOnClickListener {
            stopService(Intent(this, ReporterService::class.java))
            statusText.text = "🔴 중지됨"
        }

        layout.addView(title); layout.addView(urlInput); layout.addView(pathInput); layout.addView(totalInput)
        layout.addView(startBtn); layout.addView(stopBtn); layout.addView(statusText)
        setContentView(layout)

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
    private fun getPref(k:String, def:String="") = getSharedPreferences("tether", MODE_PRIVATE).getString(k,def)?:def
    private fun savePrefs() {
        getSharedPreferences("tether", MODE_PRIVATE).edit()
            .putString("url", urlInput.text.toString())
            .putString("path", pathInput.text.toString())
            .putString("total", totalInput.text.toString()).apply()
    }
}
