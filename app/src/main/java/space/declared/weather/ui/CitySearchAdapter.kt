package space.declared.weather.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import space.declared.weather.R
import space.declared.weather.data.CityResult

class CitySearchAdapter(
    private val onCityClicked: (CityResult) -> Unit
) : RecyclerView.Adapter<CitySearchAdapter.CityViewHolder>() {

    private var cities: List<CityResult> = emptyList()

    class CityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.cityNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.city_list_item, parent, false)
        return CityViewHolder(view)
    }

    override fun onBindViewHolder(holder: CityViewHolder, position: Int) {
        val city = cities[position]
        val displayName = listOfNotNull(city.name, city.state, city.country)
            .joinToString(", ")

        holder.nameTextView.text = displayName
        holder.itemView.setOnClickListener {
            onCityClicked(city)
        }
    }

    override fun getItemCount() = cities.size

    fun submitList(newCities: List<CityResult>) {
        cities = newCities
        notifyDataSetChanged()
    }
}