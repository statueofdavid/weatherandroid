package space.declared.weather.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the saved_locations table.
 * This interface defines all the database operations for SavedLocationEntity.
 */
@Dao
interface SavedLocationDao {

    /**
     * Inserts a new location into the database. If a location with the same
     * primary key already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocationEntity)

    /**
     * Deletes a specific location from the database.
     */
    @Delete
    suspend fun delete(location: SavedLocationEntity)

    /**
     * Retrieves all saved locations from the database, ordered by their ID.
     * By returning a Flow, the UI can observe this function and will automatically
     * update whenever the data in the table changes.
     */
    @Query("SELECT * FROM saved_locations ORDER BY id ASC")
    fun getAllLocations(): Flow<List<SavedLocationEntity>>

    /**
     * Deletes a location based on its name and coordinates.
     * This is useful for removing a location without needing its specific ID.
     */
    @Query("DELETE FROM saved_locations WHERE name = :name AND latitude = :latitude AND longitude = :longitude")
    suspend fun deleteByCoordinates(name: String, latitude: Double, longitude: Double)


    @Query("SELECT * FROM saved_locations WHERE name = :name LIMIT 1")
    suspend fun getLocationByName(name: String): SavedLocationEntity?

    /**
     * Retrieves all locations where the user has enabled notifications.
     * The `WHERE notificationsEnabled = 1` clause filters for rows where the flag is true.
     */
    @Query("SELECT * FROM saved_locations WHERE notificationsEnabled = 1")
    suspend fun getLocationsWithNotificationsEnabled(): List<SavedLocationEntity>

    /**
     * Updates an existing location. This will be used by the UI to toggle
     * the notification setting on or off.
     */
    @Update
    suspend fun update(location: SavedLocationEntity)

    @Query("SELECT COUNT(*) FROM saved_locations WHERE notificationsEnabled = 1")
    suspend fun getEnabledAlertsCount(): Int

}
