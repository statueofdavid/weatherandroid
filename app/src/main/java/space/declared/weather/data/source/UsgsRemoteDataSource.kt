package space.declared.weather.data.source

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import space.declared.weather.data.WaterLevelData
import space.declared.weather.data.local.StationEntity
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ADDED: A data class to hold bounding box coordinates for clarity
data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/**
 * Handles network operations to fetch water level data from the USGS API.
 */
class UsgsRemoteDataSource(context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    /**
     * Fetches the entire list of active USGS monitoring stations and maps them to our StationEntity model.
     * Note: This can be a very large request and may take some time.
     */
    fun fetchAllStations(callback: (List<StationEntity>) -> Unit) {
        val url = "https://waterservices.usgs.gov/nwis/site/?format=json&siteStatus=active&parameterCd=00065"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val timeSeries = response.getJSONObject("value").getJSONArray("timeSeries")
                    val stationEntities = mutableListOf<StationEntity>()
                    for (i in 0 until timeSeries.length()) {
                        val site = timeSeries.getJSONObject(i)
                        val sourceInfo = site.getJSONObject("sourceInfo")
                        val siteName = sourceInfo.getString("siteName")
                        val siteCode = sourceInfo.getJSONArray("siteCode").getJSONObject(0).getString("value")
                        val geoLocation = sourceInfo.getJSONObject("geoLocation").getJSONObject("geogLocation")
                        val siteLat = geoLocation.getDouble("latitude")
                        val siteLon = geoLocation.getDouble("longitude")

                        stationEntities.add(
                            StationEntity(
                                id = siteCode,
                                name = siteName,
                                latitude = siteLat,
                                longitude = siteLon,
                                type = "USGS" // Explicitly setting the type
                            )
                        )
                    }
                    callback(stationEntities)
                } catch (e: Exception) {
                    callback(emptyList())
                }
            },
            {
                callback(emptyList())
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    /**
     * Fetches the latest water level reading for all monitoring sites within a given radius.
     */
    // CHANGED: The 'radius' parameter is now explicitly 'radiusInMiles' for clarity
    fun fetchWaterLevels(latitude: Double, longitude: Double, radiusInMiles: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<List<WaterLevelData>>) {
        // Calculate the bounding box
        val boundingBox = calculateBoundingBox(latitude, longitude, radiusInMiles)

        // Format each coordinate to 7 decimal places ... because the API
        val minLonStr = String.format(Locale.US, "%.7f", boundingBox.minLon)
        val minLatStr = String.format(Locale.US, "%.7f", boundingBox.minLat)
        val maxLonStr = String.format(Locale.US, "%.7f", boundingBox.maxLon)
        val maxLatStr = String.format(Locale.US, "%.7f", boundingBox.maxLat)

        // Build the bBox string using the formatted values
        val bBox = "$minLonStr,$minLatStr,$maxLonStr,$maxLatStr"
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&bBox=$bBox&parameterCd=00065&siteStatus=active"

        Log.d("UsgsDataSource", "Requesting URL: $url")

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val timeSeries = response.getJSONObject("value").getJSONArray("timeSeries")
                    val results = mutableListOf<WaterLevelData>()

                    for (i in 0 until timeSeries.length()) {
                        val site = timeSeries.getJSONObject(i)
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
                            results.add(WaterLevelData(siteName, siteLat, siteLon, variableName, value, unit))
                        }
                    }
                    callback.onSuccess(results)
                } catch (e: Exception) {
                    Log.e("UsgsDataSource", "Error parsing USGS response", e)
                    // It's better to call onError for parsing failures
                    callback.onError("Failed to parse USGS data")
                }
            },
            { error ->
                Log.e("UsgsDataSource", "USGS Request Failed: ${error.message}")
                callback.onError(error.message ?: "Unknown USGS error")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }

    // ADDED: The accurate bounding box calculation helper function
    /**
     * Calculates a geographic bounding box from a center point and a radius in miles.
     */
    private fun calculateBoundingBox(centerLat: Double, centerLon: Double, radiusInMiles: Double): BoundingBox {
        // Conversion factors
        val milesPerDegreeLat = 69.0
        val milesPerDegreeLon = cos(Math.toRadians(centerLat)) * milesPerDegreeLat

        // Calculate the change in degrees
        val latDelta = radiusInMiles / milesPerDegreeLat
        val lonDelta = radiusInMiles / milesPerDegreeLon

        // Calculate the min/max coordinates
        val minLat = centerLat - latDelta
        val maxLat = centerLat + latDelta
        val minLon = centerLon - lonDelta
        val maxLon = centerLon + lonDelta

        return BoundingBox(minLat, maxLat, minLon, maxLon)
    }

    /**
     * NEW: Fetches the live, detailed data for a single selected USGS station.
     */
    fun fetchWaterLevelForStation(stationId: String, callback: OpenMeteoRemoteDataSource.ApiCallback<WaterLevelData?>) {
        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&sites=$stationId&parameterCd=00065&siteStatus=active"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
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
                            callback.onSuccess(null) // No current value available
                        }
                    } else {
                        callback.onSuccess(null) // No site found for this ID
                    }
                } catch (e: Exception) {
                    callback.onSuccess(null) // Error parsing data
                }
            },
            { error ->
                callback.onError(error.message ?: "Unknown USGS error")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}