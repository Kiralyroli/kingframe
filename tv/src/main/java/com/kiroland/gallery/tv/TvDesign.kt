package com.kiroland.gallery.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiroland.gallery.shared.Brand

fun tvText(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Brand.TextHigh) =
    TextStyle(fontFamily = Brand.Font, fontSize = size, fontWeight = weight, color = color)

/** Small spaced-out caps label above a group of settings. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = tvText(11.sp, FontWeight.SemiBold, Brand.TextFaint).copy(letterSpacing = 1.8.sp),
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
    )
}

/**
 * One settings row. When focused it gets the amber frame; if it has [onPrevious] /
 * [onNext], the remote's left/right keys change the value in place and the value is
 * shown between ‹ › so that is discoverable.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    accentIcon: Boolean = false,
    danger: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val adjustable = onPrevious != null && onNext != null
    val shape = RoundedCornerShape(10.dp)
    val bg by animateColorAsState(if (focused) Brand.Night2 else Color.Transparent, label = "rowBg")
    val frame by animateColorAsState(
        when {
            !focused -> Color.Transparent
            danger -> Brand.Danger
            else -> Brand.Amber
        },
        label = "rowFrame",
    )

    Row(
        modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { e ->
                if (!adjustable || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onPrevious?.invoke(); true }
                    Key.DirectionRight -> { onNext?.invoke(); true }
                    else -> false
                }
            }
            .clip(shape)
            .background(bg)
            .border(1.dp, frame, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (focused) Brand.Night3 else Brand.NightIcon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint = when {
                    danger && focused -> Brand.Danger
                    focused || accentIcon -> Brand.Amber
                    else -> Brand.TextMid
                },
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = tvText(16.sp, FontWeight.Medium, if (danger && focused) Brand.Danger else Brand.TextHigh), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = tvText(12.5.sp, color = if (focused) Brand.TextMid else Brand.TextLow), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        value?.let {
            Spacer(Modifier.width(12.dp))
            if (focused && adjustable) {
                Text(
                    "‹  $it  ›",
                    style = tvText(14.sp, FontWeight.Medium),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Night3).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                Text(it, style = tvText(14.sp, color = Brand.TextMid), modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

/** Pairing code as six digits on underlined slots, split 3 + 3 for easy reading. */
@Composable
fun PinSlots(pin: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        pin.forEachIndexed { i, digit ->
            if (i == 3) Spacer(Modifier.width(20.dp)) else if (i > 0) Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(digit.toString(), style = tvText(44.sp, FontWeight.Light))
                Box(Modifier.width(30.dp).height(2.dp).background(Brand.NightLine))
            }
        }
    }
}

/** "● Fogadásra kész · 4 kép" – a coloured dot and quiet text. */
@Composable
fun StatusLine(dot: Color, parts: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(parts.joinToString("  ·  "), style = tvText(13.sp, color = Brand.TextMid))
    }
}
