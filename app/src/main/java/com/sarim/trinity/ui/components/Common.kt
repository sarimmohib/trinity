package com.sarim.trinity.ui.components
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.SimCard
import com.sarim.trinity.ui.theme.Palette

/** Small tracked-out mono label — the app's connective tissue. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Palette.Muted,
    size: Int = 9,
    weight: FontWeight = FontWeight.Medium,
) {
    // Material You has no tracked-out mono captions: the same labels read as
    // ordinary small text, two points larger.
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = if (Palette.isMaterial) FontFamily.Default else FontFamily.Monospace,
        fontWeight = weight,
        fontSize = if (Palette.isMaterial) maxOf(size + 2, 11).sp else size.sp,
        letterSpacing = if (Palette.isMaterial) 0.sp else (size * 0.14f).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "NEW MESSAGE" reads as "New message" where Material does the talking. */
private fun sentence(text: String): String =
    if (text.any { it.isLowerCase() }) {
        text
    } else {
        text.lowercase().replaceFirstChar { it.titlecase() }
    }

/** Static labels: tracked caps on paper, sentence case where Material does the talking. */
@Composable
fun uiLabel(text: String): String = if (Palette.isMaterial) sentence(text) else text

/** 1px rule. There are no shadows in this app, only hairlines. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Line),
    )
}

/**
 * The Expressive surface: one rounded, faintly translucent block per thing you can
 * press, with a lit edge and a fine diagonal weave. Glass by texture, not gradient.
 */
fun Modifier.glassBlock(
    tint: Color,
    radius: Dp = 20.dp,
    strength: Float = 1f,
    texture: Boolean = true,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val alpha = ((if (Palette.isDark) 0.28f else 0.26f) * strength).coerceAtMost(0.9f)
    val weave = Color.White.copy(alpha = if (Palette.isDark) 0.05f else 0.34f)
    return this
        .clip(shape)
        .background(tint.copy(alpha = alpha))
        .then(
            if (texture) {
                // Frosted, not glossy: a diagonal weave at a hair's width apart.
                // Texture only — no gradient, which the look does not allow.
                Modifier.drawWithCache {
                    val step = 7.dp.toPx()
                    onDrawBehind {
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(
                                weave,
                                Offset(x, size.height),
                                Offset(x + size.height, 0f),
                                strokeWidth = 1f,
                            )
                            x += step
                        }
                    }
                }
            } else {
                Modifier
            },
        )
        .border(
            0.8.dp,
            Color.White.copy(alpha = if (Palette.isDark) 0.12f else 0.60f),
            shape,
        )
}

@Composable
fun Avatar(initials: String, filled: Boolean, size: Int = 40) {
    val scheme = MaterialTheme.colorScheme
    val material = Palette.isMaterial
    // Expressive gives each person a tint of their own, picked from the wallpaper's
    // roles; the other looks keep one flat tone.
    val accent = Palette.Accents[(initials.hashCode() and 0x7FFFFFFF) % Palette.Accents.size]
    val background = when {
        material && filled -> scheme.primary
        Palette.isExpressive -> accent
        material -> scheme.primaryContainer
        filled -> Palette.Ink
        else -> Palette.Wash
    }
    val foreground = when {
        material && filled -> scheme.onPrimary
        material -> scheme.onPrimaryContainer
        filled -> Palette.Paper
        else -> Palette.Ink
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size * 0.32f).sp,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    meta: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (actionLabel != null && onAction != null) {
                        Row(
                            modifier = Modifier
                                .height(26.dp)
                                .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                                .clickable { onAction() }
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoLabel(uiLabel(actionLabel), color = Palette.Ink)
                        }
                    }
                    if (onSettings != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                                .clickable { onSettings() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = Palette.Ink,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            MonoLabel(uiLabel(meta))
        }
        Hairline()
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        MonoLabel(text, color = Palette.Ink, size = 8)
    }
}

@Composable
fun RuleChip(text: String, count: Int, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .then(
                if (Palette.isMaterial) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                        .border(1.dp, Palette.Line, RoundedCornerShape(17.dp))
                        .background(Palette.Paper)
                },
            )
            .clickable { onRemove() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(text, color = Palette.Ink, size = 9)
        Spacer(Modifier.width(7.dp))
        MonoLabel(if (count > 0) count.toString() else "×", color = Palette.Faint, size = 8)
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = Palette.Ink,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            MonoLabel(label, color = tint)
        }
    }
}

/** One tappable row inside an ink sheet. */
data class SheetAction(
    val label: String,
    val hint: String? = null,
    val onClick: () -> Unit,
)

/**
 * The app's only menu: a bone page slid up over a wash scrim, actions separated
 * by hairlines. No elevation, no rounded colour — it reads as a printed slip.
 */
