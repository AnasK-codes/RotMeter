package com.rotmeter.app.blocking

import android.content.Context
import android.content.SharedPreferences

object BlockingPreferences {
    private const val FILE = "rotmeter_blocking"
    private const val ENABLED = "enabled"
    private const val SELECTED = "selected_packages"

    fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun isSelected(context: Context, packageName: String): Boolean =
        preferences(context).getStringSet(SELECTED, emptySet())?.contains(packageName) == true

    fun setSelected(context: Context, packageName: String, selected: Boolean) {
        // Never mutate SharedPreferences' returned set.
        val packages = preferences(context).getStringSet(SELECTED, emptySet()).orEmpty().toMutableSet()
        if (selected) packages.add(packageName) else packages.remove(packageName)
        preferences(context).edit().putStringSet(SELECTED, packages).apply()
    }

    fun shouldMonitor(context: Context, packageName: String): Boolean =
        packageName != context.packageName && isEnabled(context) && isSelected(context, packageName)
}
