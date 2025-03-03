/**
 * Repository implementation that uses Room database
 */
package com.lkubicki.foilrecord.data.repository

import android.content.Context
import android.icu.text.SimpleDateFormat
import com.lkubicki.foilrecord.data.local.LocationPointEntity
import com.lkubicki.foilrecord.data.local.RunDao
import com.lkubicki.foilrecord.data.mapper.toLocationPoint
import com.lkubicki.foilrecord.data.mapper.toLocationPointEntity
import com.lkubicki.foilrecord.data.mapper.toRunData
import com.lkubicki.foilrecord.data.mapper.toRunEntity
import com.lkubicki.foilrecord.data.migration.CsvMigrationService
import com.lkubicki.foilrecord.data.model.LocationPoint
import com.lkubicki.foilrecord.data.model.RunData
import com.lkubicki.foilrecord.service.LocationServiceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileReader
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runDao: RunDao,
    private val locationService: LocationServiceManager,
    private val csvMigrationService: CsvMigrationService
) : RunRepository {

    // Tracking state
    private var currentRunId: String? = null
    private var currentRunFile: File? = null
    private var currentRunStartTime: Instant? = null
    private var pointsBuffer = mutableListOf<LocationPoint>()

    // Use Room for storage, true = use Room, false = use CSV (legacy)
    private val useRoomStorage = true

    override suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            currentRunStartTime = Instant.now()
            currentRunId = generateRunId()

            if (useRoomStorage) {
                // When using Room, still create a CSV file as backup
                currentRunFile = File(context.filesDir, generateFileName(currentRunId!!))
                currentRunFile?.writeText("Timestamp,Latitude,Longitude,Velocity(m/s),Velocity(mph),Acceleration(m/s²)\n")

                // Clear points buffer
                pointsBuffer.clear()
            } else {
                // Legacy CSV approach
                currentRunFile = File(context.filesDir, generateFileName(currentRunId!!))
                currentRunFile?.writeText("Timestamp,Latitude,Longitude,Velocity(m/s),Velocity(mph),Acceleration(m/s²)\n")
            }

            // Start location service
            locationService.startLocationUpdates()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
            false
        }
    }

    override suspend fun stopRecording(): File? = withContext(Dispatchers.IO) {
        try {
            // Stop location service
            locationService.stopLocationUpdates()

            if (useRoomStorage && currentRunId != null) {
                // When using Room, commit all buffered points to database
                val runId = currentRunId!!
                saveBufferedPointsToDatabase(runId)

                // Calculate run statistics
                updateRunStatistics(runId)
            }

            // Get a copy of the file before clearing state
            val file = currentRunFile

            // Clear state
            currentRunFile = null
            currentRunId = null
            currentRunStartTime = null
            pointsBuffer.clear()

            file
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop recording")
            null
        }
    }

    override suspend fun getAllRuns(): Flow<List<RunData>> {
        return if (useRoomStorage) {
            // Use Room database
            runDao.getAllRuns().map { entities ->
                entities.map { it.toRunData() }
            }
        } else {
            // Legacy approach - read from CSV files
            flow {
                val runs = withContext(Dispatchers.IO) {
                    context.filesDir.listFiles { file ->
                        file.name.startsWith("gps_data_") && file.name.endsWith(".csv")
                    }?.sortedByDescending { it.name }?.map { file ->
                        val id = file.name.substringAfter("gps_data_").substringBefore(".csv")
                        RunData(
                            id = id,
                            startTime = parseTimeFromFileName(id),
                            filePath = file.absolutePath
                        )
                    } ?: emptyList()
                }
                emit(runs)
            }
        }
    }

    override suspend fun getRunById(runId: String): Flow<RunData?> {
        return if (useRoomStorage) {
            // Use Room database
            flow {
                withContext(Dispatchers.IO) {
                    val runWithPoints = runDao.getRunWithPoints(runId)
                    runWithPoints?.toRunData()
                }?.let { emit(it) }
            }
        } else {
            // Legacy approach - read from CSV file
            flow {
                val file = File(context.filesDir, "gps_data_$runId.csv")
                if (!file.exists()) {
                    emit(null)
                    return@flow
                }

                withContext(Dispatchers.IO) {
                    parseRunDataFromCsv(file)
                }?.let { emit(it) }
            }
        }
    }

    override suspend fun saveLocationPoint(point: LocationPoint) {
        withContext(Dispatchers.IO) {
            // Always save to CSV for backup
            currentRunFile?.let { file ->
                val data = "${point.timestamp},${point.latitude},${point.longitude},${point.velocity},${point.velocityMph},${point.acceleration}\n"
                file.appendText(data)
            }

            if (useRoomStorage) {
                // When using Room, buffer points and save in batches
                currentRunId?.let { runId ->
                    pointsBuffer.add(point)

                    // Save to database in batches to improve performance
                    if (pointsBuffer.size >= POINTS_BUFFER_SIZE) {
                        saveBufferedPointsToDatabase(runId)
                    }
                }
            }
        }
    }

    override suspend fun deleteRun(runId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Always try to delete the CSV file
            val file = File(context.filesDir, "gps_data_$runId.csv")
            val fileDeleted = if (file.exists()) file.delete() else true

            if (useRoomStorage) {
                // Delete from Room database
                val dbDeleted = runDao.deleteRun(runId) > 0
                fileDeleted && dbDeleted
            } else {
                fileDeleted
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete run $runId")
            false
        }
    }

    /**
     * Save buffered points to database
     */
    private suspend fun saveBufferedPointsToDatabase(runId: String) {
        if (pointsBuffer.isEmpty()) return

        val points = pointsBuffer.map { it.toLocationPointEntity(runId) }
        runDao.insertPoints(points)

        // Clear buffer after saving
        pointsBuffer.clear()
    }

    /**
     * Update run statistics in database
     */
    private suspend fun updateRunStatistics(runId: String) {
        val run = runDao.getRunWithPoints(runId) ?: return

        var totalDistance = 0f
        var speedSum = 0f
        var maxSpeed = 0f
        var lastLat: Double? = null
        var lastLng: Double? = null

        // Calculate statistics from points
        run.points.forEach { point ->
            // Calculate distance
            if (lastLat != null && lastLng != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    lastLat!!, lastLng!!,
                    point.latitude, point.longitude,
                    results
                )
                totalDistance += results[0]
            }

            lastLat = point.latitude
            lastLng = point.longitude

            // Track speed statistics
            speedSum += point.velocityMph
            if (point.velocityMph > maxSpeed) maxSpeed = point.velocityMph
        }

        // Calculate average speed
        val avgSpeed = if (run.points.isNotEmpty()) {
            speedSum / run.points.size
        } else 0f

        // Convert distance from meters to miles
        val distanceMiles = totalDistance * 0.000621371f

        // Update run entity
        val updatedRun = run.run.copy(
            endTime = run.points.lastOrNull()?.timestamp ?: run.run.startTime,
            distance = distanceMiles,
            avgSpeed = avgSpeed,
            maxSpeed = maxSpeed,
            pointCount = run.points.size
        )

        runDao.insertRun(updatedRun)
    }

    /**
     * Parse complete run data from CSV file (used for legacy support)
     */
    private suspend fun parseRunDataFromCsv(file: File): RunData? = withContext(Dispatchers.IO) {
        try {
            val points = mutableListOf<LocationPoint>()
            var totalDistance = 0f
            var totalSpeed = 0f
            var maxSpeed = 0f
            var lastLat: Double? = null
            var lastLng: Double? = null

            try {
                val reader = com.opencsv.CSVReader(FileReader(file))
                // Skip header
                reader.readNext()

                reader.readAll().forEach { line ->
                    if (line.size >= 6) {
                        val timestamp = Instant.parse(line[0])
                        val latitude = line[1].toDouble()
                        val longitude = line[2].toDouble()
                        val velocity = line[3].toFloat()
                        val velocityMph = line[4].toFloat()
                        val acceleration = line[5].toFloat()

                        val point = LocationPoint(
                            timestamp = timestamp,
                            latitude = latitude,
                            longitude = longitude,
                            velocity = velocity,
                            velocityMph = velocityMph,
                            acceleration = acceleration
                        )

                        points.add(point)
                        totalSpeed += velocityMph
                        if (velocityMph > maxSpeed) maxSpeed = velocityMph

                        // Calculate distance
                        if (lastLat != null && lastLng != null) {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                lastLat!!, lastLng!!,
                                latitude, longitude,
                                results
                            )
                            totalDistance += results[0]
                        }

                        lastLat = latitude
                        lastLng = longitude
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing CSV file ${file.name}")
                return@withContext null
            }

            val id = file.name.substringAfter("gps_data_").substringBefore(".csv")
            val avgSpeed = if (points.isNotEmpty()) totalSpeed / points.size else 0f

            RunData(
                id = id,
                startTime = points.firstOrNull()?.timestamp ?: parseTimeFromFileName(id),
                endTime = points.lastOrNull()?.timestamp,
                filePath = file.absolutePath,
                points = points,
                distance = totalDistance * 0.000621371f, // Convert to miles
                avgSpeed = avgSpeed,
                maxSpeed = maxSpeed
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse run data from CSV ${file.name}")
            null
        }
    }

    private fun generateRunId(): String {
        val formatter = SimpleDateFormat("yyMMddHHmm", Locale.getDefault())
        return formatter.format(java.util.Date())
    }

    private fun generateFileName(runId: String): String {
        return "gps_data_${runId}.csv"
    }

    private fun parseTimeFromFileName(id: String): Instant {
        return try {
            val pattern = "yyMMddHHmm"
            val date = SimpleDateFormat(pattern, Locale.getDefault()).parse(id)
            Instant.ofEpochMilli(date.time)
        } catch (e: Exception) {
            Instant.now()
        }
    }

    companion object {
        private const val POINTS_BUFFER_SIZE = 20 // Number of points to buffer before batch save
    }
}