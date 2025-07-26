package space.declared.weather.data

import space.declared.weather.data.TideData
import space.declared.weather.data.WaterLevelData

/**
 * Represents the combined data for the water level feature.
 * It can hold either tide data or river data, making it flexible for the UI.
 */
data class WaterData(
    val tideData: List<TideData>? = null,
    val waterLevel: List<WaterLevelData>? = null
)