@Composable
fun InkSheet(
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
) {
    // A sheet is the top of the screen: back closes it, not the screen beneath.
    androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
    val material = Palette.isMaterial
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(
                if (material) scheme.scrim.copy(alpha = 0.32f) else Color(0x6617160F),
            )
            .clickable(onClick = onDismiss),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(
                    RoundedCornerShape(
                        topStart = if (material) 28.dp else 0.dp,
                        topEnd = if (material) 28.dp else 0.dp,
                    ),
                )
                .background(if (material) scheme.surfaceContainerLow.copy(alpha = 0.94f) else Palette.Bone)
                .clickable(enabled = false) {}
                .padding(bottom = if (material) 16.dp else 8.dp),
        ) {
            if (material) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            scheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (material) 14.dp else 18.dp,
                        bottom = 12.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (material) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    MonoLabel(title, color = Palette.Faint, size = 8)
                    MonoLabel("CLOSE", color = Palette.Ink, size = 8)
                }
            }
            if (!material) Hairline()
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(if (material) 56.dp else 52.dp)
                        .clickable {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        action.label,
                        fontSize = if (material) 15.sp else 14.sp,
                        color = Palette.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    action.hint?.let { MonoLabel(it, color = Palette.Faint, size = 8) }
                }
                if (!material) Hairline(Modifier.padding(start = 24.dp, end = 24.dp))
            }
        }
    }
}

/**
 * The primary action of a tab, docked at the bottom where a thumb already is.
 * Same shape and weight as the dial pad's CALL — one hand, no stretching.
 */
@Composable
fun BottomAction(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    if (Palette.isMaterial) {
        // The one primary action floats in the thumb's corner as a glass block, so
        // the page and the list keep showing through it instead of vanishing
        // behind a solid fill.
        val scheme = MaterialTheme.colorScheme
        // The caller's modifier carries the anchor (bottom-end over a list), so it
        // has to land on the outer row; the inner one only shapes the block.
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier
                .padding(end = 20.dp, bottom = 16.dp)
                // A touch denser than a list row so the label stays readable while
                // the rows still show through it.
                .glassBlock(if (enabled) scheme.primary else scheme.outline, 20.dp, 2.6f)
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                sentence(label),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
        }
        return
    }

    Column(Modifier.fillMaxWidth().background(Palette.Paper)) {
        Hairline()
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (enabled) Palette.Ink else Palette.Wash)
                    .border(
                        1.dp,
                        if (enabled) Palette.Ink else Palette.Line,
                        RoundedCornerShape(26.dp),
                    )
                    .clickable(enabled = enabled) { onClick() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (enabled) Palette.Paper else Palette.Faint,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                }
                MonoLabel(
                    label,
                    color = if (enabled) Palette.Paper else Palette.Faint,
                    size = 10,
                )
            }
            if (hint != null) {
                Spacer(Modifier.height(7.dp))
                MonoLabel(hint, color = Palette.Faint, size = 8)
            }
        }
    }
}

/** Outlined mono button: the default action shape on paper. */
@Composable
fun MonoButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (Palette.isMaterial) {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier = modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (filled && enabled) scheme.primary else scheme.surfaceContainerHighest,
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                sentence(label),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                    filled -> scheme.onPrimary
                    else -> scheme.onSurface
                },
            )
        }
        return
    }

    val ink = if (enabled) Palette.Ink else Palette.Faint
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (filled && enabled) Palette.Ink else Palette.Paper)
            .border(1.dp, if (filled && enabled) Palette.Ink else Palette.Line, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(
            label,
            color = if (filled && enabled) Palette.Paper else ink,
            size = 9,
        )
    }
}

/** The one action that ends a composer: round, filled, at the thumb's reach. */
@Composable
fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val material = Palette.isMaterial
    val background = when {
        material && enabled -> scheme.primary
        material -> scheme.surfaceContainerHighest
        enabled -> Palette.Ink
        else -> Palette.Wash
    }
    val foreground = when {
        material && enabled -> scheme.onPrimary
        material -> scheme.onSurfaceVariant
        enabled -> Palette.Paper
        else -> Palette.Faint
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (material) {
                    Modifier
                } else {
                    Modifier.border(
                        1.dp,
                        if (enabled) Palette.Ink else Palette.Line,
                        CircleShape,
                    )
                },
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = foreground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Which SIM a text leaves from, when the phone has more than one line. */
@Composable
fun SimChip(label: String, onClick: () -> Unit) {
    val material = Palette.isMaterial
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(if (material) 17.dp else 3.dp))
            .background(
                if (material) MaterialTheme.colorScheme.secondaryContainer else Palette.Wash,
            )
            .then(
                if (material) {
                    Modifier
                } else {
                    Modifier.border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                },
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(
            label,
            color = if (material) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                Palette.Ink
            },
            size = 8,
        )
    }
}

/** The SIM choice for outgoing texts, shown only when there is one to make. */
@Composable
fun SimSheet(
    cards: List<SimCard>,
    selected: Int?,
    defaultId: Int?,
    askEachTime: Boolean = false,
    onPick: (Int?) -> Unit,
    onAsk: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    InkSheet(
        title = "SEND FROM",
        actions = buildList {
            if (onAsk != null) {
                add(
                    SheetAction(
                        "Ask every time",
                        if (askEachTime) "ON" else null,
                    ) { onAsk() },
                )
            }
            add(
                SheetAction(
                    "System default",
                    when {
                        askEachTime -> null
                        selected == null && defaultId != null -> "DEFAULT"
                        selected == null -> "ON"
                        else -> null
                    },
                ) { onPick(null) },
            )
            cards.forEach { card ->
                add(
                    SheetAction(
                        card.label,
                        if (card.subscriptionId == selected) "ON" else null,
                    ) { onPick(card.subscriptionId) },
                )
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun EmptyNote(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = text,
            color = Palette.Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Start,
            lineHeight = 18.sp,
        )
    }
}
