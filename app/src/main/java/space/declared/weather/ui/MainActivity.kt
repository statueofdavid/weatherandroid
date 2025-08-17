package space.declared.weather.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import space.declared.weather.R

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find views and assign them to class properties
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val bottomNavView: BottomNavigationView = findViewById(R.id.bottom_nav_view)

        // Assign NavController after finding the fragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Set up AppBarConfiguration using both NavController and DrawerLayout
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(bottomNavView, navController)

        // Bottom Navigation to open the drawer
        bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_saved_locations -> {
                    drawerLayout.openDrawer(GravityCompat.START)
                    false
                }
                else -> {
                    drawerLayout.closeDrawers()
                    NavigationUI.onNavDestinationSelected(item, navController)
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
