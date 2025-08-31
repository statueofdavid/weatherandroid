package space.declared.weather.ui

import SavedLocationsAdapter
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import space.declared.weather.R

class MainActivity : AppCompatActivity() {
    private val viewModel: WeatherViewModel by viewModels()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var savedLocationsAdapter: SavedLocationsAdapter
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NEW: Apply the saved theme before the content view is set
        applyThemeOnStartup()

        setContentView(R.layout.activity_main)

        // --- Find all the necessary views ---
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navDrawerContainer: FrameLayout = findViewById(R.id.nav_drawer_container)

        setSupportActionBar(toolbar)

        // Setup AppBarConfiguration with the drawer layout
        appBarConfiguration = AppBarConfiguration(navController.graph, drawerLayout)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)

        // --- Call the setup functions ---
        setupBottomNav(bottomNavView)
        setupDrawerAndObserve(navDrawerContainer)
    }

    /**
     * Reads the saved dark_mode preference and applies the corresponding
     * theme to the entire application.
     */
    private fun applyThemeOnStartup() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val darkModeValue = sharedPreferences.getString("dark_mode", "system")
        when (darkModeValue) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun setupBottomNav(bottomNavView: BottomNavigationView) {
        NavigationUI.setupWithNavController(bottomNavView, navController)

        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_saved_locations -> {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                    viewModel.onDetailCardClosed()
                    return@setOnItemSelectedListener false
                }
                else -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    viewModel.onDetailCardClosed()
                    return@setOnItemSelectedListener NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
        }
    }

    private fun setupDrawerAndObserve(navDrawerContainer: FrameLayout) {
        val savedLocationsRecyclerView: RecyclerView = navDrawerContainer.findViewById(R.id.savedLocationsRecyclerView)

        savedLocationsAdapter = SavedLocationsAdapter(
            onLocationClicked = { location ->
                viewModel.onDetailCardClosed()
                viewModel.fetchInitialData(location.latitude, location.longitude, location.name)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onDeleteClicked = { location ->
                viewModel.deleteLocation(location)
            }
        )
        savedLocationsRecyclerView.adapter = savedLocationsAdapter
        savedLocationsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Observe the list of saved locations
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    savedLocationsAdapter.submitList(state.savedLocations)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
