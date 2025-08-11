package space.declared.weather.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import space.declared.weather.R

class SettingsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var sharedPreferences: SharedPreferences

    // UI Views
    private lateinit var syncStatusContainer: LinearLayout
    private lateinit var unitsSetting: LinearLayout
    private lateinit var unitsSummary: TextView
    private lateinit var radiusSetting: LinearLayout
    private lateinit var radiusSummary: TextView
    private lateinit var darkModeSetting: LinearLayout
    private lateinit var darkModeSummary: TextView
    private lateinit var syncNowButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the custom layout from the Canvas
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        initializeViews(view)
        setupClickListeners()
        setupObservers()
        updateAllSummaries()
    }

    private fun initializeViews(view: View) {
        syncStatusContainer = view.findViewById(R.id.syncStatusContainer)
        unitsSetting = view.findViewById(R.id.unitsSetting)
        unitsSummary = view.findViewById(R.id.unitsSummary)
        radiusSetting = view.findViewById(R.id.radiusSetting)
        radiusSummary = view.findViewById(R.id.radiusSummary)
        darkModeSetting = view.findViewById(R.id.darkModeSetting)
        darkModeSummary = view.findViewById(R.id.darkModeSummary)
        syncNowButton = view.findViewById(R.id.syncNowButton)
    }

    private fun setupClickListeners() {
        unitsSetting.setOnClickListener { showUnitsDialog() }
        radiusSetting.setOnClickListener { showRadiusDialog() }
        darkModeSetting.setOnClickListener { showDarkModeDialog() }
        syncNowButton.setOnClickListener {
            viewModel.triggerStationSync()
            Toast.makeText(requireContext(), "Station data sync started.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    syncStatusContainer.visibility = if (state.isSyncing) View.VISIBLE else View.GONE
                    syncNowButton.isEnabled = !state.isSyncing
                }
            }
        }
    }

    private fun updateAllSummaries() {
        updateUnitsSummary()
        updateRadiusSummary()
        updateDarkModeSummary()
    }

    private fun updateUnitsSummary() {
        val unitsValue = sharedPreferences.getString("units", "metric")
        val entries = resources.getStringArray(R.array.unit_entries)
        val values = resources.getStringArray(R.array.unit_values)
        val index = values.indexOf(unitsValue)
        unitsSummary.text = if (index != -1) entries[index] else "Metric (°C, km/h)"
    }

    private fun updateRadiusSummary() {
        val radiusValue = sharedPreferences.getString("radius", "1.0")
        val entries = resources.getStringArray(R.array.radius_entries)
        val values = resources.getStringArray(R.array.radius_values)
        val index = values.indexOf(radiusValue)
        radiusSummary.text = if (index != -1) entries[index] else "10 miles"
    }

    private fun updateDarkModeSummary() {
        val darkModeValue = sharedPreferences.getString("dark_mode", "system")
        val entries = resources.getStringArray(R.array.dark_mode_entries)
        val values = resources.getStringArray(R.array.dark_mode_values)
        val index = values.indexOf(darkModeValue)
        darkModeSummary.text = if (index != -1) entries[index] else "System Default"
    }

    private fun showUnitsDialog() {
        val values = resources.getStringArray(R.array.unit_values)
        val currentValue = sharedPreferences.getString("units", "metric")
        val checkedItem = values.indexOf(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle("Unit System")
            .setSingleChoiceItems(R.array.unit_entries, checkedItem) { dialog, which ->
                sharedPreferences.edit().putString("units", values[which]).apply()
                updateUnitsSummary()
                viewModel.refreshDataForCurrentLocation()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRadiusDialog() {
        val values = resources.getStringArray(R.array.radius_values)
        val currentValue = sharedPreferences.getString("radius", "1.0")
        val checkedItem = values.indexOf(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle("Search Radius")
            .setSingleChoiceItems(R.array.radius_entries, checkedItem) { dialog, which ->
                sharedPreferences.edit().putString("radius", values[which]).apply()
                updateRadiusSummary()
                viewModel.refreshDataForCurrentLocation()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDarkModeDialog() {
        val values = resources.getStringArray(R.array.dark_mode_values)
        val currentValue = sharedPreferences.getString("dark_mode", "system")
        val checkedItem = values.indexOf(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle("Dark Mode")
            .setSingleChoiceItems(R.array.dark_mode_entries, checkedItem) { dialog, which ->
                val selectedValue = values[which]
                sharedPreferences.edit().putString("dark_mode", selectedValue).apply()
                updateDarkModeSummary()
                applyDarkMode(selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyDarkMode(value: String?) {
        when (value) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}