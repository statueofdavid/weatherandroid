package space.declared.weather.data.source

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import space.declared.weather.data.CurrentWaterData
import space.declared.weather.data.WaterLevelData
import space.declared.weather.data.local.StationEntity
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UsgsRemoteDataSource(context: Context) {

    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val TAG = "UsgsDataSource" // Tag for logging

    /**
     * Fetches all active USGS stations by making a separate API call for each US state.
     */
    suspend fun fetchAllStations(): List<StationEntity> = coroutineScope {
        val stateCodes = listOf(
            "al", "ak", "az", "ar", "ca", "co", "ct", "de", "fl", "ga", "hi", "id", "il",
            "in", "ia", "ks", "ky", "la", "me", "md", "ma", "mi", "mn", "ms", "mo", "mt",
            "ne", "nv", "nh", "nj", "nm", "ny", "nc", "nd", "oh", "ok", "or", "pa", "ri",
            "sc", "sd", "tn", "tx", "ut", "vt", "va", "wa", "wv", "wi", "wy",
            "as", "dc", "gu", "mp", "pr", "vi" // US territories
        )

        val deferredStations = stateCodes.map { stateCode ->
            async {
                fetchStationsForState(stateCode)
            }
        }
        deferredStations.awaitAll().flatten()
    }

    /**
     * Helper function to fetch all stations for a single state.
     */
    private suspend fun fetchStationsForState(stateCode: String): List<StationEntity> {
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&stateCd=$stateCode&parameterCd=00065&siteStatus=all"
        Log.d(TAG, "Fetching stations for state: $stateCode") // Log the request
        return suspendCancellableCoroutine { continuation ->
            val stationList = mutableListOf<StationEntity>()
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    try {
                        val timeSeries = response.getJSONObject("value").getJSONArray("timeSeries")
                        for (i in 0 until timeSeries.length()) {
                            val site = timeSeries.getJSONObject(i)
                            val sourceInfo = site.getJSONObject("sourceInfo")
                            val siteName = sourceInfo.getString("siteName")
                            val siteId = sourceInfo.getJSONArray("siteCode").getJSONObject(0).getString("value")
                            val geoLocation = sourceInfo.getJSONObject("geoLocation").getJSONObject("geogLocation")
                            stationList.add(
                                StationEntity(
                                    id = siteId,
                                    name = siteName,
                                    latitude = geoLocation.getDouble("latitude"),
                                    longitude = geoLocation.getDouble("longitude"),
                                    type = "USGS"
                                )
                            )
                        }
                        if (continuation.isActive) continuation.resume(stationList)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing stations for state $stateCode", e)
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to fetch stations for state $stateCode", error)
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            )
            requestQueue.add(request)
        }
    }


    /**
     * Fetches the live, detailed data for a single selected USGS station.
     */
    fun fetchWaterLevelForStation(stationId: String, callback: OpenMeteoRemoteDataSource.ApiCallback<WaterLevelData?>) {
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&sites=$stationId&parameterCd=00065&siteStatus=active"
        Log.d(TAG, "Fetching water level for station $stationId from URL: $url") // Log the request URL

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                Log.d(TAG, "Response for station $stationId: $response") // Log the raw JSON response
                try {
                    val timeSeries = response.getJSONObject("value").getJSONArray("timeSeries")
                    if (timeSeries.length() > 0) {
                        val site = timeSeries.getJSONObject(0)
                        val sourceInfo = site.getJSONObject("sourceInfo")
                        val siteName = sourceInfo.getString("siteName")
                        val geoLocation = sourceInfo.getJSONObject("geoLocation").getJSONObject("geogLocation")
                        val siteLat = geoLocation.getDouble("latitude")
                        val siteLon = geoLocation.getDouble("longitude")

                        val variable = site.getJSONObject("variable")
                        val variableName = variable.getString("variableName")
                        val unit = variable.getJSONObject("unit").getString("unitCode")

                        val valuesArray = site.getJSONArray("values").getJSONObject(0).getJSONArray("value")
                        if (valuesArray.length() > 0) {
                            val value = valuesArray.getJSONObject(0).getString("value")
                            val waterLevelData = WaterLevelData(siteName, siteLat, siteLon, variableName, value, unit)
                            callback.onSuccess(waterLevelData)
                        } else {
                            Log.w(TAG, "No 'value' array found for station $stationId")
                            callback.onSuccess(null)
                        }
                    } else {
                        Log.w(TAG, "No 'timeSeries' data found for station $stationId")
                        callback.onSuccess(null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing water level for station $stationId", e)
                    callback.onError("Failed to parse USGS data.")
                }
            },
            { error ->
                Log.e(TAG, "Water level request failed for station $stationId", error)
                callback.onError(error.message ?: "Unknown USGS error")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    /**
     * Fetches the latest instantaneous values for both streamflow (00060) and
     * gauge height (00065) for a single USGS station.
     * This is a suspend function designed for use in coroutines.
     */
    suspend fun fetchWaterDataForStation(stationId: String): CurrentWaterData? {
        // Note the parameter codes: 00060 for streamflow, 00065 for gauge height
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&sites=$stationId&parameterCd=00060,00065&siteStatus=active"
        Log.d(TAG, "Fetching water data for station $stationId")

        return suspendCancellableCoroutine { continuation ->
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    try {
                        val timeSeriesArray = response.getJSONObject("value").getJSONArray("timeSeries")
                        if (timeSeriesArray.length() == 0) {
                            if (continuation.isActive) continuation.resume(null)
                            return@JsonObjectRequest
                        }

                        // We expect two timeSeries objects, one for each parameter.
                        // We'll parse them and store the results here.
                        var flowRate: Double? = null
                        var gaugeHeight: Double? = null

                        val sourceInfo = timeSeriesArray.getJSONObject(0).getJSONObject("sourceInfo")
                        val stationName = sourceInfo.getString("siteName")

                        for (i in 0 until timeSeriesArray.length()) {
                            val series = timeSeriesArray.getJSONObject(i)
                            val variableCode = series.getJSONObject("variable")
                                .getJSONArray("variableCode")
                                .getJSONObject(0)
                                .getString("value")

                            val valuesArray = series.getJSONArray("values").getJSONObject(0).getJSONArray("value")
                            if (valuesArray.length() > 0) {
                                val value = valuesArray.getJSONObject(0).getString("value").toDoubleOrNull()
                                when (variableCode) {
                                    "00060" -> flowRate = value // Streamflow
                                    "00065" -> gaugeHeight = value // Gauge Height
                                }
                            }
                        }

                        val waterData = CurrentWaterData(stationId, stationName, flowRate, gaugeHeight)
                        if (continuation.isActive) continuation.resume(waterData)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing water data for station $stationId", e)
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                { error ->
                    Log.e(TAG, "Water data request failed for station $stationId", error)
                    if (continuation.isActive) continuation.resume(null)
                }
            )
            requestQueue.add(request)
        }
    }
}