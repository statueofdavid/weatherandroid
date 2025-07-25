package space.declared.weather.data

import space.declared.weather.data.source.TidePrediction
import space.declared.weather.data.source.WaterLevelData

/**
 * Represents the combined data for the water level feature.
 * It can hold either tide data or river data, making it flexible for the UI.
 */
data class WaterData(
    val isTideData: Boolean,
    val tidePredictions: List<TidePrediction>? = null,
    val waterLevel: WaterLevelData? = null
)