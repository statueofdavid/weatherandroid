import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import space.declared.weather.R
import space.declared.weather.ui.SavedLocation
import java.util.Locale

class SavedLocationsAdapter(
    private val onSwitchToggled: (SavedLocation, Boolean) -> Unit,
    private val onLocationClicked: (SavedLocation) -> Unit,
    private val onDeleteClicked: (SavedLocation) -> Unit
) : ListAdapter<SavedLocation, SavedLocationsAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.saved_location_item, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val location = getItem(position)
        holder.bind(location, onSwitchToggled, onLocationClicked, onDeleteClicked)
        holder.itemView.setOnClickListener { onLocationClicked(location) }
        holder.deleteButton.setOnClickListener { onDeleteClicked(location) }
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contextTextView: TextView = itemView.findViewById(R.id.locationContextTextView)
        private val hazardStatusTextView: TextView = itemView.findViewById(R.id.hazardStatusTextView)
        private val lastCheckedTextView: TextView = itemView.findViewById(R.id.lastCheckedTextView)

        private val latTextView: TextView = itemView.findViewById(R.id.locationLatTextView)
        private val lonTextView: TextView = itemView.findViewById(R.id.locationLonTextView)
        private val nameTextView: TextView = itemView.findViewById(R.id.locationNameTextView)

        private val notificationSwitch: SwitchMaterial = itemView.findViewById(R.id.notificationSwitch)
        private val statusContainer: LinearLayout = itemView.findViewById(R.id.statusContainer)

        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        fun bind(
            location: SavedLocation,
            onSwitchToggled: (SavedLocation, Boolean) -> Unit,
            onLocationClicked: (SavedLocation) -> Unit,
            onDeleteClicked: (SavedLocation) -> Unit
        ) {
            // --- 1. Set all your existing data and listeners ---
            nameTextView.text = location.name
            latTextView.text = String.format(Locale.US, "Lat: %.4f", location.latitude)
            lonTextView.text = String.format(Locale.US, "Lon: %.4f", location.longitude)

            itemView.setOnClickListener { onLocationClicked(location) }
            deleteButton.setOnClickListener { onDeleteClicked(location) }

            notificationSwitch.setOnCheckedChangeListener(null)
            notificationSwitch.isChecked = location.notificationsEnabled
            notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
                onSwitchToggled(location, isChecked)
            }

            // --- 2. Show the hyperlocal context (if it exists) ---
            if (location.context != null) {
                contextTextView.text = location.context
                contextTextView.visibility = View.VISIBLE
            } else {
                contextTextView.visibility = View.GONE
            }

            // --- 3. Show/Hide and populate the hazard status container ---
            if (location.notificationsEnabled) {
                statusContainer.visibility = View.VISIBLE

                // Set the hazard level text
                hazardStatusTextView.text =
                    "${location.capeLevel ?: "-"} CAPE \n${location.lightningRisk ?: "-"} Lightning"

                // Set the last checked time text
                if (location.lastHazardCheck != null) {
                    // This creates a user-friendly string like "Checked: 5 minutes ago"
                    lastCheckedTextView.text =
                        "Checked: ${formatToShortRelativeTime(location.lastHazardCheck)}"
                } else {
                    lastCheckedTextView.text = "Check pending..."
                }

            } else {
                // Hide the container if notifications are off
                statusContainer.visibility = View.GONE
            }
        }

        // a helper to make format more compact due to limited space
        private fun formatToShortRelativeTime(timestamp: Long?): String {
            if (timestamp == null) return "never"

            val now = System.currentTimeMillis()
            val diff = now - timestamp

            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                days > 0 -> "${days}d ago"
                hours > 0 -> "${hours}h ago"
                minutes > 0 -> "${minutes}m ago"
                else -> "${seconds}s ago"
            }
        }
    }
}

class LocationDiffCallback : DiffUtil.ItemCallback<SavedLocation>() {
    override fun areItemsTheSame(oldItem: SavedLocation, newItem: SavedLocation): Boolean {
        // Items are the same if they have the same unique ID
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: SavedLocation, newItem: SavedLocation): Boolean {
        // Contents are the same if all fields are identical
        return oldItem == newItem
    }
}