package space.declared.weather.data

/**
 * Represents a single tide prediction (high or low tide).
 */
data class TidePrediction(
    val time: String,
    val type: String, // "H" for high, "L" for low
    val height: String
)

/**
 * A container for the results of a tide data fetch, including the station name and location.
 */
data class TideData(
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val predictions: List<TidePrediction>
)
