package space.declared.weather.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import space.declared.weather.R

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    // Get a reference to the shared ViewModel to trigger data refreshes
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val contextThemeWrapper: Context = ContextThemeWrapper(requireActivity(), R.style.AppPreferenceTheme)
        val localInflater = inflater.cloneInContext(contextThemeWrapper)
        val view = super.onCreateView(localInflater, container, savedInstanceState)
        view.setBackgroundResource(R.drawable.gradient_background)
        return view
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        updatePreferenceSummary("units")
        updatePreferenceSummary("radius")
        updatePreferenceSummary("dark_mode")

        val updateStationsPreference = findPreference<Preference>("update_stations")
        updateStationsPreference?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), "Station list update coming soon!", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "units", "radius" -> {
                updatePreferenceSummary(key)
                // Tell the ViewModel to re-fetch data with the new settings
                viewModel.refreshDataForCurrentLocation()
            }
            "dark_mode" -> {
                updatePreferenceSummary(key)
                val darkModeValue = sharedPreferences?.getString(key, "system")
                applyDarkMode(darkModeValue)
            }
        }
    }

    private fun updatePreferenceSummary(key: String?) {
        val preference = findPreference<Preference>(key ?: return)
        if (preference is ListPreference) {
            preference.summary = preference.entry
        }
    }

    private fun applyDarkMode(value: String?) {
        when (value) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}