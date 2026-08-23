package com.android.car.settings.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.b231001.bmaterial.uicore.tokens.BTheme
import com.b231001.bmaterial.uicore.tokens.BThemePresets

private val AutomotivePreset =
    BThemePresets.Automotive.copy(
        darkScheme =
            BThemePresets.Automotive.darkScheme.copy(
                primary = Color(0xFF8BC9FF),
                onPrimary = Color(0xFF00344F),
                primaryContainer = Color(0xFF004C70),
                onPrimaryContainer = Color(0xFFC9E6FF),
                secondary = Color(0xFF9CCAFF),
                onSecondary = Color(0xFF003258),
                background = Color(0xFF0B1116),
                onBackground = Color(0xFFE6F1F8),
                surface = Color(0xFF0F171D),
                onSurface = Color(0xFFE6F1F8),
                surfaceVariant = Color(0xFF24313A),
                onSurfaceVariant = Color(0xFFBECBD4),
                surface1 = Color(0xFF141E25),
                surface2 = Color(0xFF19252D),
                surface3 = Color(0xFF21303A),
                outline = Color(0xFF7D909C),
                outlineVariant = Color(0xFF3C4C56),
                warning = Color(0xFFFFB95C),
                onWarning = Color(0xFF432B00),
                warningContainer = Color(0xFF5B3B00),
                onWarningContainer = Color(0xFFFFDDB0),
                info = Color(0xFF65D9FF),
                onInfo = Color(0xFF003544),
                success = Color(0xFF7DDBA7),
                onSuccess = Color(0xFF00391F),
            ),
        lightScheme =
            BThemePresets.Automotive.lightScheme.copy(
                primary = Color(0xFF00668A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFBDE9FF),
                onPrimaryContainer = Color(0xFF001F2C),
                secondary = Color(0xFF355F75),
                onSecondary = Color.White,
                background = Color(0xFFF4F8FA),
                onBackground = Color(0xFF151D22),
                surface = Color(0xFFFAFCFD),
                onSurface = Color(0xFF151D22),
                surfaceVariant = Color(0xFFDCE7EC),
                onSurfaceVariant = Color(0xFF3F4B52),
                surface1 = Color(0xFFF1F6F8),
                surface2 = Color(0xFFE8F0F3),
                surface3 = Color(0xFFDDE8ED),
                outline = Color(0xFF6E7C84),
                outlineVariant = Color(0xFFBEC9CE),
                warning = Color(0xFF8A4F00),
                onWarning = Color.White,
                warningContainer = Color(0xFFFFDDB0),
                onWarningContainer = Color(0xFF2C1600),
                info = Color(0xFF006780),
                onInfo = Color.White,
                success = Color(0xFF006D3D),
                onSuccess = Color.White,
            ),
    )

/**
 * Automotive displays are viewed from farther away than a handset.  Keep the B-Material
 * typography family, but give every semantic role a little more visual weight and line room.
 * This is intentionally applied at the theme boundary so feature modules do not need to carry
 * one-off font-size overrides (and so dialogs use the exact same scale as their parent screen).
 */
private fun TextStyle.forAutomotiveDisplay(scale: Float = 1.12f): TextStyle =
    copy(
        fontSize = fontSize.scaledBy(scale),
        lineHeight = lineHeight.scaledBy(scale),
    )

private fun TextUnit.scaledBy(scale: Float): TextUnit = if (this == TextUnit.Unspecified) this else this * scale

private fun androidx.compose.material3.Typography.forAutomotiveDisplay(): androidx.compose.material3.Typography =
    copy(
        displayLarge = displayLarge.forAutomotiveDisplay(1.08f),
        displayMedium = displayMedium.forAutomotiveDisplay(1.08f),
        displaySmall = displaySmall.forAutomotiveDisplay(1.1f),
        headlineLarge = headlineLarge.forAutomotiveDisplay(1.1f),
        headlineMedium = headlineMedium.forAutomotiveDisplay(1.1f),
        headlineSmall = headlineSmall.forAutomotiveDisplay(1.12f),
        titleLarge = titleLarge.forAutomotiveDisplay(1.12f),
        titleMedium = titleMedium.forAutomotiveDisplay(1.14f),
        titleSmall = titleSmall.forAutomotiveDisplay(1.14f),
        bodyLarge = bodyLarge.forAutomotiveDisplay(1.14f),
        bodyMedium = bodyMedium.forAutomotiveDisplay(1.16f),
        bodySmall = bodySmall.forAutomotiveDisplay(1.18f),
        labelLarge = labelLarge.forAutomotiveDisplay(1.15f),
        labelMedium = labelMedium.forAutomotiveDisplay(1.18f),
        labelSmall = labelSmall.forAutomotiveDisplay(1.2f),
    )

@Composable
fun MySystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    BTheme(
        preset = AutomotivePreset,
        darkTheme = darkTheme,
    ) {
        // BTheme owns the automotive color/preset locals.  A nested MaterialTheme only replaces
        // typography, preserving those locals while making every screen and dialog readable.
        MaterialTheme(
            typography = MaterialTheme.typography.forAutomotiveDisplay(),
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
