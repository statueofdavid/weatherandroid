package space.declared.weather.data

import androidx.annotation.Keep

/**
 * A simple data class to hold the parsed moon phase information
 * from the US Naval Observatory API.
 *
 * @param phase The descriptive name of the moon's current phase (e.g., "Waxing Crescent").
 * @param illumination The percentage of the moon that is illuminated.
 */
@Keep
data class MoonData(
    val phase: String,
    val illumination: String
)