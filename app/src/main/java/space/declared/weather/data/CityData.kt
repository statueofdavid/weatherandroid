package space.declared.weather.data

import org.json.JSONObject

/**
 * Represents the data for a single city returned from the geocoding API.
 */
data class CityResult(
    val name: String,
    val state: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        /**
         * A factory function to create a CityResult object from a JSONObject.
         */
        fun fromJson(json: JSONObject): CityResult {
            return CityResult(
                name = json.getString("name"),
                state = json.optString("admin1", null), // admin1 is often the state/province
                country = json.getString("country"),
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude")
            )
        }
    }
}