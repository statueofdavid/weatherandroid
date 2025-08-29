package space.declared.weather.data.source

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.suspendCancellableCoroutine
import space.declared.weather.data.MoonData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A data source for fetching moon phase information from the
 * US Naval Observatory (USNO) API.
 */
class UsnoMoonDataSource(context: Context) {

    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val TAG = "UsnoMoonDataSource"

    /**
     * Fetches the moon phase for a specific date and location.
     *
     * @param date The date for which to fetch the moon phase.
     * @param latitude The latitude of the location.
     * @param longitude The longitude of the location.
     * @return A MoonData object or null if the fetch fails.
     */
    suspend fun fetchMoonPhase(date: Date, latitude: Double, longitude: Double): MoonData? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateString = dateFormat.format(date)

        // UPDATED: Added the optional 'id' parameter as recommended by the USNO API docs.
        val url = "https://aa.usno.navy.mil/api/rstt/oneday?date=$dateString&coords=$latitude,$longitude&id=declared-space-weather"
        Log.d(TAG, "Fetching moon phase from URL: $url")

        return suspendCancellableCoroutine { continuation ->
            val request = JsonObjectRequest(Request.Method.GET, url, null,
                { response ->
                    try {
                        // FIX: First, check if the "error" key exists.
                        // A successful response will not have this key.
                        if (response.has("error") && response.getBoolean("error")) {
                            val errorMessage = response.getString("error_message")
                            Log.e(TAG, "USNO API Error: $errorMessage")
                            if (continuation.isActive) continuation.resume(null)
                            return@JsonObjectRequest
                        }

                        val properties = response.getJSONObject("properties").getJSONObject("data")
                        val phase = properties.getString("curphase")
                        val illuminated = properties.getString("fracillum")

                        val moonData = MoonData(phase, illuminated)
                        if (continuation.isActive) continuation.resume(moonData)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing USNO moon phase response", e)
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                { error ->
                    Log.e(TAG, "Failed to fetch USNO moon phase data", error)
                    // Don't crash the whole app if this fails, just return null
                    if (continuation.isActive) continuation.resume(null)
                }
            )
            requestQueue.add(request)
        }
    }
}