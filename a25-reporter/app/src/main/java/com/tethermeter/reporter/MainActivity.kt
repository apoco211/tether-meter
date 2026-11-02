package com.tethermeter.reporter

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var totalInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        scroll.addView(layout)

        val title = TextView(this).apply {
            text = "Tether Reporter - A25"
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }

        // 1. Firebase 실시간 데이터베이스 URL 입력창
        urlInput = EditText(this).apply {
            hint = "https://your-project.firebaseio.com"
            setText(getPref("url"))
        }

        // 2. 경로 입력창 (/tethering)
        pathInput = EditText(this).apply {
            hint = "/tethering"
            setText(getPref("path", "/tethering"))
        }

        // 3. 총 용량 입력창 (기본 30GB)
        totalInput = EditText(this).apply {
            hint = "요금제 GB (예: 30)"
            setText(getPref("total", "30"))
        }

        // 4. 조작 버튼들
        val startBtn = Button(this).apply { text = "▶ 모니터링 시작 (포그라운드)" }
        val stopBtn = Button(this).apply { text = "■ 중지" }

        // 5. 사용 정보 접근 권한 바로가기 버튼 (A25 필수 권한)
        val permBtn = Button(this).apply {
            text = "🔑 사용 정보 접근 권한 설정 열기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        statusText = TextView(this).apply {
            text = "대기중"
            setPadding(0, 24, 0, 0)
        }

        startBtn.setOnClickListener {
            savePrefs()
            val intent = Intent(this, ReporterService::class.java).apply {
                putExtra("url", urlInput.text.toString())
                putExtra("path", pathInput.text.toString())
                putExtra("total", totalInput.text.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            statusText.text = "🟢 모니터링 시작됨 - 알림바 확인"
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, ReporterService::class.java))
            statusText.text = "🔴 중지됨"
        }

        layout.addView(title)
        layout.addView(urlInput)
        layout.addView(pathInput)
        layout.addView(totalInput)
        layout.addView(startBtn)
        layout.addView(stopBtn)
        layout.addView(permBtn)
        layout.addView(statusText)

        setContentView(scroll)

        // 안드로이드 13 이상 알림 권한
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        checkUsagePermission()
    }

    override fun onResume() {
        super.onResume()
        checkUsagePermission()
    }

    private fun checkUsagePermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            statusText.text = "⚠️ 아래 '사용 정보 접근 권한 설정 열기'를 눌러 허용해 주세요."
        }
    }

    private fun getPref(k: String, def: String = "") =
        getSharedPreferences("tether", MODE_PRIVATE).getString(k, def) ?: def

    private fun savePrefs() {
        getSharedPreferences("tether", MODE_PRIVATE).edit()
            .putString("url", urlInput.text.toString())
            .putString("path", pathInput.text.toString())
            .putString("total", totalInput.text.toString())
            .apply()
    }
}