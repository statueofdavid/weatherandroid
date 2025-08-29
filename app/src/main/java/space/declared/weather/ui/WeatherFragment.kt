package space.declared.weather.ui

import SavedLocationsAdapter
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import space.declared.weather.R
import space.declared.weather.data.DailyForecast
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class WeatherFragment : Fragment() {

    private val viewModel: WeatherViewModel by activityViewModels()

    // --- UI Elements ---
    private lateinit var progressBar: ProgressBar
    private lateinit var weatherDataContainer: LinearLayout
    private lateinit var citySearch: EditText
    private lateinit var locationButton: Button
    private lateinit var forecastTabs: TabLayout
    private lateinit var cityName: TextView
    private lateinit var temperature: TextView
    private lateinit var weatherDescription: TextView
    private lateinit var uvIndex: TextView
    private lateinit var sunriseTime: TextView
    private lateinit var sunsetTime: TextView
    private lateinit var moonPhase: TextView
    private lateinit var sunIcon: ImageView
    private lateinit var pressure: TextView
    private lateinit var humidity: TextView
    private lateinit var precipitation: TextView
    private lateinit var cloudCover: TextView
    private lateinit var wind: TextView
    private lateinit var windGusts: TextView
    private lateinit var citySearchRecyclerView: RecyclerView
    private lateinit var citySearchAdapter: CitySearchAdapter
    private lateinit var citySearchCardContainer: CardView
    private lateinit var dailyDetailsContainer: LinearLayout

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private lateinit var locationPermissionRequest:
            androidx.activity.result.ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> getCurrentLocationWeather()
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> getCurrentLocationWeather()
                else -> Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_weather, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        setupSearch()
        setupObservers()

        if (viewModel.uiState.value.cityName == null && !viewModel.uiState.value.isLoading) {
            getCurrentLocationWeather()
        }
    }

    private fun initializeViews(view: View) {
        progressBar = view.findViewById(R.id.progressBar)
        weatherDataContainer = view.findViewById(R.id.weatherDataContainer)
        dailyDetailsContainer = view.findViewById(R.id.dailyDetailsContainer)
        citySearch = view.findViewById(R.id.citySearch)
        citySearchRecyclerView = view.findViewById(R.id.citySearchRecyclerView)
        citySearchCardContainer = view.findViewById(R.id.citySearchCardContainer)
        locationButton = view.findViewById(R.id.locationButton)
        forecastTabs = view.findViewById(R.id.forecastTabs)
        cityName = view.findViewById(R.id.cityName)
        temperature = view.findViewById(R.id.temperature)
        weatherDescription = view.findViewById(R.id.weatherDescription)
        uvIndex = view.findViewById(R.id.uvIndex)
        sunriseTime = view.findViewById(R.id.sunriseTime)
        sunsetTime = view.findViewById(R.id.sunsetTime)
        moonPhase = view.findViewById(R.id.moonPhase)
        sunIcon = view.findViewById(R.id.sunIcon)
        pressure = view.findViewById(R.id.pressure)
        humidity = view.findViewById(R.id.humidity)
        precipitation = view.findViewById(R.id.precipitation)
        cloudCover = view.findViewById(R.id.cloudCover)
        wind = view.findViewById(R.id.wind)
        windGusts = view.findViewById(R.id.windGusts)
    }

    private fun setupClickListeners() {
        locationButton.setOnClickListener { getCurrentLocationWeather() }
    }

    private fun setupSearch() {
        citySearchAdapter = CitySearchAdapter { city ->
            val displayName = if (!city.state.isNullOrEmpty()) "${city.name}, ${city.state}" else city.name
            viewModel.fetchInitialData(city.latitude, city.longitude, displayName)

            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
            citySearch.clearFocus()
            citySearch.text.clear()
            viewModel.onCitySelectionDialogShown()
        }
        citySearchRecyclerView.adapter = citySearchAdapter

        citySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
            }
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length > 2) {
                    searchRunnable = Runnable { viewModel.onCitySearch(query) }
                    searchHandler.postDelayed(searchRunnable!!, 500L)
                } else {
                    viewModel.onCitySelectionDialogShown()
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    weatherDataContainer.visibility = if (state.isLoading || state.error != null) View.GONE else View.VISIBLE

                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    citySearchAdapter.submitList(state.cityList)
                    citySearchCardContainer.visibility = if (state.cityList.isNotEmpty()) View.VISIBLE else View.GONE

                    state.cityName?.let { cityName.text = it }
                    if (state.fullForecast.isNotEmpty()) {
                        setupForecastTabs(state.fullForecast)
                    }

                    // Update the sun icon's position
                    if (state.selectedDayWeather != null) {
                        updateSunIconPosition(state.sunPositionBias)
                        sunIcon.visibility = View.VISIBLE
                    } else {
                        sunIcon.visibility = View.INVISIBLE
                    }


                    state.selectedDayWeather?.let { details ->
                        val animation = AnimationUtils.loadAnimation(requireContext(), R.anim.item_animation_fall_down)
                        dailyDetailsContainer.startAnimation(animation)

                        temperature.text = "${details.tempMax} / ${details.tempMin}"
                        weatherDescription.text = details.weatherDescription

                        uvIndex.text = details.uvIndex
                        sunriseTime.text = details.sunrise.split(": ").lastOrNull() ?: ""
                        sunsetTime.text = details.sunset.split(": ").lastOrNull() ?: ""
                        // moonPhase.text = "Moon: " + details.moonPhase // We will add this later

                        pressure.text = details.pressure ?: "Pressure: N/A"
                        humidity.text = details.humidity ?: "Humidity: N/A"
                        precipitation.text = details.precipitationChance ?: "Precipitation Chance: N/A"
                        cloudCover.text = details.cloudCover ?: "Cloud Cover: N/A"
                        wind.text = details.wind ?: "Wind: N/A"
                        windGusts.text = details.windGusts ?: "Gusts: N/A"
                    }
                }
            }
        }
    }

    /**
     * Calculates the X and Y position of the sun icon on the arc
     * using trigonometry to create a smooth, circular path.
     */
    private fun updateSunIconPosition(progress: Float) {
        // Convert the linear progress (0.0=sunrise, 1.0=sunset) into an angle in radians.
        // The angle goes from PI (180 deg) at sunrise, to PI/2 (90 deg) at noon,
        // to 0 at sunset, and continues into negative values for the night path.
        val angle = Math.PI * (1 - progress)

        // Calculate the horizontal and vertical positions using cos and sin.
        val horizontalBias = ( (cos(angle) + 1) / 2 ).toFloat()
        val verticalBias = ( (1 - sin(angle)) / 2 ).toFloat()

        // Apply the new biases to the sun icon's layout parameters.
        val params = sunIcon.layoutParams as ConstraintLayout.LayoutParams
        params.horizontalBias = horizontalBias
        params.verticalBias = verticalBias
        sunIcon.layoutParams = params
    }

    private fun setupForecastTabs(forecast: List<DailyForecast>) {
        if (forecastTabs.tabCount == forecast.size) return

        forecastTabs.removeAllTabs()
        forecast.forEachIndexed { index, day ->
            val tab = forecastTabs.newTab().setText(formatDayForTab(day.date, index))
            forecastTabs.addTab(tab)
        }
        forecastTabs.clearOnTabSelectedListeners()
        forecastTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { viewModel.onDaySelected(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun getCurrentLocationWeather() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.fetchInitialData(location.latitude, location.longitude, "Current Location")
                } else {
                    Toast.makeText(requireContext(), "Could not retrieve location. Is GPS on?", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to get location.", Toast.LENGTH_SHORT).show()
            }
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun formatDayForTab(dateString: String, index: Int): String {
        if (index == 0) return "Today"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date!!)
        } catch (e: Exception) { "N/A" }
    }
}