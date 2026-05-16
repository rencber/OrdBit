package com.ordbit.svenska

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// One DataStore file, kept inside the app's private storage on the device.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ordbit_progress")

/**
 * A snapshot of everything saved locally.
 *
 * Each word carries a three-state mark, exactly like WordBit:
 *   statusNo  = "Kan inte"  (don't know it)
 *   statusMid = "Osäker"    (in between)
 *   statusYes = "Kan"       (know it)
 * A word id appears in at most one of the three sets; absence means unmarked.
 */
data class Saved(
    val statusNo: Set<String> = emptySet(),
    val statusMid: Set<String> = emptySet(),
    val statusYes: Set<String> = emptySet(),
    val favorites: Set<String> = emptySet(),
    val theme: String = "dagsljus",
    val section: String = "A1",
    val mode: String = "las"
)

/**
 * Reads and writes the user's progress. Everything lives on the device only —
 * there is no account, no sync and no network call.
 */
class Store(private val context: Context) {

    private object Keys {
        val statusNo = stringSetPreferencesKey("status_no")
        val statusMid = stringSetPreferencesKey("status_mid")
        val statusYes = stringSetPreferencesKey("status_yes")
        val favorites = stringSetPreferencesKey("favorites")
        val theme = stringPreferencesKey("theme")
        val section = stringPreferencesKey("section")
        val mode = stringPreferencesKey("mode")
    }

    suspend fun load(): Saved {
        val p = context.dataStore.data.first()
        return Saved(
            statusNo = p[Keys.statusNo] ?: emptySet(),
            statusMid = p[Keys.statusMid] ?: emptySet(),
            statusYes = p[Keys.statusYes] ?: emptySet(),
            favorites = p[Keys.favorites] ?: emptySet(),
            theme = p[Keys.theme] ?: "dagsljus",
            section = p[Keys.section] ?: "A1",
            mode = p[Keys.mode] ?: "las"
        )
    }

    suspend fun save(s: Saved) {
        context.dataStore.edit { p ->
            p[Keys.statusNo] = s.statusNo
            p[Keys.statusMid] = s.statusMid
            p[Keys.statusYes] = s.statusYes
            p[Keys.favorites] = s.favorites
            p[Keys.theme] = s.theme
            p[Keys.section] = s.section
            p[Keys.mode] = s.mode
        }
    }
}
