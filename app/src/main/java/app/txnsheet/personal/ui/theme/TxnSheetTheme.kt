package app.txnsheet.personal.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF161719)
private val Paper = Color(0xFFF4F2EB)
private val PaperSurface = Color(0xFFFCFBF7)
private val PaperRaised = Color(0xFFFFFFFF)
private val PaperVariant = Color(0xFFEAE8E1)
private val InkMuted = Color(0xFF62666D)
private val PaperOutline = Color(0xFFD4D3CC)
private val Cobalt = Color(0xFF3159D9)
private val CobaltContainer = Color(0xFFE0E7FF)

private val Night = Color(0xFF090A0C)
private val NightSurface = Color(0xFF121416)
private val NightRaised = Color(0xFF17191C)
private val NightVariant = Color(0xFF1C2025)
private val NightInk = Color(0xFFF2F3EF)
private val NightMuted = Color(0xFFA9ADB4)
private val NightOutline = Color(0xFF363A40)
private val NightCobalt = Color(0xFF9CB4FF)
private val NightCobaltContainer = Color(0xFF253C7A)

private val TxnLightScheme = lightColorScheme(
    primary = Cobalt,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = Color(0xFF1C347E),
    secondary = Color(0xFF4D5D77),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E6F1),
    onSecondaryContainer = Color(0xFF29364B),
    tertiary = Color(0xFF725573),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainer = PaperSurface,
    surfaceContainerLow = Paper,
    surfaceContainerHigh = PaperVariant,
    surfaceContainerHighest = Color(0xFFE1DFD8),
    outline = PaperOutline,
    outlineVariant = Color(0xFFE2E0D9),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val TxnDarkScheme = darkColorScheme(
    primary = NightCobalt,
    onPrimary = Color(0xFF0D286D),
    primaryContainer = NightCobaltContainer,
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBAC6E0),
    onSecondary = Color(0xFF243047),
    secondaryContainer = Color(0xFF39455F),
    onSecondaryContainer = Color(0xFFD9E2FF),
    tertiary = Color(0xFFDFBCDF),
    onTertiary = Color(0xFF402A43),
    background = Night,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightSurface,
    surfaceContainerLow = Night,
    surfaceContainerHigh = NightVariant,
    surfaceContainerHighest = NightRaised,
    outline = NightOutline,
    outlineVariant = Color(0xFF272B31),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Immutable
data class TxnSemanticColors(
    val debit: Color,
    val debitContainer: Color,
    val credit: Color,
    val creditContainer: Color,
    val review: Color,
    val reviewContainer: Color,
    val queued: Color,
    val queuedContainer: Color,
    val ignored: Color,
)

private val TxnLightSemanticColors = TxnSemanticColors(
    debit = Color(0xFFB4232E),
    debitContainer = Color(0xFFFDE8EA),
    credit = Color(0xFF147A52),
    creditContainer = Color(0xFFDCF4E8),
    review = Color(0xFF8A4B00),
    reviewContainer = Color(0xFFFCE8CC),
    queued = Color(0xFF2C5FA8),
    queuedContainer = Color(0xFFE4EDFA),
    ignored = InkMuted,
)

private val TxnDarkSemanticColors = TxnSemanticColors(
    debit = Color(0xFFFFB2B7),
    debitContainer = Color(0xFF54141B),
    credit = Color(0xFF72D6A7),
    creditContainer = Color(0xFF123B2C),
    review = Color(0xFFF4BD70),
    reviewContainer = Color(0xFF4A2B00),
    queued = Color(0xFF98C1FF),
    queuedContainer = Color(0xFF1C3456),
    ignored = NightMuted,
)

val LocalTxnSemanticColors = staticCompositionLocalOf { TxnLightSemanticColors }

object TxnSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val screen = 20.dp
    val lg = 24.dp
    val section = 32.dp
    val xl = 40.dp
    val xxl = 48.dp
    val minTouchTarget = 48.dp
    val rowMinHeight = 72.dp
    val contentMaxWidth = 560.dp
}

object TxnMotion {
    const val micro = 90
    const val short = 160
    const val navigation = 220
    const val detail = 240
    const val sheet = 300
    const val confirmation = 320
}

private val TxnShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val UiSans = FontFamily.SansSerif
val TxnMono = FontFamily.Monospace

private val TxnTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.35).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = UiSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun TxnSheetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamicAvailable = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable -> dynamicLightColorScheme(context)
        darkTheme -> TxnDarkScheme
        else -> TxnLightScheme
    }
    val semanticColors = if (darkTheme) TxnDarkSemanticColors else TxnLightSemanticColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalTxnSemanticColors provides semanticColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TxnTypography,
            shapes = TxnShapes,
            content = content,
        )
    }
}

