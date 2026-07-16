package com.sleepalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NightColors {
    val Background = Color(0xFF14141F)
    val RingBackground = Color(0xFF0C0C15)
    val Card = Color(0xFF1B1B28)
    val CardElevated = Color(0xFF1D1D2E)
    val HeroTop = Color(0xFF1E1E30)
    val HeroBottom = Color(0xFF191926)
    val Key = Color(0xFF262637)
    val TrackOff = Color(0xFF2B2B3E)

    val Amber = Color(0xFFEDB45E)
    val OnAmber = Color(0xFF1A1208)
    val Lavender = Color(0xFFA99BDD)

    val Text = Color(0xFFECEAF4)
    val Body = Color(0xFFB6B2C6)
    val Dim = Color(0xFF9B97AD)
    val Faint = Color(0xFF6F6B82)
    val Disabled = Color(0xFF5B5870)

    val Error = Color(0xFFFF9D9D)
    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x24FFFFFF)
    val AmberFaint = Color(0x29EDB45E)
    val LavenderFaint = Color(0x29A99BDD)
}

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

private val NightColorScheme = darkColorScheme(
    primary = NightColors.Amber,
    onPrimary = NightColors.OnAmber,
    primaryContainer = NightColors.AmberFaint,
    onPrimaryContainer = NightColors.Amber,
    secondary = NightColors.Lavender,
    onSecondary = NightColors.OnAmber,
    secondaryContainer = NightColors.LavenderFaint,
    onSecondaryContainer = NightColors.Lavender,
    background = NightColors.Background,
    onBackground = NightColors.Text,
    surface = NightColors.Background,
    onSurface = NightColors.Text,
    surfaceVariant = NightColors.Card,
    onSurfaceVariant = NightColors.Dim,
    surfaceContainer = NightColors.Card,
    surfaceContainerLow = NightColors.Card,
    surfaceContainerHigh = NightColors.CardElevated,
    surfaceContainerHighest = NightColors.CardElevated,
    outline = NightColors.BorderStrong,
    outlineVariant = NightColors.Border,
    error = NightColors.Error,
    onError = NightColors.OnAmber
)

private val NightTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun SleepAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColorScheme,
        typography = NightTypography,
        content = content
    )
}

@Composable
fun NightCard(
    modifier: Modifier = Modifier,
    background: Color = NightColors.Card,
    cornerRadius: Int = 16,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, NightColors.Border, shape)
            .padding(contentPadding.dp),
        content = content
    )
}
