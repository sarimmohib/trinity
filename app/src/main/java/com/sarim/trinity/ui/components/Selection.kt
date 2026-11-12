package com.sarim.trinity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a long press turns on: a set of picked rows, keyed by a stable id so the
 * selection survives a refresh that rebuilds the list around it.
 *
 * The mode is the selection itself — clearing the last row leaves it, which is
 * what a back gesture and the bar's CANCEL both do.
 */
@Stable
class Selection<K>(private val keyOf: (K) -> Long) {

    var ids by mutableStateOf<Set<Long>>(emptySet())
        private set

    val active: Boolean get() = ids.isNotEmpty()

    val count: Int get() = ids.size

    fun contains(item: K): Boolean = keyOf(item) in ids

    /** Long press: begin here, with the pressed row already picked. */
    fun start(item: K) {
        if (!active) ids = setOf(keyOf(item))
    }

    fun toggle(item: K) {
        val key = keyOf(item)
        ids = if (key in ids) ids - key else ids + key
    }

    fun pickAll(items: List<K>) {
        ids = ids + items.map(keyOf)
    }

    fun clear() {
        ids = emptySet()
    }
}

/** One bar action: a glass block with a word on it. */
data class SelectionAction(
    val label: String,
    val onClick: () -> Unit,
)

/** The trailing tick a picked row shows in place of its usual affordance. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(
                1.dp,
                if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * The bar that replaces a tab's bottom action while rows are picked. It floats
 * over the list like every other surface: count and CANCEL on top, actions as
 * glass blocks that scroll sideways when there are more than a thumb can reach.
 */
@Composable
fun SelectionBar(
    count: Int,
    actions: List<SelectionAction>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            // Dense enough to read over a busy list, still see-through.
            .glassBlock(scheme.primary, 24.dp, 2.4f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (count == 1) "1 selected" else "$count selected",
                color = scheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            MonoLabel(
                uiLabel("CANCEL"),
                color = scheme.onSurface,
                size = 8,
                modifier = Modifier
                    .clickable { onClear() }
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .height(34.dp)
                        .glassBlock(scheme.secondaryContainer, 17.dp, 2f)
                        .clickable { action.onClick() }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoLabel(uiLabel(action.label), color = scheme.onSurface, size = 8)
                }
            }
        }
    }
}
