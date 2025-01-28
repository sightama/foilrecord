package com.lkubicki.foilrecord

import android.app.Service
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.io.File
import java.time.Instant
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.abs
import java.util.Date
import java.util.Locale


// New data point generated on average every 3.2 seconds tested on Pixel 9 Pro XL in good connection
class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentFile: File? = null
    private var lastLocation: Location? = null
    private var lastVelocity = 0f
    private var lastTimestamp = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val currentTime = Instant.now().toString()
                val velocity = calculateVelocity(location)
                val acceleration = calculateAcceleration(velocity)

                saveLocationData(location, currentTime, velocity, acceleration)

                lastLocation = location
                lastVelocity = velocity
                lastTimestamp = location.time

                // Broadcast location update for UI
                sendLocationUpdateBroadcast(location, velocity, acceleration)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val channelId = createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Create new file for tracking
        currentFile = File(filesDir, generateFileName())
        currentFile?.writeText("Timestamp,Latitude,Longitude,Velocity(m/s),Velocity(mph),Acceleration(m/s²)\n")

        requestLocationUpdates()
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel(): String {
        val channelId = "location_service_channel"
        val channelName = "Location Tracking Service"
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    private fun createNotification(): Notification {
        val channelId = createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Run")
            .setContentText("Tracking your location...")
            .setSmallIcon(R.drawable.ic_notification) // Create this icon
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(1000)
            .setMinUpdateIntervalMillis(500)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun sendLocationUpdateBroadcast(
        location: Location,
        velocity: Float,
        acceleration: Float
    ) {
        Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_VELOCITY, velocity)
            putExtra(EXTRA_ACCELERATION, acceleration)
            LocalBroadcastManager.getInstance(this@LocationService).sendBroadcast(this)
        }
    }

    // Helper functions from your original MainActivity
    private fun calculateVelocity(currentLocation: Location): Float {
        // Note that the speed returned here may be more accurate than would be obtained simply by calculating distance / time for sequential positions, such as if the Doppler measurements from GNSS satellites are taken into account.
        val systemSpeed = currentLocation.speed

        // Calculate manual speed as fallback
        val manualSpeed = if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(currentLocation)
            val timeDiff = (currentLocation.time - lastLocation!!.time) / 1000f
            if (timeDiff > 0) distance / timeDiff else 0f
        } else 0f

        // Check if system speed seems reliable
        return when {
            // If speed is negative or unreasonably high (> 300 m/s or ~670 mph), use manual calculation
            systemSpeed < 0 || systemSpeed > 300 -> manualSpeed

            // If speed is 0 but we've moved a significant distance, use manual calculation
            systemSpeed == 0f && lastLocation != null &&
                    lastLocation!!.distanceTo(currentLocation) > 5 -> manualSpeed

            // Otherwise use system speed
            else -> systemSpeed
        }
    }

    private fun calculateAcceleration(currentVelocity: Float): Float {
        return if (lastTimestamp > 0) {
            val timeDiff = (System.currentTimeMillis() - lastTimestamp) / 1000f
            abs((currentVelocity - lastVelocity) / timeDiff)  // Using absolute value
        } else 0f
    }

    private fun convertToMph(velocityMs: Float): Float {
        return velocityMs * 2.23694f
    }

    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyMMddHHmm", Locale.getDefault())
        val current = formatter.format(Date())
        return "gps_data_${current}.csv"
    }

    private fun saveLocationData(
        location: Location,
        timestamp: String,
        velocity: Float,
        acceleration: Float
    ) {
        val velocityMph = convertToMph(velocity)
        val data =
            "$timestamp,${location.latitude},${location.longitude},${velocity},${velocityMph},${acceleration}\n"
        currentFile?.appendText(data)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "ACTION_STOP_TRACKING"
        const val ACTION_LOCATION_UPDATE = "ACTION_LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "EXTRA_LATITUDE"
        const val EXTRA_LONGITUDE = "EXTRA_LONGITUDE"
        const val EXTRA_VELOCITY = "EXTRA_VELOCITY"
        const val EXTRA_ACCELERATION = "EXTRA_ACCELERATION"
        private const val NOTIFICATION_ID = 1
    }
}