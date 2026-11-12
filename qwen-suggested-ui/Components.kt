package com.sarim.trinity.ui.qwen_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.sarim.trinity.ui.qwen_theme.QwenPalette

/**
 * Wide-tracked monospace label. Used for metadata, section headers, and UI chrome.
 * Automatically uppercases text for that technical/blueprint feel.
 */
@Composable
fun ChromeLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = QwenPalette.Muted,
    bold: Boolean = false,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Architectural section divider.
 * Use `heavy = true` for major section breaks (e.g., under the header).
 * Use `indent = true` for list item separators (starts after the avatar column).
 */
@Composable
fun Rule(heavy: Boolean = false, indent: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 92.dp else 0.dp)
            .height(if (heavy) 3.dp else 1.dp)
            .background(if (heavy) QwenPalette.Ink else QwenPalette.LineSoft)
    )
}

/**
 * Sharp, rectangular avatar. No circles.
 * Unread/active states invert the colors to Ink.
 */
@Composable
fun ArchitectAvatar(initials: String, active: Boolean, size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(if (active) QwenPalette.Ink else QwenPalette.Wash),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.uppercase(),
            color = if (active) QwenPalette.Paper else QwenPalette.Ink,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.45f).sp,
        )
    }
}

/**
 * Editorial Screen Header.
 * Large serif title with a heavy 3dp ink rule underneath.
 */
@Composable
fun EditorialHeader(
    title: String,
    meta: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        color = QwenPalette.Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChromeLabel(text = meta, color = QwenPalette.Muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (actionLabel != null && onAction != null) {
                        ArchitectButton(label = actionLabel, onClick = onAction, filled = false)
                        Spacer(Modifier.width(12.dp))
                    }
                    if (onSettings != null) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = QwenPalette.Ink,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onSettings() }
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
        Rule(heavy = true)
    }
}

/**
 * Sharp, rectangular button. No pills, no rounded corners.
 */
@Composable
fun ArchitectButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (filled) QwenPalette.Ink else QwenPalette.Paper
    val textColor = if (filled) QwenPalette.Paper else QwenPalette.Ink
    val borderColor = if (filled) QwenPalette.Ink else QwenPalette.Line

    Box(
        modifier = modifier
            .height(40.dp)
            .border(1.dp, borderColor)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChromeLabel(text = label, color = textColor, bold = true)
    }
}

/**
 * A text link with an underline, used as an alternative to small buttons.
 */
@Composable
fun TextLink(text: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(4.dp)
    ) {
        ChromeLabel(text = text, color = QwenPalette.Ink, bold = true)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(QwenPalette.Ink))
    }
}

@Composable
fun NoticeBar(
    text: String,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(QwenPalette.Wash)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = QwenPalette.Ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = QwenPalette.Ink,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextLink(text = actionLabel, onClick = { onClick?.invoke() })
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = QwenPalette.Muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Serif,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
    }
}
