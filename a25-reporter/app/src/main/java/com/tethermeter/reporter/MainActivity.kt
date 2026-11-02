<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
xmlns:tools="http://schemas.android.com/tools"
package="com.tethermeter.reporter">
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<!-- 안드로이드 14(API 34) dataSync 포그라운드 서비스 필수 권한 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- 모바일 데이터 통계 조회를 위한 필수 권한 -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>
<!-- AppCompatActivity와 호환되도록 AppCompat 테마 지정 (크래시 해결) -->
<application
android:allowBackup="true"
android:label="Tether Reporter"
android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">
<activity
android:name=".MainActivity"
android:exported="true">
<intent-filter>
<action android:name="android.intent.action.MAIN"/>
<category android:name="android.intent.category.LAUNCHER"/>
</intent-filter>
</activity>
<service
android:name=".ReporterService"
android:exported="false"
android:foregroundServiceType="dataSync"/>
</application>
</manifest>