package space.declared.weather.data.source

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NominatimRemoteDataSource(private val context: Context) {

    /**
     * Fetches a human-readable context string for a given set of coordinates.
     * @return A quirky context string (e.g., "Near Central Park") or null if failed.
     */
    suspend fun getQuirkyLocationContext(
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        val userAgent = getUserAgent()
        val urlString = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$latitude&lon=$longitude"

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", userAgent)
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                return@withContext parseNominatimResponse(json)
            }
        } catch (e: Exception) {
            Log.e("NominatimDataSource", "Failed to get quirky context", e)
        }
        return@withContext null
    }

    /**
     * Intelligently parses the JSON response from Nominatim to find the
     * most interesting name available.
     */
    private fun parseNominatimResponse(json: JSONObject): String? {
        val address = json.optJSONObject("address") ?: return null
        val quirkyFeature = when {
            address.has("tourism") -> address.getString("tourism")
            address.has("amenity") -> address.getString("amenity")
            address.has("historic") -> address.getString("historic")
            address.has("shop") -> "Near a ${address.getString("shop")}"
            address.has("leisure") -> address.getString("leisure")
            address.has("road") -> "On ${address.getString("road")}"
            address.has("city") -> "In ${address.getString("city")}"
            else -> null
        }
        // Cleans up the text by replacing underscores and capitalizing the first letter.
        return quirkyFeature?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
    }

    /**
     * Creates a user agent string required by Nominatim's usage policy.
     */
    private fun getUserAgent(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = pInfo.versionName
            "MyEnvironmentalApp/$version (${context.packageName})"
        } catch (e: PackageManager.NameNotFoundException) {
            "MyEnvironmentalApp/1.0 (space.declared.weather)"
        }
    }
}