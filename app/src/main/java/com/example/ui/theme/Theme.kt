package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.example.data.local.CalmVisualPrefs

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

private val CalmDarkColorScheme = darkColorScheme(
    primary = CalmMistPrimary,
    onPrimary = Color(0xFF0B1117),
    primaryContainer = CalmMistContainer,
    onPrimaryContainer = CalmMistOnContainer,
    secondary = CalmStoneSecondary,
    onSecondary = Color(0xFF0F141C),
    secondaryContainer = CalmStoneContainer,
    onSecondaryContainer = CalmStoneOnContainer,
    tertiary = CalmSandAmber,
    onTertiary = Color(0xFF1E190E),
    tertiaryContainer = CalmSandContainer,
    onTertiaryContainer = Color(0xFFE7DBC0),
    background = CalmBackground,
    onBackground = TextPrimary,
    surface = CalmSurface,
    onSurface = TextPrimary,
    surfaceVariant = CalmElevatedSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = CalmSurface,
    surfaceContainer = CalmElevatedSurface,
    surfaceContainerHigh = CalmHighSurface,
    outline = CalmBorderHairline,
    outlineVariant = Color(0xFF1C222E),
    error = CalmRoseError,
    onError = Color(0xFF1F1011),
    errorContainer = CalmRoseContainer,
    onErrorContainer = Color(0xFFE7CACA)
)

@Immutable
data class NephoPalette(
    val isCalmMode: Boolean,
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val highSurface: Color,
    val border: Color,
    val accent: Color,
    val accentContainer: Color,
    val secondaryAccent: Color,
    val secondaryContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val emerald: Color,
    val emeraldContainer: Color,
    val crimson: Color,
    val crimsonContainer: Color,
    val calmBlue: Color = CalmGlowBlue,
    val calmBlueBright: Color = CalmGlowBlueBright,
    val calmBlueDeep: Color = CalmGlowBlueDeep,
    val calmBlueSurface: Color = CalmGlowBlueSurface
) {
    fun accentForApp(appAccent: Color): Color = if (isCalmMode) accent else appAccent
}

private val VividNephoPalette = NephoPalette(
    isCalmMode = false,
    background = ObsidianBackground,
    surface = MidnightSurface,
    elevatedSurface = ElevatedSurface,
    highSurface = HighSurface,
    border = BorderHairline,
    accent = CyanPulse,
    accentContainer = CyanPulseContainer,
    secondaryAccent = IndigoGlow,
    secondaryContainer = IndigoGlowContainer,
    amber = AmberSignal,
    amberContainer = AmberSignalContainer,
    emerald = EmeraldOk,
    emeraldContainer = EmeraldOkContainer,
    crimson = CrimsonError,
    crimsonContainer = CrimsonErrorContainer
)

private val CalmNephoPalette = NephoPalette(
    isCalmMode = true,
    background = CalmBackground,
    surface = CalmSurface,
    elevatedSurface = CalmElevatedSurface,
    highSurface = CalmHighSurface,
    border = CalmBorderHairline,
    accent = CalmMistPrimary,
    accentContainer = CalmMistContainer,
    secondaryAccent = CalmStoneSecondary,
    secondaryContainer = CalmStoneContainer,
    amber = CalmSandAmber,
    amberContainer = CalmSandContainer,
    emerald = CalmSageOk,
    emeraldContainer = CalmSageContainer,
    crimson = CalmRoseError,
    crimsonContainer = CalmRoseContainer
)

val LocalCalmVisualPrefs = staticCompositionLocalOf { CalmVisualPrefs() }
val LocalNephoPalette = staticCompositionLocalOf { CalmNephoPalette }

/** Reusable desaturation ColorFilter for calm monochrome app icons. */
val MonochromeIconColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0f)
    }
)

@Composable
fun NephoTheme(
    calmPrefs: CalmVisualPrefs = CalmVisualPrefs(),
    content: @Composable () -> Unit
) {
    val palette = if (calmPrefs.calmPaletteEnabled) CalmNephoPalette else VividNephoPalette
    val colorScheme = if (calmPrefs.calmPaletteEnabled) CalmDarkColorScheme else PulseDarkColorScheme

    CompositionLocalProvider(
        LocalCalmVisualPrefs provides calmPrefs,
        LocalNephoPalette provides palette
    ) {
        com.example.ui.ProvideCalmLightingClock {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = PulseTypography,
                content = content
            )
        }
    }
}
