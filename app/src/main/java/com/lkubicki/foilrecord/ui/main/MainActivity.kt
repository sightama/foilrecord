package com.lkubicki.foilrecord.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.lkubicki.foilrecord.R
import com.lkubicki.foilrecord.databinding.ActivityMainBinding
import com.lkubicki.foilrecord.service.LocationService
import com.lkubicki.foilrecord.ui.rundetails.RunDetailsActivity
import com.lkubicki.foilrecord.ui.runlist.RunListComposeActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DecimalFormat

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    // Format for displaying numbers with 2 decimal places
    private val decimalFormat = DecimalFormat("0.00")

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            Timber.d("All required permissions granted")
        } else {
            // Show error message to user
            showPermissionErrorDialog()
        }
    }

    // Broadcast receiver for location updates
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationService.ACTION_LOCATION_UPDATE) {
                val latitude = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0.0)
                val velocity = intent.getFloatExtra(LocationService.EXTRA_VELOCITY, 0f)
                val acceleration = intent.getFloatExtra(LocationService.EXTRA_ACCELERATION, 0f)
                val accuracy = intent.getFloatExtra(LocationService.EXTRA_ACCURACY, 0f)

                // Update ViewModel with new location data
                viewModel.updateLocationData(latitude, longitude, velocity, acceleration, accuracy)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag("FoilRecord").d("MainActivity onCreate started")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up Material Toolbar
        setSupportActionBar(binding.toolbar)

        // Setup click listeners
        binding.button.setOnClickListener { viewModel.startRecording() }
        binding.button2.setOnClickListener { viewModel.stopRecording() }
        binding.viewRunsButton.setOnClickListener { viewModel.onViewRunsClicked() }

        // Observe UI state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    updateUI(state)
                }
            }
        }

        // Observe one-time events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    handleEvent(event)
                }
            }
        }

        // Request permissions
        checkAndRequestPermissions()

        // Register broadcast receiver with proper Android version handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                locationUpdateReceiver,
                IntentFilter(LocationService.ACTION_LOCATION_UPDATE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
            registerReceiver(
                locationUpdateReceiver,
                IntentFilter(LocationService.ACTION_LOCATION_UPDATE)
            )
        }

        Timber.tag("FoilRecord").d("MainActivity onCreate completed")
    }

    private fun updateUI(state: MainState) {
        // Update location data text
        val locationText = if (state.currentLocation != null) {
            "Lat: ${decimalFormat.format(state.currentLocation.latitude)}\n" +
                    "Lon: ${decimalFormat.format(state.currentLocation.longitude)}\n" +
                    "Speed: ${decimalFormat.format(state.currentSpeed * 2.23694f)} mph\n" +
                    "Acceleration: ${decimalFormat.format(state.currentAcceleration)} m/s²\n" +
                    "Accuracy: ${decimalFormat.format(state.accuracy)} m"
        } else {
            "Waiting for location data..."
        }
        binding.textView2.text = locationText

        // Update button states
        binding.button.isEnabled = !state.isRecording
        binding.button2.isEnabled = state.isRecording

        // Update status card appearance
        if (state.isRecording) {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.recording_background)
            )
            binding.textView2.setTextColor(
                ContextCompat.getColor(this, R.color.recording_text)
            )
        } else {
            binding.statusCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.surface_light)
            )
            binding.textView2.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary_light)
            )
        }
    }

    private fun handleEvent(event: MainEvent) {
        when (event) {
            is MainEvent.ShowMessage -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
            }

            is MainEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(R.color.error))
                    .show()
            }

            is MainEvent.NavigateToRunDetails -> {
                val intent = Intent(this, RunDetailsActivity::class.java)
                intent.putExtra("RUN_ID", event.runId)
                startActivity(intent)
            }

            MainEvent.NavigateToRunList -> {
                val intent = Intent(this, RunListComposeActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Add background location permission for Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // Add notifications permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun showPermissionErrorDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs location permissions to track your foiling sessions. Please grant the permissions in the app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                // Open app settings
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationUpdateReceiver)
    }
}