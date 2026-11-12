package com.sarim.trinity.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Light, dark, or whatever the phone is doing. */
enum class Brightness { System, Light, Dark }

/** Remembers light/dark across launches. */
object ThemePrefs {
    private const val FILE = "trinity.theme"
    private const val KEY_BRIGHTNESS = "brightness"

    var brightness by mutableStateOf(Brightness.System)
        private set

    fun load(context: Context) {
        val prefs = prefs(context)
        brightness = runCatching { Brightness.valueOf(prefs.getString(KEY_BRIGHTNESS, null).orEmpty()) }
            .getOrDefault(Brightness.System)
    }

    fun setBrightness(context: Context, next: Brightness) {
        brightness = next
        prefs(context).edit().putString(KEY_BRIGHTNESS, next.name).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * The paper palette is deliberately achromatic: no purple, neon, deep blue, red,
 * green or orange, and no gradients. Hierarchy comes from weight, scale and
 * hairline rules only.
 *
 * Every value here is observable state rather than a constant, so the whole app
 * (which reads `Palette.Ink`, `Palette.Paper`, …) restyles itself when the theme
 * changes without a second set of screens. [apply] is the single writer.
 */
object Palette {
    private val BonePaper = Color(0xFFF1EDE4)
    private val PaperPaper = Color(0xFFFAF8F2)
    private val InkPaper = Color(0xFF17160F)
    private val MutedPaper = Color(0xFF6D6A5C)
    private val FaintPaper = Color(0xFF9C9788)
    private val LinePaper = Color(0xFFC6BEA8) // visible row border: adjacent rows must not blur
    private val LineSoftPaper = Color(0xFFDCD6C8) // section rules
    private val WashPaper = Color(0xFFE7E2D5)

    // The two accents the paper look now allows: a pale green and plain blues
    // (no indigo or purple). They sit behind ink as tints, never as a full fill.
    private val GreenPaper = Color(0xFF2E7A61)
    private val GreenWashPaper = Color(0x242E7A61)
    private val BluePaper = Color(0xFF2B5D8C)
    private val BlueWashPaper = Color(0x242B5D8C)

    /** True in the Material You look: rounded shapes, tonal surfaces, no rules. */
    var isMaterial by mutableStateOf(false)
        private set

    /** The Expressive variant: rounder shapes, more colour, see-through bars. */
    var isExpressive by mutableStateOf(false)
        private set

    var isDark by mutableStateOf(false)
        private set

    /** Bars and sheets in the Material looks: a shade translucent. */
    var Glass by mutableStateOf(PaperPaper)
        private set

    /** Accent pair: pale green and blue in paper, scheme roles in Material. */
    var Green by mutableStateOf(GreenPaper)
        private set
    var Blue by mutableStateOf(BluePaper)
        private set
    var GreenWash by mutableStateOf(GreenWashPaper)
        private set
    var BlueWash by mutableStateOf(BlueWashPaper)
        private set

    /** Rotating container tints, so nothing in Expressive is one flat colour. */
    var Accents by mutableStateOf(listOf(GreenWashPaper, BlueWashPaper))
        private set

    var Bone by mutableStateOf(BonePaper)
        private set
    var Paper by mutableStateOf(PaperPaper)
        private set
    var Ink by mutableStateOf(InkPaper)
        private set
    var Muted by mutableStateOf(MutedPaper)
        private set
    var Faint by mutableStateOf(FaintPaper)
        private set
    var Line by mutableStateOf(LinePaper)
        private set
    var LineSoft by mutableStateOf(LineSoftPaper)
        private set
    var Wash by mutableStateOf(WashPaper)
        private set

    fun apply(scheme: ColorScheme, dark: Boolean) {
        // One look only now: transparent Expressive.
        val material = true
        val expressive = true
        // Guarded writes: each value only changes when the look actually changes,
        // so composition never chases its own tail.
        if (isMaterial != material) isMaterial = material
        if (isExpressive != expressive) isExpressive = expressive
        if (isDark != dark) isDark = dark
        val bone = if (material) scheme.background else BonePaper
        if (Bone != bone) Bone = bone
        val paper = if (material) scheme.surface else PaperPaper
        if (Paper != paper) Paper = paper
        val ink = if (material) scheme.onSurface else InkPaper
        if (Ink != ink) Ink = ink
        val muted = if (material) scheme.onSurfaceVariant else MutedPaper
        if (Muted != muted) Muted = muted
        val faint = if (material) scheme.outline else FaintPaper
        if (Faint != faint) Faint = faint
        // Material You has no rules between rows, and dividers under a tonal
        // surface are exactly the printed feel the other theme is for.
        val line = if (material) Color.Transparent else LinePaper
        if (Line != line) Line = line
        val lineSoft = if (material) Color.Transparent else LineSoftPaper
        if (LineSoft != lineSoft) LineSoft = lineSoft
        // Expressive leans on the colour role for chips and avatars; plain Material
        // You stays on the neutral container.
        val wash = when {
            expressive -> scheme.primaryContainer
            material -> scheme.surfaceContainerHighest
            else -> WashPaper
        }
        if (Wash != wash) Wash = wash
        // A bar you can see a little of the page through.
        val glass = if (material) {
            scheme.surface.copy(alpha = if (expressive) 0.82f else 0.92f)
        } else {
            PaperPaper
        }
        if (Glass != glass) Glass = glass
        val green = if (material) scheme.primary else GreenPaper
        if (Green != green) Green = green
        val blue = if (material) scheme.tertiary else BluePaper
        if (Blue != blue) Blue = blue
        val greenWash = when {
            expressive -> scheme.primaryContainer.copy(alpha = 0.55f)
            material -> scheme.surfaceContainerHigh
            else -> GreenWashPaper
        }
        if (GreenWash != greenWash) GreenWash = greenWash
        val blueWash = when {
            expressive -> scheme.tertiaryContainer.copy(alpha = 0.50f)
            material -> scheme.surfaceContainerHigh
            else -> BlueWashPaper
        }
        if (BlueWash != blueWash) BlueWash = blueWash
        val accents = when {
            expressive -> listOf(
                scheme.primaryContainer.copy(alpha = 0.55f),
                scheme.secondaryContainer.copy(alpha = 0.55f),
                scheme.tertiaryContainer.copy(alpha = 0.55f),
                scheme.primary.copy(alpha = 0.20f),
            )
            material -> listOf(scheme.surfaceContainerHigh)
            else -> listOf(GreenWashPaper, BlueWashPaper)
        }
        if (Accents != accents) Accents = accents
    }
}

private val PaperColors = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.Wash,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Muted,
    onSecondary = Palette.Paper,
    background = Palette.Bone,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Wash,
    onSurfaceVariant = Palette.Muted,
    tertiary = Palette.Blue,
    onTertiary = Palette.Paper,
    tertiaryContainer = Palette.BlueWash,
    onTertiaryContainer = Palette.Ink,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Ink,
    onError = Palette.Paper,
)

/**
 * What a phone without wallpaper colours gets: the same shape language and tonal
 * steps as Material You, in neutral slate, so it still stays clear of purple.
 */
private fun neutralScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFB9C7D6),
        onPrimary = Color(0xFF23313C),
        primaryContainer = Color(0xFF3A4854),
        onPrimaryContainer = Color(0xFFD5E3F1),
        secondary = Color(0xFFC2C7CE),
        onSecondary = Color(0xFF2B3138),
        secondaryContainer = Color(0xFF41474E),
        onSecondaryContainer = Color(0xFFDEE3EA),
        tertiary = Color(0xFFC6C6CF),
        onTertiary = Color(0xFF2F3038),
        tertiaryContainer = Color(0xFF45464F),
        onTertiaryContainer = Color(0xFFE2E1EB),
        background = Color(0xFF111315),
        onBackground = Color(0xFFE2E2E5),
        surface = Color(0xFF111315),
        onSurface = Color(0xFFE2E2E5),
        surfaceVariant = Color(0xFF44474B),
        onSurfaceVariant = Color(0xFFC4C6CA),
        outline = Color(0xFF8E9094),
        outlineVariant = Color(0xFF44474B),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF44525E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E2EF),
        onPrimaryContainer = Color(0xFF16232D),
        secondary = Color(0xFF55606A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCE3EB),
        onSecondaryContainer = Color(0xFF171C21),
        tertiary = Color(0xFF555F66),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDDE3E9),
        onTertiaryContainer = Color(0xFF181C20),
        background = Color(0xFFF7F8F9),
        onBackground = Color(0xFF191C1E),
        surface = Color(0xFFFCFCFD),
        onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFE1E4E8),
        onSurfaceVariant = Color(0xFF45484C),
        outline = Color(0xFF75787C),
        outlineVariant = Color(0xFFC5C7CB),
    )
}

