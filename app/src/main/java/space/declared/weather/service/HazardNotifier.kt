package space.declared.weather.service

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import space.declared.weather.data.source.OpenMeteoRemoteDataSource

class HazardNotifier(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val openMeteoRemoteDataSource = OpenMeteoRemoteDataSource(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // TODO: 1. Get saved locations from the database that have notifications enabled.
        // TODO: 2. Iterate through each location.
        // TODO: 3. Make a network call using openMeteoRemoteDataSource.fetchLightningData()
        // TODO: 4. Process the data, compare to last-known values.
        // TODO: 5. Send a notification if thresholds are met.
        // TODO: 6. Update last-known values.

        // Placeholder for now
        Result.success()
    }
}