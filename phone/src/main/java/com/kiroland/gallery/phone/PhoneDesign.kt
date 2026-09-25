package com.kiroland.gallery.phone

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiroland.gallery.shared.Brand

// "Csend · Keret": flat surfaces, type first, amber only for the primary action and
// for the frame around whatever is selected - the same language as the TV screen.

private val darkScheme: ColorScheme = darkColorScheme(
    primary = Brand.Amber,
    onPrimary = Brand.OnAmber,
    primaryContainer = Brand.Night3,
    onPrimaryContainer = Brand.TextHigh,
    secondary = Brand.Amber,
    onSecondary = Brand.OnAmber,
    background = Brand.Night0,
    onBackground = Brand.TextHigh,
    surface = Brand.Night0,
    onSurface = Brand.TextHigh,
    surfaceVariant = Brand.Night2,
    onSurfaceVariant = Brand.TextMid,
    surfaceContainerLowest = Brand.Night0,
    surfaceContainerLow = Brand.Night1,
    surfaceContainer = Brand.Night1,
    surfaceContainerHigh = Brand.Night2,
    surfaceContainerHighest = Brand.Night3,
    outline = Brand.NightLine,
    outlineVariant = Brand.NightLine,
    error = Brand.Danger,
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Brand.AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Brand.Paper2,
    onPrimaryContainer = Brand.Ink,
    secondary = Brand.AmberDeep,
    background = Brand.Paper0,
    onBackground = Brand.Ink,
    surface = Brand.Paper0,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Paper2,
    onSurfaceVariant = Brand.InkMid,
    surfaceContainerLowest = Brand.Paper1,
    surfaceContainerLow = Brand.Paper1,
    surfaceContainer = Brand.Paper1,
    surfaceContainerHigh = Brand.Paper2,
    surfaceContainerHighest = Color(0xFFE8E8E4),
    outline = Brand.PaperLine,
    outlineVariant = Brand.PaperLine,
    error = Color(0xFFB3261E),
)

private val typography: Typography = Typography().let { base ->
    fun TextStyle.jakarta(weight: FontWeight? = null) = copy(fontFamily = Brand.Font, fontWeight = weight ?: fontWeight)
    Typography(
        displaySmall = base.displaySmall.jakarta(FontWeight.Light),
        headlineLarge = base.headlineLarge.jakarta(FontWeight.Medium),
        headlineMedium = base.headlineMedium.jakarta(FontWeight.Medium),
        headlineSmall = base.headlineSmall.jakarta(FontWeight.Medium),
        titleLarge = base.titleLarge.jakarta(FontWeight.Medium),
        titleMedium = base.titleMedium.jakarta(FontWeight.Medium),
        titleSmall = base.titleSmall.jakarta(FontWeight.Medium),
        bodyLarge = base.bodyLarge.jakarta(),
        bodyMedium = base.bodyMedium.jakarta(),
        bodySmall = base.bodySmall.jakarta(),
        labelLarge = base.labelLarge.jakarta(FontWeight.Medium),
        labelMedium = base.labelMedium.jakarta(FontWeight.Medium),
        labelSmall = base.labelSmall.jakarta(FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode by context.phoneApp.theme.collectAsState()
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    // Status/navigation bar icons must follow the app's theme, not the system's.
    val activity = context as? ComponentActivity
    LaunchedEffect(dark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme else lightScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}

/** Flat card with a hairline border. */
@Composable
fun Modifier.csendCard(): Modifier = this
    .clip(MaterialTheme.shapes.medium)
    .background(MaterialTheme.colorScheme.surfaceContainer)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)

/** The amber frame that marks the selected item (like the focused row on the TV). */
@Composable
fun Modifier.selectedFrame(selected: Boolean): Modifier {
    val shape = MaterialTheme.shapes.medium
    return this
        .clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
}

/** App mark: near-black tile, white landscape, small amber crown (same as the launcher icon). */
@Composable
fun BrandMark(size: Dp = 40.dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(Brand.Night0)) {
        val w = this.size.width
        val h = this.size.height
        drawPath(
            Path().apply {
                moveTo(w * 0.375f, h * 0.44f); lineTo(w * 0.375f, h * 0.26f); lineTo(w * 0.444f, h * 0.333f)
                lineTo(w * 0.5f, h * 0.22f); lineTo(w * 0.556f, h * 0.333f); lineTo(w * 0.625f, h * 0.26f)
                lineTo(w * 0.625f, h * 0.44f); close()
            },
            Brand.Amber,
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.18f, h * 0.73f); lineTo(w * 0.38f, h * 0.52f); lineTo(w * 0.5f, h * 0.65f)
                lineTo(w * 0.6f, h * 0.56f); lineTo(w * 0.82f, h * 0.73f); close()
            },
            Brand.TextHigh,
        )
    }
}

/** The one primary action per screen: a solid amber pill. */
@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .alpha(if (enabled) 1f else 0.4f)
            .background(Brand.Amber)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Brand.OnAmber)
    }
}

/** Spaced-out caps label above a group, as on the TV. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** Icon circle + title + subtitle: the phone's version of the TV settings row. */
@Composable
fun IconRow(icon: ImageVector, title: String, subtitle: String? = null, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

/** Numbered how-to line with an outlined amber number. */
@Composable
fun StepRow(n: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Number with a caption for the stats row. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.csendCard().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light), color = accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small coloured dot for status lines. */
@Composable
fun Dot(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
