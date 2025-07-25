package space.declared.weather.data.source

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

/**
 * Represents the water level data from a USGS monitoring site.
 */
data class WaterLevelData(
    val siteName: String,
    val variableName: String, // e.g., "Gage height"
    val value: String,
    val unit: String
)

/**
 * Handles network operations to fetch water level data from the USGS API.
 */
class UsgsRemoteDataSource(context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    /**
     * Fetches the latest water level reading for the nearest monitoring site.
     */
    fun fetchWaterLevel(latitude: Double, longitude: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<WaterLevelData?>) {
        // The USGS API can find the nearest site using a bounding box.
        // We'll create a small box around the user's location.
        val latMinus = latitude - 0.5
        val latPlus = latitude + 0.5
        val lonMinus = longitude - 0.5
        val lonPlus = longitude + 0.5
        val bBox = "$lonMinus,$latMinus,$lonPlus,$latPlus"

        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&bBox=$bBox&parameterCd=00065&siteStatus=active"

        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                try {
                    val timeSeries = response.getJSONObject("value").getJSONArray("timeSeries")
                    if (timeSeries.length() > 0) {
                        // Get the first and likely closest site
                        val site = timeSeries.getJSONObject(0)
                        val siteName = site.getJSONObject("sourceInfo").getString("siteName")
                        val variable = site.getJSONObject("variable")
                        val variableName = variable.getString("variableName")
                        val unit = variable.getJSONObject("unit").getString("unitCode")
                        val value = site.getJSONArray("values").getJSONObject(0).getJSONArray("value").getJSONObject(0).getString("value")

                        val waterLevelData = WaterLevelData(siteName, variableName, value, unit)
                        callback.onSuccess(waterLevelData)
                    } else {
                        callback.onSuccess(null) // No sites found in the area
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