package com.unodevelopments.cblsshmngr.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R

private val backgrounds = listOf(
    Color(0xFFF7F8FA),
    Color(0xFFF3EFE6),
    Color(0xFF12141A),
    Color(0xFF050805),
    Color(0xFF070B18),
    Color(0xFF1A120C),
)
private val primaries = listOf(
    Color(0xFF1565C0),
    Color(0xFF1E6B45),
    Color(0xFFE6E8EE),
    Color(0xFF39FF14),
    Color(0xFF5CE1FF),
    Color(0xFFE07A3D),
)
private val accents = listOf(
    Color(0xFF455A64),
    Color(0xFF243E68),
    Color(0xFFB7C6FF),
    Color(0xFFFF4D8D),
    Color(0xFF7CFF6B),
    Color(0xFFE6B325),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val store = (LocalContext.current.applicationContext as CabalApp).themeStore
    val id by store.id.collectAsStateWithLifecycle()
    val layout by store.layout.collectAsStateWithLifecycle()
    val tabs by store.tabs.collectAsStateWithLifecycle()
    val custom by store.custom.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(stringResource(R.string.themes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ChoiceRow(
                options = ThemeId.entries.map { it to themeLabel(it) },
                selected = id,
                onSelect = store::select,
            )
            Text(stringResource(R.string.theme_layout), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                options = listOf(
                    LaunchLayout.PANELS to stringResource(R.string.theme_panels),
                    LaunchLayout.TABS to stringResource(R.string.theme_tabs),
                    LaunchLayout.LIST to stringResource(R.string.theme_list),
                ),
                selected = layout,
                onSelect = store::setLayout,
            )
            Text(stringResource(R.string.theme_tab_style), style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                options = listOf(
                    TabStyle.UNDERLINE to stringResource(R.string.theme_underline),
                    TabStyle.PILLS to stringResource(R.string.theme_pills),
                ),
                selected = tabs,
                onSelect = store::setTabs,
            )
            Text(stringResource(R.string.theme_background), style = MaterialTheme.typography.titleMedium)
            Swatches(backgrounds, Color(custom.background)) { store.setCustom(background = it.toArgb()) }
            Text(stringResource(R.string.theme_primary), style = MaterialTheme.typography.titleMedium)
            Swatches(primaries, Color(custom.primary)) { store.setCustom(primary = it.toArgb()) }
            Text(stringResource(R.string.theme_accent), style = MaterialTheme.typography.titleMedium)
            Swatches(accents, Color(custom.accent)) { store.setCustom(accent = it.toArgb()) }
        }
    }
}

@Composable
private fun themeLabel(id: ThemeId): String = stringResource(
    when (id) {
        ThemeId.LIGHT -> R.string.theme_light
        ThemeId.DARK -> R.string.theme_dark
        ThemeId.HACKER -> R.string.theme_hacker
        ThemeId.SCIFI -> R.string.theme_scifi
        ThemeId.CONSOLE -> R.string.theme_console
        ThemeId.CUSTOM -> R.string.theme_custom
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Text(
                text = label,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Swatches(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            val on = color.toArgb() == selected.toArgb()
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (on) 3.dp else 1.dp,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onPick(color) },
            )
        }
    }
}
