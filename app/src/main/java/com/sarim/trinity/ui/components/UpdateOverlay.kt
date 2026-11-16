package com.sarim.trinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.Links
import com.sarim.trinity.data.Release
import com.sarim.trinity.data.UpdateChecker
import com.sarim.trinity.ui.theme.Palette

/**
 * Announces a newer GitHub release over whatever screen is up, with the release
 * notes (the changelog and fixes) in a scrolling block. Tapping outside is "later".
 */
@Composable
fun UpdateOverlay(
    release: Release,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val notes = remember(release.notes) { readableNotes(release.notes) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLater() }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Paper, RoundedCornerShape(10.dp))
                .border(1.dp, Palette.Ink, RoundedCornerShape(10.dp))
                // Swallow taps so only the scrim outside the card dismisses.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            MonoLabel("UPDATE AVAILABLE", size = 9)
            Spacer(Modifier.height(6.dp))
            Text(
                "Version ${release.version}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
            )
            Spacer(Modifier.height(2.dp))
            MonoLabel("YOU HAVE ${UpdateChecker.current}", size = 8, color = Palette.Faint)
            Spacer(Modifier.height(14.dp))
            MonoLabel("WHAT'S NEW", size = 8, color = Palette.Faint)
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    notes.ifBlank { "No release notes were written for this version." },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Palette.Ink,
                )
            }
            Spacer(Modifier.height(18.dp))
            OverlayButton(
                if (release.apkUrl != null) "DOWNLOAD UPDATE" else "VIEW RELEASE",
                filled = true,
            ) { Links.open(context, release.apkUrl ?: release.pageUrl) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton("LATER", filled = false, modifier = Modifier.weight(1f)) { onLater() }
                OverlayButton("SKIP THIS VERSION", filled = false, modifier = Modifier.weight(1f)) { onSkip() }
            }
        }
    }
}

@Composable
private fun OverlayButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (filled) Palette.Ink else Palette.Bone, RoundedCornerShape(5.dp))
            .border(1.dp, if (filled) Palette.Ink else Palette.Line, RoundedCornerShape(5.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(label, color = if (filled) Palette.Paper else Palette.Ink, size = 9)
    }
}

/** GitHub release notes are Markdown; this keeps them readable without rendering it. */
private fun readableNotes(raw: String): String = raw.lines().joinToString("\n") { line ->
    val trimmed = line.trim()
    when {
        trimmed.startsWith("#") -> trimmed.trimStart('#').trim().uppercase()
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "•  " + trimmed.drop(2)
        else -> trimmed
    }.replace("**", "").replace("`", "")
}.replace(Regex("\n{3,}"), "\n\n").trim()
