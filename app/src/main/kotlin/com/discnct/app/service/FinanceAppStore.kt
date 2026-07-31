package com.discnct.app.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.discnct.app.finance.FINANCIAL_PACKAGE_SEED
import com.discnct.app.finance.financialPackages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.financeAppsDataStore by preferencesDataStore(name = "finance_apps")
private val ADDED_PACKAGES = stringSetPreferencesKey("added_packages")
private val REMOVED_PACKAGES = stringSetPreferencesKey("removed_packages")

/**
 * The apps Discnct stands down for, as the user has it.
 *
 * Only the *differences* from [FINANCIAL_PACKAGE_SEED] are stored, never the resolved list. Writing
 * the whole set out would freeze whatever the seed happened to be on the day it was first read, so
 * a release that adds someone's bank would never reach anyone who had already opened the screen.
 */
class FinanceAppStore(private val context: Context) {

    /** Seed plus additions minus removals — what enforcement actually checks against. */
    val exemptPackages: Flow<Set<String>> = context.financeAppsDataStore.data.map { prefs ->
        financialPackages(
            added = prefs[ADDED_PACKAGES] ?: emptySet(),
            removed = prefs[REMOVED_PACKAGES] ?: emptySet(),
        )
    }

    /** True for packages Discnct ships as exempt, so the picker can label them as such. */
    fun isSeeded(packageName: String): Boolean = packageName in FINANCIAL_PACKAGE_SEED

    /**
     * Exempt [packageName], or stop exempting it.
     *
     * Recorded on whichever side actually differs from the seed, and cleared from the other, so the
     * two sets stay a description of the user's edits rather than an accumulating log of taps.
     */
    suspend fun setExempt(packageName: String, exempt: Boolean) {
        context.financeAppsDataStore.edit { prefs ->
            val added = prefs[ADDED_PACKAGES] ?: emptySet()
            val removed = prefs[REMOVED_PACKAGES] ?: emptySet()
            if (exempt) {
                prefs[REMOVED_PACKAGES] = removed - packageName
                prefs[ADDED_PACKAGES] =
                    if (isSeeded(packageName)) added - packageName else added + packageName
            } else {
                prefs[ADDED_PACKAGES] = added - packageName
                prefs[REMOVED_PACKAGES] =
                    if (isSeeded(packageName)) removed + packageName else removed - packageName
            }
        }
    }
}
