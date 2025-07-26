package space.declared.weather.data.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import space.declared.weather.data.TideData
import space.declared.weather.data.TidePrediction
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Handles network operations to fetch tide data from the NOAA API.
 */
class NoaaRemoteDataSource(private val context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)
    private var stationList: List<TideStation>? = null

    /**
     * Fetches tide predictions for all stations within a given radius.
     */
    fun fetchTidePredictions(latitude: Double, longitude: Double, radius: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<List<TideData>>) {
        if (stationList == null) {
            stationList = loadStationsFromAssets()
        }

        val nearbyStations = stationList?.filter { station ->
            val latDistance = abs(station.lat - latitude)
            val lonDistance = abs(station.lon - longitude)
            latDistance < radius && lonDistance < radius
        }

        if (nearbyStations.isNullOrEmpty()) {
            callback.onSuccess(emptyList())
            return
        }

        val results = mutableListOf<TideData>()
        var completedCalls = 0

        nearbyStations.forEach { station ->
            fetchPredictionsForStation(station, object : OpenMeteoRemoteDataSource.ApiCallback<TideData?> {
                override fun onSuccess(result: TideData?) {
                    result?.let { results.add(it) }
                    completedCalls++
                    if (completedCalls == nearbyStations.size) {
                        callback.onSuccess(results)
                    }
                }

                override fun onError(error: String) {
                    completedCalls++
                    if (completedCalls == nearbyStations.size) {
                        callback.onSuccess(results)
                    }
                }
            })
        }
    }

    private fun fetchPredictionsForStation(station: TideStation, callback: OpenMeteoRemoteDataSource.ApiCallback<TideData?>) {
        val url = "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter?station=${station.id}&date=today&product=predictions&datum=MLLW&time_zone=lst_ldt&interval=hilo&units=english&format=json"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val predictionsArray = response.optJSONArray("predictions")
                    if (predictionsArray == null) {
                        callback.onSuccess(null)
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
                    callback.onError("Error parsing tide predictions")
                }
            },
            { error ->
                callback.onError(error.message ?: "Unknown error fetching tide predictions")
            }
        )
        requestQueue.add(jsonObjectRequest)
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