package space.declared.weather.data

/**
 * Represents the water level data from a USGS monitoring site, now including location.
 */
data class WaterLevelData(
    val siteName: String,
    val latitude: Double,
    val longitude: Double,
    val variableName: String, // e.g., "Gage height"
    val value: String,
    val unit: String
)
