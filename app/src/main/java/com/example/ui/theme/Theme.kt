package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF021922),
    primaryContainer = CyanPulseContainer,
    onPrimaryContainer = CyanPulseOnContainer,
    secondary = IndigoGlow,
    onSecondary = Color(0xFF0F1035),
    secondaryContainer = IndigoGlowContainer,
    onSecondaryContainer = IndigoGlowOnContainer,
    tertiary = AmberSignal,
    onTertiary = Color(0xFF281200),
    tertiaryContainer = AmberSignalContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = ElevatedSurface,
    surfaceContainerHigh = HighSurface,
    outline = BorderHairline,
    outlineVariant = Color(0xFF1E293B),
    error = CrimsonError,
    onError = Color(0xFF2A0505),
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun PulseHubTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PulseDarkColorScheme,
        typography = PulseTypography,
        content = content
    )
}
