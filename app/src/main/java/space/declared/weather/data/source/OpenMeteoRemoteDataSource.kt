package space.declared.weather.data.source

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import space.declared.weather.data.CityResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles all network operations to fetch data from the Open-Meteo API.
 */
class OpenMeteoRemoteDataSource(context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    interface ApiCallback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }

//    fun fetchCityList(query: String, callback: ApiCallback<List<CityResult>>) {
//        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=en&format=json"
//
//        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
//            { response ->
//                val resultsArray = response.optJSONArray("results")
//                if (resultsArray == null || resultsArray.length() == 0) {
//                    callback.onSuccess(emptyList())
//                    return@JsonObjectRequest
//                }
//
//                val cityResults = List(resultsArray.length()) { i ->
//                    CityResult.fromJson(resultsArray.getJSONObject(i))
//                }
//                callback.onSuccess(cityResults)
//            },
//            { error ->
//                callback.onError(error.message ?: "Unknown error fetching city list")
//            }
//        )
//        requestQueue.add(jsonObjectRequest)
//    }

    // Change to 'suspend' and return 'List<CityResult>' directly
    suspend fun fetchCityList(query: String): List<CityResult> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=en&format=json"

        return suspendCancellableCoroutine { continuation ->
            val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    val resultsArray = response.optJSONArray("results")
                    if (resultsArray == null || resultsArray.length() == 0) {
                        continuation.resume(emptyList()) // Resume with empty list
                        return@JsonObjectRequest
                    }

                    val cityResults = List(resultsArray.length()) { i ->
                        CityResult.fromJson(resultsArray.getJSONObject(i))
                    }
                    continuation.resume(cityResults) // Resume with actual results
                },
                { error ->
                    continuation.resumeWithException(Exception(error.message ?: "Search failed"))
                }
            )
            requestQueue.add(jsonObjectRequest)
            continuation.invokeOnCancellation { jsonObjectRequest.cancel() }
        }
    }

    /**
     * Fetches the full weather data, including current, hourly, and daily summaries.
     */
    suspend fun fetchWeatherData(latitude: Double, longitude: Double, units: String): JSONObject {
        val tempUnit = if (units == "imperial") "fahrenheit" else "celsius"
        val windUnit = if (units == "imperial") "mph" else "kmh"

        // Parameters for the "current" weather object
        val currentParams = "temperature_2m,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"

        // Parameters for the "hourly" forecast object
        val hourlyParams = "temperature_2m,relative_humidity_2m,precipitation_probability,cloud_cover"

        // Parameters for the "daily" forecast object, now including summary data
        val dailyParams = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max"

        // Construct the final URL with all necessary parameters
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=$currentParams&hourly=$hourlyParams&daily=$dailyParams&temperature_unit=$tempUnit&wind_speed_unit=$windUnit&timeformat=unixtime&timezone=auto"

        return suspendCancellableCoroutine { continuation ->
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    if (continuation.isActive) {
                        continuation.resume(response) // This returns the JSONObject directly
                    }
                },
                { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception(error.message ?: "Unknown error"))
                    }
                }
            )
            requestQueue.add(request)
            continuation.invokeOnCancellation { request.cancel() }
        }
//        return suspendCancellableCoroutine { continuation ->
//            val request = JsonObjectRequest(Request.Method.GET, url, null,
//                { response ->
//                    if (continuation.isActive) {
//                        continuation.resume(response)
//                    }
//                },
//                { error ->
//                    if (continuation.isActive) {
//                        continuation.resumeWithException(Exception(error.message ?: "Unknown error fetching weather data"))
//                    }
//                }
//            )
//            requestQueue.add(request)
//            continuation.invokeOnCancellation { request.cancel() }
//        }
    }

    /**
     * Fetches a dedicated set of hourly variables for lightning and thunderstorm potential.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetchLightningData(latitude: Double, longitude: Double): JSONObject {
        return suspendCancellableCoroutine { continuation ->
            val hourlyParams = "lightning_potential,cape"
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&hourly=$hourlyParams&timeformat=unixtime&timezone=auto"

            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    if (continuation.isActive) {
                        continuation.resume(response)
                    }
                },
                { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception(error.message ?: "Unknown error fetching lightning data"))
                    }
                }
            )
            requestQueue.add(request)

            continuation.invokeOnCancellation {
                request.cancel()
            }
        }
    }
}