package com.rotmeter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rotmeter.app.databinding.ActivityMainBinding
import com.rotmeter.app.ui.AlertsFragment
import com.rotmeter.app.ui.HomeFragment
import com.rotmeter.app.ui.LimitsFragment
import com.rotmeter.app.ui.StatsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var selectedTab = R.id.nav_home
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = ContextCompat.getColor(this, R.color.rot_background)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(background, background),
            navigationBarStyle = SystemBarStyle.auto(background, background)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        selectedTab = savedInstanceState?.getInt(SELECTED_TAB, R.id.nav_home) ?: R.id.nav_home
        binding.bottomNavigation.selectedItemId = selectedTab
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (supportFragmentManager.isStateSaved) return@setOnItemSelectedListener false
            if (selectedTab != item.itemId) {
                showTab(item.itemId)
                selectedTab = item.itemId
            }
            true
        }
        val restored = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (restored == null || restored.tag != selectedTab.toString()) {
            showTab(selectedTab)
        }
        requestNotificationPermissionOnce()
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val preferences = getSharedPreferences("rotmeter_permissions", MODE_PRIVATE)
        if (!preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) {
            preferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showTab(tab: Int) {
        val fragment = when (tab) {
            R.id.nav_stats -> StatsFragment()
            R.id.nav_limits -> LimitsFragment()
            R.id.nav_alerts -> AlertsFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.fragment_container, fragment, tab.toString())
            .commitNow()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val SELECTED_TAB = "selected_tab"
        private const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
