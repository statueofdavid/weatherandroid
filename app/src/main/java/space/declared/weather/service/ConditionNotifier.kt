package space.declared.weather.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.declared.weather.R
import space.declared.weather.data.local.AppDatabase
import space.declared.weather.data.source.OpenMeteoRemoteDataSource
import space.declared.weather.data.source.UsgsRemoteDataSource // Import USGS source

// TODO: Implement UI for custom thresholds in next feature branch
class ConditionNotifier(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val openMeteoDataSource = OpenMeteoRemoteDataSource(appContext)
    private val usgsDataSource = UsgsRemoteDataSource(appContext) // Instantiate USGS source
    private val savedLocationDao = AppDatabase.getDatabase(appContext).savedLocationDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Assumes a similar method to get locations that have *condition* alerts enabled
            val locationsToAlert = savedLocationDao.getLocationsWithNotificationsEnabled()
            if (locationsToAlert.isEmpty()) return@withContext Result.success()

            val conditionChecks = locationsToAlert.map { location ->
                async {
                    val alertMessages = mutableListOf<String>()
                    var currentWind: Double? = null
                    var currentFlow: Double? = null

                    try {
                        // Use coroutineScope to run network calls in parallel for efficiency
                        coroutineScope {
                            // --- Check Weather Conditions (Wind, etc.) ---
                            val weatherJob = async {
                                try {
                                    // Now returns JSONObject directly, resolving the "Type Mismatch"
                                    val weatherData = openMeteoDataSource.fetchWeatherData(
                                        location.latitude,
                                        location.longitude,
                                        "imperial"
                                    )

                                    // Use 'opt' methods to avoid JSONExceptions if keys are missing
                                    val current = weatherData.optJSONObject("current")
                                    currentWind = current?.optDouble("wind_speed_10m")

                                    val threshold = location.windSpeedAlertThreshold ?: 0.0
                                    val wind = currentWind

                                    if (location.windSpeedAlertEnabled && threshold > 0 && wind != null && wind > threshold) {
                                        alertMessages.add("Wind speed at ${wind.toInt()} mph")
                                    } else {
                                        Log.d("TAG","cause my ide said that i needed an else here")
                                    }
                                } catch (e: Exception) {
                                    Log.e("ConditionNotifier", "Weather check failed", e)
                                }
                            }

                            // --- Check Water Conditions (Flow, Level) ---
                            val waterJob = async {
                                // Only run if the location is linked to a USGS station
                                if (location.usgsStationId != null) {
                                    val waterData = usgsDataSource.fetchWaterDataForStation(location.usgsStationId) // Assumes a suspend function
                                    currentFlow = waterData?.flowRateCfs

                                    val threshold = location.flowRateAlertThreshold ?: 0.0
                                    if (location.flowRateAlertEnabled && threshold > 0 && currentFlow!! > threshold) {
                                        alertMessages.add("Water flow at ${currentFlow!!.toInt()} CFS")
                                    }
                                }
                            }
                            awaitAll(weatherJob, waterJob)
                        }

                    } catch (e: java.net.UnknownHostException) {
                        Log.e("ConditionNotifier", "Network error for ${location.name}", e)
                        // Optionally update DB to show a network error state in the UI
                    } catch (e: Exception) {
                        Log.e("ConditionNotifier", "Error processing conditions for ${location.name}", e)
                        // Optionally update DB to show a generic error state
                    }

                    // SEND NOTIFICATION (if any alerts were triggered)
                    if (alertMessages.isNotEmpty()) {
                        val combinedMessage = alertMessages.joinToString(separator = " & ")
                        sendNotification(location.name, "$combinedMessage exceeds your thresholds.")
                    }

                    // UPDATE THE DATABASE with the latest checked values
                    val updatedLocation = location.copy(
                        lastConditionCheck = System.currentTimeMillis(),
                        currentWindSpeed = currentWind,
                        currentFlowRate = currentFlow
                        // update other fields as needed
                    )
                    savedLocationDao.update(updatedLocation)
                }
            }

            conditionChecks.awaitAll()
            Result.success()

        } catch (e: Exception) {
            Log.e("ConditionNotifier", "Catastrophic worker failure. Won't retry.", e)
            Result.failure()
        }
    }

    private fun sendNotification(locationName: String, message: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "CONDITION_ALERTS_CHANNEL" // Different channel for user control

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Condition Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for user-defined condition thresholds."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("Condition Alert: $locationName")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use your app's icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Use a different ID base to avoid overwriting hazard notifications
        notificationManager.notify(locationName.hashCode() + 1, notification)
    }
}