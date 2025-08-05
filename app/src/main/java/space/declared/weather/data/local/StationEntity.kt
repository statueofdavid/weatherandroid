package space.declared.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single station (either NOAA or USGS) in our local Room database.
 */
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val type: String // "NOAA" or "USGS"
)