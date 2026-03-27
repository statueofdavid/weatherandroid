package space.declared.weather.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.declared.weather.R
import space.declared.weather.data.local.AppDatabase
import space.declared.weather.data.source.OpenMeteoRemoteDataSource

class HazardNotifier(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val openMeteoRemoteDataSource = OpenMeteoRemoteDataSource(appContext)
    private val savedLocationDao = AppDatabase.getDatabase(appContext).savedLocationDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val locationsToAlert = savedLocationDao.getLocationsWithNotificationsEnabled()
            if (locationsToAlert.isEmpty()) return@withContext Result.success()

            // locationsToAlert.map { location -> ... is correct, this will run them in parallel
            val lightningChecks = locationsToAlert.map { location ->
                async {
                    var finalCapeStatus = "Low"
                    var finalLightningStatus = "None"
                    var shouldAlert = false
                    var alertMessage: String? = null

                    try {
                        // NETWORK CALL
                        val lightningData = openMeteoRemoteDataSource.fetchLightningData(
                            location.latitude,
                            location.longitude
                        )

                        // JSON PARSING & LOGIC
                        val hourly = lightningData.getJSONObject("hourly")
                        val capeArray = hourly.getJSONArray("cape")
                        val lightningArray = hourly.getJSONArray("lightning_potential")
                        val timeArray = hourly.getJSONArray("time")
                        val sixHoursFromNow = (System.currentTimeMillis() / 1000) + (6 * 3600)

                        for (i in 0 until timeArray.length()) {
                            if (timeArray.getLong(i) > sixHoursFromNow) break

                            val capeValue = capeArray.optDouble(i, 0.0)
                            val lightningValue = lightningArray.optDouble(i, 0.0)

                            if (capeValue > 1000) {
                                finalCapeStatus = "High"
                                shouldAlert = true
                            } else if (capeValue > 500 && finalCapeStatus != "High") {
                                finalCapeStatus = "Moderate"
                            }

                            if (lightningValue > 75) {
                                finalLightningStatus = "High"
                                shouldAlert = true
                            } else if (lightningValue > 40 && finalLightningStatus != "High") {
                                finalLightningStatus = "Low"
                            }
                        }

                        // Set alert message if needed
                        if (shouldAlert) {
                            alertMessage = "High Hazard Risk Detected!" // Simplified message
                        }

                    } catch (e: java.net.UnknownHostException) {
                        // User is offline. Save this status to the DB for the UI.
                        Log.e("HazardNotifier", "Network error for ${location.name}", e)
                        finalCapeStatus = "Network Error"
                        finalLightningStatus = "N/A"
                    } catch (e: org.json.JSONException) {
                        // The API returned bad data or changed.
                        Log.e("HazardNotifier", "API JSON error for ${location.name}", e)
                        finalCapeStatus = "API Error"
                        finalLightningStatus = "N/A"
                    } catch (e: Exception) {
                        // Any other unexpected error.
                        Log.e("HazardNotifier", "Unknown error for ${location.name}", e)
                        finalCapeStatus = "Error"
                        finalLightningStatus = "N/A"
                    }


                    // SEND NOTIFICATION (if we determined we should)
                    alertMessage?.let {
                        sendNotification(location.name, it)
                    }

                    // UPDATE THE DATABASE
                    val updatedLocation = location.copy(
                        lastHazardCheck = System.currentTimeMillis(),
                        capeLevel = finalCapeStatus,
                        lightningRisk = finalLightningStatus
                    )
                    savedLocationDao.update(updatedLocation)
                }
            }

            lightningChecks.awaitAll()
            Result.success()

        } catch (e: Exception) {
            Log.e("HazardNotifier", "Catastrophic worker failure. Won't retry.", e)
            Result.failure()
        }
    }

    private fun sendNotification(locationName: String, message: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "HAZARD_ALERTS_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Environmental Hazard Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for potential environmental hazards like thunderstorms."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("Environmental Alert: $locationName")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(locationName.hashCode(), notification)
    }
}