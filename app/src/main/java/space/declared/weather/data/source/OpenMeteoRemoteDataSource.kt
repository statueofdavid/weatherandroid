package space.declared.weather.data.source

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import space.declared.weather.data.CityResult
import org.json.JSONObject

/**
 * Handles all network operations to fetch data from the Open-Meteo API.
 */
class OpenMeteoRemoteDataSource(context: Context) {

    // A single Volley request queue for the entire data source
    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    // A generic callback interface for handling API responses
    interface ApiCallback<T> {
        fun onSuccess(result: T)
        fun onError(error: String)
    }

    /**
     * Fetches a list of cities matching the search query.
     */
    fun fetchCityList(query: String, callback: ApiCallback<List<CityResult>>) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=5&language=en&format=json"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val resultsArray = response.optJSONArray("results")
                if (resultsArray == null || resultsArray.length() == 0) {
                    callback.onSuccess(emptyList()) // Return empty list if no results
                    return@JsonObjectRequest
                }

                val cityResults = List(resultsArray.length()) { i ->
                    CityResult.fromJson(resultsArray.getJSONObject(i))
                }
                callback.onSuccess(cityResults)
            },
            { error ->
                callback.onError(error.message ?: "Unknown error fetching city list")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    /**
     * Fetches the full weather data for a given latitude and longitude.
     */
    fun fetchWeatherData(latitude: Double, longitude: Double, callback: ApiCallback<JSONObject>) {
        val currentParams = "temperature_2m,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
        val hourlyParams = "relative_humidity_2m,precipitation_probability,cloud_cover"
        val dailyParams = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,uv_index_max"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=$currentParams&hourly=$hourlyParams&daily=$dailyParams&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm&timezone=auto&forecast_days=10"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                callback.onSuccess(response) // Return the raw JSON object on success
            },
            { error ->
                callback.onError(error.message ?: "Unknown error fetching weather data")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}