private fun Context.findActivity(): Activity? = generateSequence(this) {
    (it as? ContextWrapper)?.baseContext
}.filterIsInstance<Activity>().firstOrNull()

/**
 * Expressive's own palette: green, blue and teal instead of whatever the wallpaper
 * happens to be. Wallpaper colours can land on purple or indigo, and those stay out
 * of this app; here the "more colour" comes from three roles rather than the phone.
 */
private fun expressiveScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFF8FD5BC),
        onPrimary = Color(0xFF00382A),
        primaryContainer = Color(0xFF1B4E3D),
        onPrimaryContainer = Color(0xFFC4F2DF),
        secondary = Color(0xFFA9C8EA),
        onSecondary = Color(0xFF08243C),
        secondaryContainer = Color(0xFF23405C),
        onSecondaryContainer = Color(0xFFD3E4FF),
        tertiary = Color(0xFF8FD1DE),
        onTertiary = Color(0xFF00363E),
        tertiaryContainer = Color(0xFF0F4C56),
        onTertiaryContainer = Color(0xFFACEBF6),
        background = Color(0xFF0E1412),
        onBackground = Color(0xFFDDE5E0),
        surface = Color(0xFF111816),
        onSurface = Color(0xFFDDE5E0),
        surfaceVariant = Color(0xFF25302C),
        onSurfaceVariant = Color(0xFFBEC9C3),
        outline = Color(0xFF89938D),
        outlineVariant = Color(0xFF3F4944),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF2E7A61),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB8E6D6),
        onPrimaryContainer = Color(0xFF07301F),
        secondary = Color(0xFF2B5D8C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCFE4FF),
        onSecondaryContainer = Color(0xFF001C36),
        tertiary = Color(0xFF1F7A8C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBDEAF2),
        onTertiaryContainer = Color(0xFF00252C),
        background = Color(0xFFF3F8F5),
        onBackground = Color(0xFF171D1A),
        surface = Color(0xFFFAFDFB),
        onSurface = Color(0xFF171D1A),
        surfaceVariant = Color(0xFFE0EAE4),
        onSurfaceVariant = Color(0xFF3F4944),
        outline = Color(0xFF6F7973),
        outlineVariant = Color(0xFFBFC9C3),
    )
}

private val PaperType = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-1.4).sp,
        lineHeight = 38.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
    ),
)

/** Material's own type scale: a light, large title and no tracked-out mono anywhere. */
private val MaterialType = Typography().let { base ->
    base.copy(
        displaySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 28.sp,
            letterSpacing = 0.sp,
            lineHeight = 36.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
    )
}

@Composable
fun TrinityTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Paper is ink on paper and has no dark form; the Material looks follow the
    // user's choice, falling back to the system.
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePrefs.brightness) {
        Brightness.Light -> false
        Brightness.Dark -> true
        Brightness.System -> systemDark
    }
    val scheme = remember(dark) { expressiveScheme(dark) }
    Palette.apply(scheme, dark)
    // The window behind the status bar has to match the page, or a cream band shows
    // above a dark screen.
    val window = context.findActivity()?.window
    LaunchedEffect(Palette.Bone) {
        window?.setBackgroundDrawable(ColorDrawable(Palette.Bone.toArgb()))
    }
    MaterialTheme(
        colorScheme = scheme,
        // Only the paper look uses the tracked mono scale; both Material looks use
        // the standard one.
        typography = MaterialType,
        content = content,
    )
}
