package space.declared.weather.data.source

import android.content.Context
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import space.declared.weather.data.WaterLevelData

/**
 * Handles network operations to fetch water level data from the USGS API.
 */
class UsgsRemoteDataSource(context: Context) {

    private val requestQueue = Volley.newRequestQueue(context.applicationContext)

    /**
     * Fetches the latest water level reading for all monitoring sites within a given radius.
     */
    fun fetchWaterLevels(latitude: Double, longitude: Double, radius: Double, callback: OpenMeteoRemoteDataSource.ApiCallback<List<WaterLevelData>>) {
        // The USGS API can find the nearest sites using a bounding box.
        // We'll create a box around the user's location based on the radius.
        val latMinus = latitude - radius
        val latPlus = latitude + radius
        val lonMinus = longitude - radius
        val lonPlus = longitude + radius
        val bBox = "$lonMinus,$latMinus,$lonPlus,$latPlus"

        val url = "https://waterservices.usgs.gov/nwis/iv/?format=json&bBox=$bBox&parameterCd=00065&siteStatus=active"

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
                    callback.onSuccess(emptyList())
                }
            },
            { error ->
                callback.onError(error.message ?: "Unknown USGS error")
            }
        )
        requestQueue.add(jsonObjectRequest)
    }
}