package com.lkubicki.foilrecord

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton


class MainActivity : AppCompatActivity() {
    private lateinit var textView2: TextView
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button
    private var isRecording = false


    // Helper function to format float to 2 decimal places - for mph below.
    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationService.ACTION_LOCATION_UPDATE) {
                val latitude = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0.0)
                val velocity = intent.getFloatExtra(LocationService.EXTRA_VELOCITY, 0f)
                val acceleration = intent.getFloatExtra(LocationService.EXTRA_ACCELERATION, 0f)

                updateUI(latitude, longitude, velocity, acceleration)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView2 = findViewById(R.id.textView2)
        recordButton = findViewById(R.id.button)
        stopButton = findViewById(R.id.button2)

        checkAndRequestPermissions()

        // Initialize button states
        stopButton.isEnabled = false

        // Add explicit view runs button setup
        val viewRunsButton = findViewById<MaterialButton>(R.id.viewRunsButton)
        viewRunsButton.setOnClickListener {
            val intent = Intent(this, RunListActivity::class.java)
            startActivity(intent)
            // Add this log to help debug
            Log.d("MainActivity", "View Runs button clicked")
        }

        recordButton.setOnClickListener { startRecording() }
        stopButton.setOnClickListener { stopRecording() }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationUpdateReceiver,
            IntentFilter(LocationService.ACTION_LOCATION_UPDATE)
        )
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.plus(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val notGrantedPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGrantedPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun startRecording() {
        Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START_TRACKING
            startService(this)
        }
        isRecording = true
        updateButtonStates()
        Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP_TRACKING
            startService(this)
        }
        isRecording = false
        updateButtonStates()
        Toast.makeText(this, "Recording stopped!", Toast.LENGTH_SHORT).show()
    }

    private fun updateButtonStates() {
        recordButton.isEnabled = !isRecording
        stopButton.isEnabled = isRecording
    }

    private fun updateUI(latitude: Double, longitude: Double, velocity: Float, acceleration: Float) {
        val velocityMph = velocity * 2.23694f
        textView2.text = "Lat: $latitude, Lon: $longitude\n" +
                "Velocity: $velocity m/s (${velocityMph.format(2)} mph)\n" +
                "Acceleration: $acceleration m/s²"
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(locationUpdateReceiver)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 123
    }
}