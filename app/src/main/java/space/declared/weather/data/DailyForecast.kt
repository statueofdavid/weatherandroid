package space.declared.weather.data

/**
 * Represents the data for a single day in the 10-day forecast.
 */
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double
)
