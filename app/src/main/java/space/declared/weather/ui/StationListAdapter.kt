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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.station_list_item, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station)
        holder.itemView.setOnClickListener { onStationClicked(station) }
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        private val distanceTextView: TextView = itemView.findViewById(R.id.stationDistanceTextView)

        fun bind(stationItem: StationListItem) {
            nameTextView.text = stationItem.entity.name
            distanceTextView.text = String.format("%.1f miles", stationItem.distance)
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