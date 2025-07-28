package com.shieldtechub.shieldkidsparents.features.appblocker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class BlockedAppsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var listView: ListView
    private val PREFS_NAME = "blocked_apps_prefs"
    private val BLOCKED_APPS_KEY = "blocked_apps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_blocked_apps) // Add layout later
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        listView = ListView(this)
        setContentView(listView)
        updateList()
    }

    private fun updateList() {
        val blockedApps = getBlockedApps()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, blockedApps)
        listView.adapter = adapter
    }

    private fun getBlockedApps(): List<String> {
        return prefs.getStringSet(BLOCKED_APPS_KEY, emptySet())?.toList() ?: emptyList()
    }

    private fun addBlockedApp(appOrWebsite: String) {
        val current = prefs.getStringSet(BLOCKED_APPS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(appOrWebsite)
        prefs.edit().putStringSet(BLOCKED_APPS_KEY, current).apply()
        updateList()
    }

    private fun removeBlockedApp(appOrWebsite: String) {
        val current = prefs.getStringSet(BLOCKED_APPS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(appOrWebsite)
        prefs.edit().putStringSet(BLOCKED_APPS_KEY, current).apply()
        updateList()
    }

    // Add methods for adding/removing apps as needed
} 