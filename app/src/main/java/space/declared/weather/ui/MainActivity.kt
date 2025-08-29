package space.declared.weather.ui

import SavedLocationsAdapter
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- 1. Find all views and assign them to class properties ---
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        val navDrawerContainer: FrameLayout = findViewById(R.id.nav_drawer_container)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // --- 2. Set up the UI components by calling helper functions ---
        setSupportActionBar(toolbar)
        setupBottomNav(bottomNavView)
        setupDrawerAndObserve(navDrawerContainer)
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
                    return@setOnItemSelectedListener false
                }
                else -> {
                    viewModel.onDetailCardClosed()
                    drawerLayout.closeDrawer(GravityCompat.START)
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