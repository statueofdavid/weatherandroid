package space.declared.weather.data.source

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import space.declared.weather.data.TideData
import space.declared.weather.data.TidePrediction
import space.declared.weather.data.local.StationEntity
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Handles network operations to fetch tide data from the NOAA API.
 */
class NoaaRemoteDataSource(private val context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)
    private var stationList: List<TideStation>? = null

    /**
     * Fetches the entire list of NOAA stations from the bundled assets
     * and maps them to our StationEntity model for saving in the database.
     */
    fun fetchAllStations(callback: (List<StationEntity>) -> Unit) {
        val stations = loadStationsFromAssets()
        val stationEntities = stations.map { tideStation ->
            StationEntity(
                id = tideStation.id,
                name = tideStation.name,
                latitude = tideStation.lat,
                longitude = tideStation.lon,
                type = "NOAA" // Explicitly setting the type
            )
        }
        callback(stationEntities)
    }

    /**
     * Fetches tide predictions for all stations within a given radius.
     */
    // CHANGED: The 'radius' parameter is now explicitly 'radiusInMiles' for clarity
    fun fetchTidePredictions(latitude: Double, longitude: Double, radiusInMiles: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<List<TideData>>) {
        if (stationList == null) {
            stationList = loadStationsFromAssets()
        }

        // CHANGED: The incorrect filtering logic is replaced with the accurate Haversine formula calculation
        val nearbyStations = stationList?.filter { station ->
            val distance = calculateDistanceInMiles(latitude, longitude, station.lat, station.lon)
            distance <= radiusInMiles
        }

        if (nearbyStations.isNullOrEmpty()) {
            Log.d("NoaaDataSource", "No nearby tide stations found within $radiusInMiles miles.")
            callback.onSuccess(emptyList())
            return
        }

        Log.d("NoaaDataSource", "Found ${nearbyStations.size} nearby tide stations. Fetching predictions...")

        val results = mutableListOf<TideData>()
        var completedCalls = 0
        val totalCalls = nearbyStations.size

        nearbyStations.forEach { station ->
            fetchPredictionsForStation(station, object : OpenMeteoRemoteDataSource.ApiCallback<TideData?> {
                override fun onSuccess(result: TideData?) {
                    result?.let { results.add(it) }
                    completedCalls++
                    if (completedCalls == totalCalls) {
                        callback.onSuccess(results)
                    }
                }

                override fun onError(error: String) {
                    completedCalls++
                    if (completedCalls == totalCalls) {
                        callback.onSuccess(results) // Still return success with what we have
                    }
                }
            })
        }
    }

    private fun fetchPredictionsForStation(station: TideStation, callback: OpenMeteoRemoteDataSource.ApiCallback<TideData?>) {
        val url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?station=${station.id}&date=today&product=predictions&datum=MLLW&time_zone=lst_ldt&interval=hilo&units=english&format=json"

        Log.d("NoaaDataSource", "Requesting URL: $url")

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val predictionsArray = response.optJSONArray("predictions")
                    if (predictionsArray == null) {
                        callback.onSuccess(null) // Station may not have tide predictions
                        return@JsonObjectRequest
                    }

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
                    callback.onSuccess(TideData(station.name, station.lat, station.lon, predictions))
                } catch (e: Exception) {
                    Log.e("NoaaDataSource", "Error parsing NOAA response", e)
                    callback.onError("Error parsing tide predictions for station ${station.id}")
                }
            },
            { error ->
                Log.e("NoaaDataSource", "NOAA Request Failed: ${error.message}")
                callback.onError(error.message ?: "Unknown error fetching predictions for station ${station.id}")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    // ADDED: The accurate distance calculation helper function
    /**
     * Calculates the distance between two lat/lon points in miles using the Haversine formula.
     */
    private fun calculateDistanceInMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMiles = 3958.8 // Radius of the Earth in miles

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusMiles * c
    }

    private fun loadStationsFromAssets(): List<TideStation> {
        return try {
            context.assets.open("tide_stations_full.json").use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val stationListWrapperType = object : TypeToken<StationListWrapper>() {}.type
                    val wrapper: StationListWrapper = Gson().fromJson(reader, stationListWrapperType)
                    wrapper.stations
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private data class StationListWrapper(val stations: List<TideStation>)

    private data class TideStation(
        val id: String,
        val name: String,
        val lat: Double,
        @SerializedName("lng") val lon: Double
    )
}