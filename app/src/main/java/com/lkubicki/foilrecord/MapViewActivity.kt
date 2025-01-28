package com.lkubicki.foilrecord

import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.opencsv.CSVReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileReader
import com.lkubicki.foilrecord.databinding.ActivityMapViewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Locale

class MapViewActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapViewBinding
    private var pathPoints = mutableListOf<RunDataPoint>()
    private var animationJob: Job? = null
    private var isPlaying = false

    data class RunDataPoint(
        val position: LatLng,
        val speed: Float,
        val timestamp: String
    )

    // not sure why toolbar is removed here...
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityMapViewBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Set up the toolbar
//        setSupportActionBar(binding.toolbar)
//        // Enable the Up button
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//        supportActionBar?.setDisplayShowHomeEnabled(true)
//
//        // Set up the map
//        val mapFragment = supportFragmentManager
//            .findFragmentById(R.id.mapView) as SupportMapFragment
//        mapFragment.getMapAsync(this)
//
//        // Set up toolbar
//        setSupportActionBar(binding.toolbar)
//        supportActionBar?.setDisplayHomeAsUpEnabled(true)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the play button
        binding.playButton.setOnClickListener {
            if (!isPlaying) {
                startAnimation()
            } else {
                stopAnimation()
            }
        }

        // Set up the map
