package space.declared.weather.ui

import SavedLocationsAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. We can now send notifications.
        } else {
            // Permission denied. Tell the user why this matters.
            Toast.makeText(this, "Notifications will be disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyThemeOnStartup()

        // --- Find all views and assign them to class properties ---
        drawerLayout = findViewById(R.id.drawer_layout)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navDrawerContainer: FrameLayout = findViewById(R.id.nav_drawer_container)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // --- Set up the UI components by calling helper functions ---
        setSupportActionBar(toolbar)
        setupBottomNav(bottomNavView)

        askForNotificationPermission()
        // This combined function ensures the adapter is created before it's used.
        setupDrawerAndObserve(navDrawerContainer)

        val drawerCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, drawerCallback)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerCallback.isEnabled = true
            }
            override fun onDrawerClosed(drawerView: View) {
                drawerCallback.isEnabled = false
            }
        })
    }

    private fun setupBottomNav(bottomNavView: BottomNavigationView) {
        // This connects your Weather, Water, and Settings fragments to the bottom nav
        NavigationUI.setupWithNavController(bottomNavView, navController)

        // This adds the custom behavior for the "Saved Locations" button
        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_saved_locations -> {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        drawerLayout.openDrawer(GravityCompat.START)
                    }
                    // Return false so the item doesn't stay selected
                    return@setOnItemSelectedListener false
                }
                else -> {
                    // FIX: Close the details card when navigating away
                    viewModel.onDetailCardClosed()
                    // For all other items, close the drawer and navigate
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return@setOnItemSelectedListener NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
        }
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

    /**
     * Sets up the drawer's RecyclerView and adapter, and then immediately
     * starts observing the data. This guarantees the adapter is initialized
     * before it's ever used, fixing the race condition.
     */
    private fun setupDrawerAndObserve(navDrawerContainer: FrameLayout) {
        // Find the RecyclerView inside the drawer container
        val savedLocationsRecyclerView: RecyclerView = navDrawerContainer.findViewById(R.id.savedLocationsRecyclerView)

        // Create and configure the adapter that handles clicks
        savedLocationsAdapter = SavedLocationsAdapter(
            onSwitchToggled = { location, isEnabled ->
                viewModel.toggleHazardAlertsForLocation(location, isEnabled)
            },
            onLocationClicked = { location ->
                // Close the details card to prevent showing stale data.
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

        // Observe the list of saved locations from the ViewModel's StateFlow
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    savedLocationsAdapter.submitList(state.savedLocations)
                }
            }
        }
    }

    private fun askForNotificationPermission() {
        // This is only required for Android 13 (API 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Launch the permission dialog
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}