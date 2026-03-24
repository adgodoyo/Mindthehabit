package com.example.mindthehabit.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mindthehabit.data.local.dao.BehaviorDao
import com.example.mindthehabit.data.local.entity.LightReadingEntity
import com.example.mindthehabit.data.local.entity.ScreenEventEntity
import com.example.mindthehabit.data.local.entity.WifiEventEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SensorForegroundService : Service(), SensorEventListener, LocationListener {

    @Inject
    lateinit var behaviorDao: BehaviorDao

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var locationManager: LocationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> "ON"
                Intent.ACTION_SCREEN_OFF -> "OFF"
                Intent.ACTION_USER_PRESENT -> "UNLOCK"
                else -> null
            }
            event?.let { type ->
                serviceScope.launch {
                    behaviorDao.insertScreenEvent(
                        ScreenEventEntity(timestamp = System.currentTimeMillis(), eventType = type)
                    )
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
                ?: return
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            
            serviceScope.launch {
                behaviorDao.insertWifiEvent(
                    WifiEventEntity(
                        timestamp = System.currentTimeMillis(),
                        ssid = connectionInfo.ssid,
                        bssid = connectionInfo.bssid,
                        signalStrength = connectionInfo.rssi,
                        eventType = "CONNECTED"
                    )
                )
            }
        }

        override fun onLost(network: Network) {
            serviceScope.launch {
                behaviorDao.insertWifiEvent(
                    WifiEventEntity(
                        timestamp = System.currentTimeMillis(),
                        ssid = null,
                        bssid = null,
                        signalStrength = 0,
                        eventType = "DISCONNECTED"
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
            ?: throw IllegalStateException("SensorManager not available")
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
            ?: throw IllegalStateException("ConnectivityManager not available")
        locationManager = getSystemService(LocationManager::class.java)
            ?: throw IllegalStateException("LocationManager not available")

        createNotificationChannel()
        startForeground(1, createNotification())

        // Register Screen Listener
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000L, 50f, this)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            serviceScope.launch {
                behaviorDao.insertLightReading(LightReadingEntity(timestamp = System.currentTimeMillis(), lux = lux))
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        serviceScope.launch {
            behaviorDao.insertLocation(
                com.example.mindthehabit.data.local.entity.LocationEntity(
                    timestamp = System.currentTimeMillis(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    provider = location.provider ?: "UNKNOWN"
                )
            )
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        sensorManager.unregisterListener(this)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        locationManager.removeUpdates(this)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("sensor_service", "Sensor Monitoring", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "sensor_service")
            .setContentTitle("BehaviorLens Active")
            .setContentText("Acquiring hardware telemetry...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }
}
