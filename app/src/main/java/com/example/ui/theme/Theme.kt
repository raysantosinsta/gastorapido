package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = ObsidianBackground,
    primaryContainer = GraphiteElevated,
    onPrimaryContainer = PureWhite,
    secondary = ZincSecondary,
    onSecondary = ObsidianBackground,
    secondaryContainer = GraphiteCard,
    onSecondaryContainer = SoftWhite,
    tertiary = MonoStep2,
    onTertiary = ObsidianBackground,
    background = ObsidianBackground,
    onBackground = SoftWhite,
    surface = CharcoalSurface,
    onSurface = SoftWhite,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = ZincSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = GraphiteCard,
    surfaceContainerHigh = GraphiteElevated,
    surfaceContainerHighest = GraphiteHover,
    outline = StrongBorder,
    outlineVariant = HairlineBorder,
    error = DestructiveRed,
    onError = PureWhite,
    errorContainer = DestructiveContainer,
    onErrorContainer = OnDestructiveContainer,
    inverseSurface = PureWhite,
    inverseOnSurface = ObsidianBackground,
    inversePrimary = ObsidianBackground
)

private val GastoRapidoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GastoRapidoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeDarkColorScheme,
        typography = GastoRapidoTypography,
        shapes = GastoRapidoShapes,
        content = content
    )
}
