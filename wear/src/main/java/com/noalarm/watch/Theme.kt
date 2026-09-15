package com.noalarm.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Stessa palette Nothing del telefono (app/ui/theme/Theme.kt): nessun modulo
// condiviso fra :app e :wear, si duplicano solo questi pochi colori.
private val NothingRed = Color(0xFFD71921)
private val Ink = Color(0xFF000000)
private val Chalk = Color(0xFFFFFFFF)
private val Muted = Color(0xFF8A8A8A)
private val Surface1 = Color(0xFF1C1C1C)

private val NothingDark = darkColorScheme(
    primary = Chalk,
    onPrimary = Ink,
    secondary = NothingRed,
    onSecondary = Chalk,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Surface1,
    onSurfaceVariant = Muted,
    error = NothingRed,
    onError = Chalk,
)

private val NothingTypography = Typography().let { t ->
    t.copy(
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.sp),
        labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
        bodySmall = t.bodySmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp),
    )
}

@Composable
fun NoAlarmWatchTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = NothingDark, typography = NothingTypography, content = content)
