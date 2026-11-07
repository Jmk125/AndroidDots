package com.motiondots

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Surface
import android.view.WindowManager

/** Foreground service that owns the full-screen, touch-through dots overlay and the motion sensors. */
class DotsService : Service(), SensorEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var sensorManager: SensorManager
    private lateinit var display: Display
    private var view: DotsView? = null
    private var sensor: Sensor? = null
    private var usingRawAccel = false
    private val gravity = FloatArray(3)
    private var sensorsOn = false

    private lateinit var settings: DotSettings
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        view?.reload()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) stopSensors() else startSensors()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        display = (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        settings = DotSettings(this)

        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            usingRawAccel = true
        }

        addOverlay()
        startSensors()
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })

        running = true
        notifyStateChanged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (this::settings.isInitialized) {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            runCatching { unregisterReceiver(screenReceiver) }
            stopSensors()
            view?.let { runCatching { windowManager.removeView(it) } }
            view = null
        }
        running = false
        notifyStateChanged()
        super.onDestroy()
    }

    private fun addOverlay() {
        val v = DotsView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        windowManager.addView(v, lp)
        view = v
    }

    private fun startSensors() {
        if (sensorsOn || sensor == null) return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        sensorsOn = true
    }

    private fun stopSensors() {
        if (!sensorsOn) return
        sensorManager.unregisterListener(this)
        sensorsOn = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        var x = e.values[0]
        var y = e.values[1]
        if (usingRawAccel) { // strip gravity with a low-pass filter
            val a = 0.9f
            gravity[0] = a * gravity[0] + (1 - a) * e.values[0]
            gravity[1] = a * gravity[1] + (1 - a) * e.values[1]
            x -= gravity[0]
            y -= gravity[1]
        }
        // Device axes -> screen axes (x right, y up), accounting for display rotation.
        val right: Float
        val up: Float
        when (display.rotation) {
            Surface.ROTATION_90 -> { right = y; up = -x }
            Surface.ROTATION_180 -> { right = -x; up = -y }
            Surface.ROTATION_270 -> { right = -y; up = x }
            else -> { right = x; up = y }
        }
        view?.setAcceleration(right, up)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Motion dots", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, DotsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_dots)
            .setContentTitle("Motion dots are on")
            .setContentText("Tap to adjust, or stop them here.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_dots), "Stop", stop
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifyStateChanged() {
        stateListener?.invoke()
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, DotsTileService::class.java))
        }
    }

    companion object {
        private const val CHANNEL = "motion_dots"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.motiondots.STOP"

        @Volatile var running = false
        /** Set by the UI to be told when the overlay starts or stops. */
        @Volatile var stateListener: (() -> Unit)? = null

        fun hasOverlayPermission(ctx: Context) = Settings.canDrawOverlays(ctx)

        fun overlayPermissionIntent(ctx: Context) = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")
        )

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DotsService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DotsService::class.java))
        }
    }
}
