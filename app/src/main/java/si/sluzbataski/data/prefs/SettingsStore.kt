package si.sluzbataski.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Majhne nastavitve aplikacije (SharedPreferences):
 *  - katero delovno mesto je trenutno aktivno,
 *  - ročno izbran koledar (če uporabnik ne uporablja privzetega "SluzbaTaski"),
 *  - tema (sistemska / svetla / temna).
 *
 * Do teh nastavitev dostopajo tudi widgeti, zato so v SharedPreferences
 * (hitro, sinhrono branje) in ne v bazi.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** ID aktivnega delovnega mesta ali -1, če še ni nastavljeno. */
    var activeWorkplaceId: Long
        get() = prefs.getLong(KEY_ACTIVE_WP, -1L)
        set(value) = prefs.edit().putLong(KEY_ACTIVE_WP, value).apply()

    /** ID ročno izbranega koledarja ali -1 (= išči koledar po imenu "SluzbaTaski"). */
    var calendarId: Long
        get() = prefs.getLong(KEY_CALENDAR_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_CALENDAR_ID, value).apply()

    /** Ali naj se taski z datumom vpisujejo v koledar. */
    var calendarEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CALENDAR_ENABLED, value).apply()

    /** Tema aplikacije: [THEME_SYSTEM], [THEME_LIGHT] ali [THEME_DARK]. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** Flow teme – vsi odprti zasloni se ob spremembi takoj prebarvajo. */
    fun observeThemeMode(): Flow<Int> = callbackFlow {
        trySend(themeMode)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME) trySend(themeMode)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Flow, ki odda novo vrednost vsakič, ko se aktivno delovno mesto spremeni. */
    fun observeActiveWorkplaceId(): Flow<Long> = callbackFlow {
        trySend(activeWorkplaceId)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ACTIVE_WP) trySend(activeWorkplaceId)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ACTIVE_WP = "active_workplace_id"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_CALENDAR_ENABLED = "calendar_enabled"
    }
}
