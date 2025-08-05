package space.declared.weather.ui

import space.declared.weather.data.local.StationEntity

/**
 * A UI model representing a single station in the Water Levels list.
 * This holds all the information needed for the list display.
 */
data class StationListItem(
    val entity: StationEntity,
    val distance: Float // in miles
)
