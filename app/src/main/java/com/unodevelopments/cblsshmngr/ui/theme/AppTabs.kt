package com.unodevelopments.cblsshmngr.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTabRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalLook.current
    if (look.tabStyle == TabStyle.PILLS) {
        Row(
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                Text(
                    text = label,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(look.corner))
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    } else {
        PrimaryTabRow(selectedTabIndex = selected, modifier = modifier) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    text = { Text(label) },
                )
            }
        }
    }
}
