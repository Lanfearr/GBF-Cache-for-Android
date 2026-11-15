package com.sagisawa.gbfaccelerator.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AppPreferences {
    private const val PREFS_NAME = "gbf_accelerator_prefs"
    private const val KEY_ENABLE_RAM_CACHE = "enable_ram_cache"
    private const val KEY_ENABLE_PREFETCH = "enable_prefetch"
    private const val KEY_SELECTED_BROWSER_PACKAGES = "selected_browser_packages"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isRamCacheEnabled(context: Context): Boolean {
        // Mobile devices default to false (OFF) to conserve limited RAM
        return getPrefs(context).getBoolean(KEY_ENABLE_RAM_CACHE, false)
    }

    fun setRamCacheEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_ENABLE_RAM_CACHE, enabled) }
    }

    fun isPrefetchEnabled(context: Context): Boolean {
        // Mobile devices default to false (OFF) to prevent network/rendering stutter
        return getPrefs(context).getBoolean(KEY_ENABLE_PREFETCH, false)
    }

    fun setPrefetchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_ENABLE_PREFETCH, enabled) }
    }

    fun getSelectedBrowserPackages(context: Context): Set<String>? {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_SELECTED_BROWSER_PACKAGES)) return null
        return prefs.getStringSet(KEY_SELECTED_BROWSER_PACKAGES, emptySet())?.toSet() ?: emptySet()
    }

    fun setSelectedBrowserPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit { putStringSet(KEY_SELECTED_BROWSER_PACKAGES, packages.toSet()) }
    }
}
