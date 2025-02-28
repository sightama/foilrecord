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
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    // Format for displaying numbers with 2 decimal places
    private val decimalFormat = DecimalFormat("0.00")

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Timber.d("Permission results received: $permissions")
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            Timber.d("All required permissions granted")
        } else {
            Timber.w("Some permissions were denied: ${permissions.filterValues { !it }}")
            // Show error message to user
            showPermissionErrorDialog()
        }
    }

    // Broadcast receiver for location updates
    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                Timber.d("Received broadcast: ${intent?.action}")
                if (intent?.action == LocationService.ACTION_LOCATION_UPDATE) {
                    val latitude = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0.0)
                    val velocity = intent.getFloatExtra(LocationService.EXTRA_VELOCITY, 0f)
                    val acceleration = intent.getFloatExtra(LocationService.EXTRA_ACCELERATION, 0f)
                    val accuracy = intent.getFloatExtra(LocationService.EXTRA_ACCURACY, 0f)

                    Timber.d("Location update: lat=$latitude, lng=$longitude, speed=$velocity, acc=$acceleration")

                    // Update ViewModel with new location data
                    viewModel.updateLocationData(latitude, longitude, velocity, acceleration, accuracy)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in locationUpdateReceiver.onReceive")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Timber.i("MainActivity.onCreate started")
            super.onCreate(savedInstanceState)

            try {
                Timber.d("Inflating layout")
                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)
            } catch (e: Exception) {
                Timber.e(e, "Error inflating layout")
                // Fallback to a simple TextView if binding fails
                val errorMessage = android.widget.TextView(this).apply {
                    text = "Error initializing UI: ${e.message}"
                    setPadding(50, 50, 50, 50)
                }
                setContentView(errorMessage)
                return
            }

            try {
                Timber.d("Setting up toolbar")
                // Set up Material Toolbar
                setSupportActionBar(binding.toolbar)
            } catch (e: Exception) {
                Timber.e(e, "Error setting up toolbar")
            }

            try {
                Timber.d("Setting up click listeners")
                // Setup click listeners
                binding.button.setOnClickListener {
                    Timber.d("Start recording button clicked")
                    viewModel.startRecording()
                }
                binding.button2.setOnClickListener {
                    Timber.d("Stop recording button clicked")
                    viewModel.stopRecording()
                }
                binding.viewRunsButton.setOnClickListener {
                    Timber.d("View runs button clicked")
                    viewModel.onViewRunsClicked()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting up click listeners")
            }

            try {
                Timber.d("Setting up UI state observation")
                // Observe UI state changes
                lifecycleScope.launch {
                    Timber.d("Starting UI state collection")
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.state.collectLatest { state ->
                            Timber.d("UI state updated: isRecording=${state.isRecording}")
                            updateUI(state)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting up UI state observation")
            }

            try {
                Timber.d("Setting up events observation")
                // Observe one-time events
                lifecycleScope.launch {
                    Timber.d("Starting events collection")
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.events.collect { event ->
                            Timber.d("Event received: $event")
                            handleEvent(event)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting up events observation")
            }

            try {
                Timber.d("Checking and requesting permissions")
                // Request permissions
                checkAndRequestPermissions()
            } catch (e: Exception) {
                Timber.e(e, "Error checking permissions")
            }

            try {
                Timber.d("Registering broadcast receiver")
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
                Timber.d("Broadcast receiver registered successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error registering broadcast receiver")
            }

            Timber.i("MainActivity.onCreate completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Fatal error in onCreate")
            // Last resort fallback
            try {
                val errorMessage = android.widget.TextView(this).apply {
                    text = "Fatal error: ${e.message}"
                    setPadding(50, 50, 50, 50)
                }
                setContentView(errorMessage)
            } catch (innerEx: Exception) {
                Log.e(TAG, "Could not even show error message: ${innerEx.message}")
            }
        }
    }

    private fun updateUI(state: MainState) {
        try {
            Timber.d("updateUI called with state: $state")

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
            Timber.d("updateUI completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error in updateUI")
        }
    }

    private fun handleEvent(event: MainEvent) {
        try {
            Timber.d("handleEvent called with event: $event")
            when (event) {
                is MainEvent.ShowMessage -> {
                    Timber.d("Showing message: ${event.message}")
                    try {
                        Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Timber.e(e, "Error showing Snackbar message")
                    }
                }

                is MainEvent.ShowError -> {
                    Timber.w("Showing error: ${event.message}")
                    try {
                        Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                            .setBackgroundTint(getColor(R.color.error))
                            .show()
                    } catch (e: Exception) {
                        Timber.e(e, "Error showing Snackbar error")
                    }
                }

                is MainEvent.NavigateToRunDetails -> {
                    Timber.d("Navigating to RunDetailsActivity with runId: ${event.runId}")
                    try {
                        val intent = Intent(this, RunDetailsActivity::class.java)
                        intent.putExtra("RUN_ID", event.runId)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Error navigating to RunDetailsActivity")
                    }
                }

                MainEvent.NavigateToRunList -> {
                    Timber.d("Navigating to RunListComposeActivity")
                    try {
                        val intent = Intent(this, RunListComposeActivity::class.java)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Error navigating to RunListComposeActivity")
                    }
                }
            }
            Timber.d("handleEvent completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error in handleEvent")
        }
    }

    private fun checkAndRequestPermissions() {
        try {
            Timber.d("checkAndRequestPermissions called")
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

            Timber.d("Permissions to request: $permissionsToRequest")
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                Timber.d("All permissions already granted")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in checkAndRequestPermissions")
        }
    }

    private fun showPermissionErrorDialog() {
        try {
            Timber.d("showPermissionErrorDialog called")
            MaterialAlertDialogBuilder(this)
                .setTitle("Permissions Required")
                .setMessage("This app needs location permissions to track your foiling sessions. Please grant the permissions in the app settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    Timber.d("Opening app settings")
                    // Open app settings
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = android.net.Uri.fromParts("package", packageName, null)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Error opening app settings")
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> Timber.d("Permission dialog cancelled") }
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Error showing permission error dialog")
        }
    }

    override fun onStart() {
        try {
            Timber.i("MainActivity.onStart called")
            super.onStart()
        } catch (e: Exception) {
            Timber.e(e, "Error in onStart")
        }
    }

    override fun onResume() {
        try {
            Timber.i("MainActivity.onResume called")
            super.onResume()
        } catch (e: Exception) {
            Timber.e(e, "Error in onResume")
        }
    }

    override fun onPause() {
        try {
            Timber.i("MainActivity.onPause called")
            super.onPause()
        } catch (e: Exception) {
            Timber.e(e, "Error in onPause")
        }
    }

    override fun onStop() {
        try {
            Timber.i("MainActivity.onStop called")
            super.onStop()
        } catch (e: Exception) {
            Timber.e(e, "Error in onStop")
        }
    }

    override fun onDestroy() {
        try {
            Timber.i("MainActivity.onDestroy called")
            try {
                Timber.d("Unregistering broadcast receiver")
                unregisterReceiver(locationUpdateReceiver)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering broadcast receiver")
            }
            super.onDestroy()
            Timber.i("MainActivity.onDestroy completed")
        } catch (e: Exception) {
            Timber.e(e, "Error in onDestroy")
        }
    }
}