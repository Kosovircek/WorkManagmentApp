package si.sluzbataski.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import si.sluzbataski.AppGraph
import si.sluzbataski.data.prefs.SettingsStore

private val Indigo = Color(0xFF3949AB)

/**
 * Tema aplikacije (Material 3).
 *
 *  - Svetla/temna: po izbiri v Nastavitvah (Sistemska / Svetla / Temna).
 *    "Sistemska" sledi nastavitvi telefona.
 *  - Na Android 12+ prevzame barve iz ozadja telefona (Material You), na starejših
 *    uporabi indigo barvno shemo.
 *  - Ikone v statusni vrstici se prilagodijo (temne na svetlem, svetle na temnem).
 */
@Composable
fun SluzbaTaskiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppGraph.get(context).settings }
    val mode by remember { settings.observeThemeMode() }.collectAsState(initial = settings.themeMode)
    val dark = when (mode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9FA8DA))
        else -> lightColorScheme(primary = Indigo)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
