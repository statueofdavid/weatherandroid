package space.declared.weather.data.source

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.suspendCancellableCoroutine
import space.declared.weather.data.TideData
import space.declared.weather.data.TidePrediction
import space.declared.weather.data.local.StationEntity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NoaaRemoteDataSource(context: Context) {

    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val TAG = "NoaaDataSource" // Tag for logging

    /**
     * Fetches the list of all active NOAA tide prediction stations from the network API.
     */
    suspend fun fetchAllStations(): List<StationEntity> {
        val url = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions"
        Log.d(TAG, "Fetching all stations from URL: $url") // Log the request URL
        val stationList = mutableListOf<StationEntity>()

        return suspendCancellableCoroutine { continuation ->
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    try {
                        val stationsArray = response.getJSONArray("stations")
                        Log.d(TAG, "Successfully fetched ${stationsArray.length()} stations.") // Log success
                        for (i in 0 until stationsArray.length()) {
                            val stationJson = stationsArray.getJSONObject(i)
                            stationList.add(
                                StationEntity(
                                    id = stationJson.getString("id"),
                                    name = stationJson.getString("name"),
                                    latitude = stationJson.getDouble("lat"),
                                    longitude = stationJson.getDouble("lng"),
                                    type = "NOAA"
                                )
                            )
                        }
                        if (continuation.isActive) continuation.resume(stationList)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing all stations from network", e)
                        if (continuation.isActive) continuation.resume(emptyList()) // Return empty on error
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to fetch all NOAA stations", error)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            )
            requestQueue.add(request)
        }
    }

    /**
     * Fetches the detailed tide prediction data for a single selected NOAA station.
     */
    fun fetchTideDataForStation(
        stationId: String,
        stationName: String, // FIX: Pass in the station name
        latitude: Double,
        longitude: Double,
        callback: OpenMeteoRemoteDataSource.ApiCallback<TideData?>
    ) {
        val url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?date=today&station=$stationId&product=predictions&datum=MLLW&time_zone=lst_ldt&interval=hilo&format=json&units=english"
        Log.d(TAG, "Fetching tide data for station $stationId from URL: $url") // Log the request URL

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                Log.d(TAG, "Response for station $stationId: $response") // Log the raw JSON response
                try {
                    // First, check if the API returned an explicit error object.
                    if (response.has("error")) {
                        val errorMessage = response.getJSONObject("error").getString("message")
                        Log.w(TAG, "API returned error for station $stationId: $errorMessage") // Log API-specific error
                        callback.onError(errorMessage)
                        return@JsonObjectRequest
                    }

                    // FIX: We only need to check for the "predictions" key now.
                    if (response.has("predictions")) {
                        val predictionsArray = response.getJSONArray("predictions")
                        val predictions = mutableListOf<TidePrediction>()
                        for (i in 0 until predictionsArray.length()) {
                            val predictionJson = predictionsArray.getJSONObject(i)
                            predictions.add(
                                TidePrediction(
                                    time = predictionJson.getString("t"),
                                    height = predictionJson.getString("v"),
                                    type = predictionJson.getString("type")
                                )
                            )
                        }
                        // FIX: Use the stationName that was passed into the function.
                        val tideData = TideData(stationName, latitude, longitude, predictions)
                        callback.onSuccess(tideData)
                    } else {
                        Log.w(TAG, "No predictions found for station $stationId") // Log missing data
                        callback.onSuccess(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing tide data for station $stationId", e)
                    callback.onError("Failed to parse tide data.")
                }
            },
            { error ->
                Log.e(TAG, "Tide data request failed for station $stationId", error)
                callback.onError(error.message ?: "Unknown NOAA error")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}