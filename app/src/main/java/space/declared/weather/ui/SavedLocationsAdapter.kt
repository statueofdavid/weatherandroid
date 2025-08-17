import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import space.declared.weather.R
import space.declared.weather.ui.SavedLocation

class SavedLocationsAdapter(
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
        holder.bind(location)
        holder.itemView.setOnClickListener { onLocationClicked(location) }
        holder.deleteButton.setOnClickListener { onDeleteClicked(location) }
    }

    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.locationNameTextView)
        val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        fun bind(location: SavedLocation) {
            // Display the location's name
            nameTextView.text = location.name
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