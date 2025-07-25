package space.declared.weather.data.source

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import kotlin.math.abs

/**
 * Represents a single tide prediction (high or low tide).
 */
data class TidePrediction(
    val time: String,
    val type: String, // "H" for high, "L" for low
    val height: String
)

/**
 * Handles network operations to fetch tide data from the NOAA API.
 */
class NoaaRemoteDataSource(context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    /**
     * Fetches tide predictions for a given latitude and longitude.
     * This is a two-step process: first find the nearest station, then get its predictions.
     */
    fun fetchTidePredictions(latitude: Double, longitude: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<List<TidePrediction>>) {
        // For simplicity in this example, we are using a hardcoded station list.
        // A production app would fetch this from a NOAA endpoint or a local database.
        val stations = getKnownTideStations()

        val closestStation = stations.minByOrNull { station ->
            abs(station.lat - latitude) + abs(station.lon - longitude)
        }

        if (closestStation != null) {
            // Check if the closest station is within a reasonable distance (e.g., 1 degree of lat/lon)
            if (abs(closestStation.lat - latitude) < 1.0 && abs(closestStation.lon - longitude) < 1.0) {
                fetchPredictionsForStation(closestStation.id, callback)
            } else {
                callback.onSuccess(emptyList()) // Station is too far away
            }
        } else {
            callback.onSuccess(emptyList()) // No stations found
        }
    }

    private fun fetchPredictionsForStation(stationId: String, callback: OpenMeteoRemoteDataSource.ApiCallback<List<TidePrediction>>) {
        val url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?station=$stationId&date=today&product=predictions&datum=MLLW&time_zone=lst_ldt&interval=hilo&units=english&format=json"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val predictionsArray = response.getJSONArray("predictions")
                    val predictions = mutableListOf<TidePrediction>()
                    for (i in 0 until predictionsArray.length()) {
                        val predictionJson = predictionsArray.getJSONObject(i)
                        predictions.add(
                            TidePrediction(
                                time = predictionJson.getString("t"),
                                type = predictionJson.getString("type"),
                                height = predictionJson.getString("v")
                            )
                        )
                    }
                    callback.onSuccess(predictions)
                } catch (e: Exception) {
                    callback.onError("Error parsing tide predictions")
                }
            },
            { error ->
                callback.onError(error.message ?: "Unknown error fetching tide predictions")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    // A small, hardcoded list of major US tide stations for demonstration purposes.
    private fun getKnownTideStations(): List<TideStation> {
        return listOf(
            TideStation("8454000", "Boston, MA", 42.35, -71.05),
            TideStation("8518750", "The Battery, NY", 40.70, -74.01),
            TideStation("8638863", "Sewell's Point, VA", 36.95, -76.33),
            TideStation("8724580", "Miami, FL", 25.76, -80.19),
            TideStation("9414290", "San Francisco, CA", 37.80, -122.46),
            TideStation("9447130", "Seattle, WA", 47.60, -122.34)
        )
    }

    private data class TideStation(val id: String, val name: String, val lat: Double, val lon: Double)
}