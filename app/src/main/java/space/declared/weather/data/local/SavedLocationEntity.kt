package space.declared.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single saved location in the user's list.
 * This will be the structure for our 'saved_locations' table in the Room database.
 *
 * @param id A unique identifier for the location.
 * @param name The display name of the location (e.g., "New York, NY" or "Current Location").
 * @param latitude The geographical latitude.
 * @param longitude The geographical longitude.
 * @param isCurrentLocation A flag to distinguish the special "Current Location" entry.
 */
@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isCurrentLocation: Boolean = false
)