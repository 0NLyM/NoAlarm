package com.noalarm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.R
import com.noalarm.data.AppFont
import com.noalarm.data.Store

// Palette Nothing: nero, bianco, un solo rosso.
val NothingRed = Color(0xFFD71921)
val Ink = Color(0xFF000000)
val Surface1 = Color(0xFF101010)
val Surface2 = Color(0xFF1C1C1C)
val Chalk = Color(0xFFFFFFFF)
val Muted = Color(0xFF8A8A8A)
val Line = Color(0xFF2A2A2A)

// Punti spenti della griglia dot-matrix. Volutamente molto vicini al fondo:
// devono suggerire il display senza rubare contrasto alle cifre accese.
val DotOffDark = Color(0xFF0D0D0D)
val DotOffLight = Color(0xFFEFEFEA)

val LocalDotOff = staticCompositionLocalOf { DotOffDark }

private val Dark = darkColorScheme(
    primary = Chalk,
    onPrimary = Ink,
    secondary = NothingRed,
    onSecondary = Chalk,
    tertiary = NothingRed,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Surface1,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    outline = Line,
    error = NothingRed,
    onError = Chalk,
)

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Chalk,
    secondary = NothingRed,
    onSecondary = Chalk,
    tertiary = NothingRed,
    background = Color(0xFFF5F5F3),
    onBackground = Ink,
    surface = Color(0xFFF5F5F3),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E7E4),
    onSurfaceVariant = Color(0xFF5A5A5A),
    surfaceContainer = Color(0xFFECECE9),
    surfaceContainerHigh = Color(0xFFE2E2DE),
    outline = Color(0xFFCFCFC9),
    error = NothingRed,
    onError = Chalk,
)

// Il carattere "Nothing" lo da' soprattutto il dot-matrix disegnato a mano
// (DotText, per le cifre): questa Typography copre il resto dei testi, e
// segue il carattere scelto nelle impostazioni (Sistema, Geist o Inter).
private val GeistFamily = FontFamily(Font(R.font.geist_variable))
private val InterFamily = FontFamily(Font(R.font.inter_variable))

private fun fontFamilyOf(font: AppFont) = when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.GEIST -> GeistFamily
    AppFont.INTER -> InterFamily
}

private fun typography(family: FontFamily) = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = family, letterSpacing = 2.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = family, letterSpacing = 1.sp),
        titleMedium = t.titleMedium.copy(fontFamily = family, letterSpacing = 1.sp),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        ),
        labelMedium = TextStyle(fontFamily = family, fontSize = 11.sp, letterSpacing = 1.5.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = family),
        bodySmall = t.bodySmall.copy(fontFamily = family, letterSpacing = 0.5.sp),
    )
}

@Composable
fun NoAlarmTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) Dark else Light
    val settings by Store.settings.collectAsStateWithLifecycle()
    val view = LocalContext.current as? Activity
    if (view != null) SideEffect {
        WindowCompat.getInsetsController(view.window, view.window.decorView)
            .isAppearanceLightStatusBars = !dark
    }
    CompositionLocalProvider(LocalDotOff provides if (dark) DotOffDark else DotOffLight) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(fontFamilyOf(settings.fontFamily)),
            content = content,
        )
    }
}