//        val mapFragment = supportFragmentManager
//            .findFragmentById(R.id.mapView) as SupportMapFragment
//        mapFragment.getMapAsync(this)

        setSupportActionBar(binding.toolbar)
        // Enable the Up button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Set up the map
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun startAnimation() {
        if (pathPoints.isEmpty()) return

        isPlaying = true
        binding.playButton.setImageResource(android.R.drawable.ic_media_pause)

        // Clear previous lines and markers
        map.clear()

        // Add start marker
        map.addMarker(
            MarkerOptions()
                .position(pathPoints.first().position)
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        // Calculate total duration and compression factor
        val startTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .parse(pathPoints.first().timestamp)?.time ?: return
        val endTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .parse(pathPoints.last().timestamp)?.time ?: return
        val actualDuration = endTime - startTime
        val animationDuration = 30000L  // 60000L // 60 seconds in milliseconds
        val compressionFactor = actualDuration.toFloat() / animationDuration

        // Create moving marker
        val movingMarker = map.addMarker(
            MarkerOptions()
                .position(pathPoints.first().position)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        ) ?: return

        animationJob = lifecycleScope.launch {
            var lastDrawnIndex = 0
            val startAnimationTime = System.currentTimeMillis()

            while (isActive && lastDrawnIndex < pathPoints.size - 1) {
                val elapsedAnimationTime = System.currentTimeMillis() - startAnimationTime
                val simulatedTime = startTime + (elapsedAnimationTime * compressionFactor).toLong()

                // Find points to draw based on simulated time
                var currentIndex = lastDrawnIndex
                while (currentIndex < pathPoints.size) {
                    val pointTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        .parse(pathPoints[currentIndex].timestamp)?.time ?: break

                    if (pointTime <= simulatedTime) {
                        // Draw line segment if we have a previous point
                        if (currentIndex > lastDrawnIndex) {
                            val startPoint = pathPoints[currentIndex - 1]
                            val endPoint = pathPoints[currentIndex]

                            val color = when {
                                startPoint.speed <= 6f -> Color.RED
                                startPoint.speed <= 9f -> Color.YELLOW
                                else -> Color.GREEN
                            }

                            map.addPolyline(
                                PolylineOptions()
                                    .add(startPoint.position, endPoint.position)
                                    .color(color)
                                    .width(12f)
                            )
                        }

                        // Update marker position
                        movingMarker.position = pathPoints[currentIndex].position

                        lastDrawnIndex = currentIndex
                        currentIndex++
                    } else {
                        break
                    }
                }

                // Check if we've reached the end
                if (lastDrawnIndex >= pathPoints.size - 1) {
                    stopAnimation()
                    break
                }

                // Adjust delay based on current point's speed
                val currentSpeed = pathPoints[lastDrawnIndex].speed
                val baseDelay = 16L // Base delay for smooth animation (roughly 60fps)
                val speedFactor = maxOf(1f, currentSpeed / 5f) // Adjust this ratio to taste
                delay((baseDelay / speedFactor).toLong())
            }
        }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
        isPlaying = false
        binding.playButton.setImageResource(android.R.drawable.ic_media_play)

        // Redraw the complete route
        drawColoredPath()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAnimation()
    }

    // Add this override to handle the Up button click
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Change this line to use satellite view
        map.mapType = GoogleMap.MAP_TYPE_HYBRID  // or GoogleMap.MAP_TYPE_SATELLITE for pure satellite view

        intent.getStringExtra("FILE_PATH")?.let { filePath ->
            lifecycleScope.launch(Dispatchers.IO) {
                loadAndDisplayRoute(File(filePath))
            }
        }
    }

    private suspend fun loadAndDisplayRoute(file: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MapView", "Starting to read file")
                val reader = CSVReader(FileReader(file))
                // Skip header
                val header = reader.readNext()
                Log.d("MapView", "CSV Header: ${header?.joinToString()}")

                reader.readAll().forEach { line ->
                    try {
                        val timestamp = line[0]
                        val lat = line[1].toDouble()
                        val lng = line[2].toDouble()
                        val speedMph = line[4].toFloat() // Using the mph column

                        Log.d("MapView", "Read point: Lat=$lat, Lng=$lng, Speed=$speedMph mph")

                        pathPoints.add(RunDataPoint(
                            position = LatLng(lat, lng),
                            speed = speedMph,
                            timestamp = timestamp
                        ))
                    } catch (e: Exception) {
                        Log.e("MapView", "Error parsing line: ${e.message}")
                        Log.e("MapView", "Line content: ${line.joinToString()}")
                    }
                }

                Log.d("MapView", "Total points loaded: ${pathPoints.size}")

                withContext(Dispatchers.Main) {
                    drawColoredPath()
                    zoomToRoute()
                    showRouteStats()
                }
            } catch (e: Exception) {
                Log.e("MapView", "Error reading file: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun drawColoredPath() {
        Log.d("MapView", "Drawing path with ${pathPoints.size} points")
        for (i in 0 until pathPoints.size - 1) {
            val startPoint = pathPoints[i]
            val endPoint = pathPoints[i + 1]

            val color = when {
                startPoint.speed <= 6f -> {
                    Log.d("MapView", "Speed ${startPoint.speed} <= 6mph: RED")
                    Color.RED
                }
                startPoint.speed <= 9f -> {
                    Log.d("MapView", "Speed ${startPoint.speed} <= 9mph: YELLOW")
                    Color.YELLOW
                }
                else -> {
                    Log.d("MapView", "Speed ${startPoint.speed} > 9mph: GREEN")
                    Color.GREEN
                }
            }

            map.addPolyline(
                PolylineOptions()
                    .add(startPoint.position, endPoint.position)
                    .color(color)
                    .width(12f)
            )
        }

        // Add start marker
        pathPoints.firstOrNull()?.let { first ->
            Log.d("MapView", "Adding start marker at ${first.position}")
            map.addMarker(
                MarkerOptions()
                    .position(first.position)
                    .title("Start")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        // Add end marker
        pathPoints.lastOrNull()?.let { last ->
            Log.d("MapView", "Adding end marker at ${last.position}")
            map.addMarker(
                MarkerOptions()
                    .position(last.position)
                    .title("Finish")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }
    }

    private fun zoomToRoute() {
        if (pathPoints.isEmpty()) {
            Log.d("MapView", "No points to zoom to")
            return
        }

        val builder = LatLngBounds.Builder()
        pathPoints.forEach { builder.include(it.position) }

        val bounds = builder.build()
        val padding = 100 // pixels
        try {
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            map.moveCamera(cameraUpdate)
            Log.d("MapView", "Zoomed to route bounds")
        } catch (e: Exception) {
            Log.e("MapView", "Error zooming to route: ${e.message}")
        }
    }

    private fun showRouteStats() {
        val totalDistance = calculateTotalDistance()
        val averageSpeed = pathPoints.map { it.speed }.average()

        binding.statsCard.visibility = View.VISIBLE
        binding.distanceText.text = "%.2f miles".format(totalDistance)
        binding.avgSpeedText.text = "%.1f mph".format(averageSpeed)
    }

    private fun calculateTotalDistance(): Float {
        var distance = 0f
        for (i in 0 until pathPoints.size - 1) {
            val results = FloatArray(1)
            Location.distanceBetween(
                pathPoints[i].position.latitude,
                pathPoints[i].position.longitude,
                pathPoints[i + 1].position.latitude,
                pathPoints[i + 1].position.longitude,
                results
            )
            distance += results[0]
        }
        // Convert meters to miles
        return distance * 0.000621371f
    }
}