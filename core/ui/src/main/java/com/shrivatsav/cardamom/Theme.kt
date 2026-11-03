package com.shrivatsav.cardamom
import com.shrivatsav.cardamom.ui.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

enum class AppTheme(val label: String) {
    SYSTEM("System default"),
    DARK("Dark"),
    LIGHT("Light"),
}

enum class LockTimeout(val label: String, val seconds: Long) {
    IMMEDIATELY("Immediately", 0L),
    SECS_15("15 seconds", 15L),
    MIN_1("1 minute", 60L),
    MIN_5("5 minutes", 300L),
}

data class CardamomPalette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val outline: Color,
    val ink: Color,
    val yellow: Color,
    val danger: Color,
    val isDark: Boolean,
)

val DarkPalette = CardamomPalette(
    bg = Color(0xFF161616),
    surface = Color(0xFF232323),
    text = Color(0xFFFBF3E4),
    muted = Color(0xFFA8A39A),
    outline = Color(0xFF3A3A3A),
    ink = Color(0xFF161616),
    yellow = Color(0xFFF5C842),
    danger = Color(0xFFFF9C94),
    isDark = true,
)

val LightPalette = CardamomPalette(
    bg = Color(0xFFF8F6F1),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1A1917),
    muted = Color(0xFF78746B),
    outline = Color(0xFFE4DFD5),
    ink = Color(0xFF1A1917),
    yellow = Color(0xFFE2B026),
    danger = Color(0xFFDC362D),
    isDark = false,
)

val LocalCardamomPalette = staticCompositionLocalOf { DarkPalette }

object C {
    val Bg: Color @Composable get() = LocalCardamomPalette.current.bg
    val Surface: Color @Composable get() = LocalCardamomPalette.current.surface
    val Cream: Color @Composable get() = LocalCardamomPalette.current.text
    val Yellow: Color @Composable get() = LocalCardamomPalette.current.yellow
    val Muted: Color @Composable get() = LocalCardamomPalette.current.muted
    val Ink: Color @Composable get() = LocalCardamomPalette.current.ink
    val Outline: Color @Composable get() = LocalCardamomPalette.current.outline
    val Danger: Color @Composable get() = LocalCardamomPalette.current.danger

    val StaticInk = Color(0xFF161616)
    val Orange = Color(0xFFF6B04B)
    val Sage = Color(0xFFCFE0CC)
    val Mint = Color(0xFFA8D8B4)
    val Purple = Color(0xFF8B7CF6)
    val Pink = Color(0xFFF08A8A)
    val Sky = Color(0xFF94C7EC)
    val Amber = Color(0xFFEDB84E)
    val Lavender = Color(0xFFB5AEF8)
    val Rose = Color(0xFFEAA2B2)
    val Olive = Color(0xFFC4D5B0)
    val CardColors = listOf(Orange, Sage, Mint, Purple, Pink, Sky, Amber, Lavender, Rose, Olive)
}

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlexFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 300f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 400f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 500f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 600f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 700f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 800f),
        ),
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("wght", 900f),
        ),
    ),
)

private val defaultTypography = Typography()

val CardamomTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = GoogleSansFlexFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = GoogleSansFlexFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = GoogleSansFlexFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = GoogleSansFlexFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = GoogleSansFlexFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = GoogleSansFlexFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = GoogleSansFlexFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = GoogleSansFlexFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = GoogleSansFlexFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = GoogleSansFlexFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = GoogleSansFlexFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = GoogleSansFlexFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = GoogleSansFlexFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = GoogleSansFlexFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = GoogleSansFlexFamily),
)

@Composable
fun CardamomTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (isDark) DarkPalette else LightPalette
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.yellow, onPrimary = palette.ink,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.surface, onSurfaceVariant = palette.muted,
            outline = palette.outline,
        )
    } else {
        lightColorScheme(
            primary = palette.yellow, onPrimary = Color.White,
            background = palette.bg, onBackground = palette.text,
            surface = palette.surface, onSurface = palette.text,
            surfaceVariant = Color(0xFFEFEBE2), onSurfaceVariant = palette.muted,
            outline = palette.outline,
        )
    }

    CompositionLocalProvider(
        LocalCardamomPalette provides palette,
        androidx.compose.material3.LocalContentColor provides palette.text,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CardamomTypography,
            content = {
                ProvideTextStyle(TextStyle(fontFamily = GoogleSansFlexFamily)) {
                    content()
                }
            },
        )
    }
}

