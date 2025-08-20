import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import space.declared.weather.R
import space.declared.weather.ui.StationListItem

// --- RecyclerView Adapter for the list of water stations ---
class StationListAdapter(private val onStationClicked: (StationListItem) -> Unit) :
    ListAdapter<StationListItem, StationListAdapter.StationViewHolder>(StationDiffCallback()) {
    private var unitSystem: String = "imperial"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.station_list_item, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station, unitSystem)
        holder.itemView.setOnClickListener { onStationClicked(station) }
    }

    fun setUnitSystem(units: String) {
        if (unitSystem != units) {
            unitSystem = units
            notifyDataSetChanged() // Redraw the list with the new units
        }
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        private val distanceTextView: TextView = itemView.findViewById(R.id.stationDistanceTextView)

        fun bind(stationItem: StationListItem, unitSystem: String) {
            nameTextView.text = stationItem.entity.name

            val distanceText = if (unitSystem == "metric") {
                // Convert meters to kilometers
                val distanceInKm = stationItem.distanceInMeters / 1000f
                String.format("%.1f km", distanceInKm)
            } else {
                // Convert meters to miles
                val distanceInMiles = stationItem.distanceInMeters * 0.000621371f
                String.format("%.1f miles", distanceInMiles)
            }
            distanceTextView.text = distanceText
        }
    }
}

// --- DiffUtil for efficient list updates ---
class StationDiffCallback : DiffUtil.ItemCallback<StationListItem>() {
    override fun areItemsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        return oldItem.entity.id == newItem.entity.id
    }

    override fun areContentsTheSame(oldItem: StationListItem, newItem: StationListItem): Boolean {
        return oldItem == newItem
    }
}