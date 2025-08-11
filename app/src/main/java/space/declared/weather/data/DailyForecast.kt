package space.declared.weather.data

import androidx.annotation.Keep

/**
 * Represents the data for a single day in the 10-day forecast.
 */
@Keep
data class DailyForecast(
    val date: String,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double
)
