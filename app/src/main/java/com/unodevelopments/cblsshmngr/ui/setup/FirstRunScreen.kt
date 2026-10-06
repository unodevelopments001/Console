package com.unodevelopments.cblsshmngr.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.ui.theme.LaunchLayout
import com.unodevelopments.cblsshmngr.ui.theme.TabStyle
import com.unodevelopments.cblsshmngr.ui.theme.ThemeId

private const val STEPS = 4

@Composable
fun FirstRunScreen(onFinished: () -> Unit) {
    val store = (LocalContext.current.applicationContext as CabalApp).themeStore
    var step by remember { mutableIntStateOf(0) }
    var agreed by remember { mutableStateOf(false) }
    BackHandler(enabled = step > 0) { step -= 1 }

    Scaffold { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        StepDots(step = step, count = STEPS)
        Text(
            text = stringResource(R.string.setup_step, step + 1, STEPS),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> WelcomeStep()
                1 -> TermsCopy()
                2 -> ThemeStep()
                else -> LockStep()
            }
        }
        if (step == 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text(stringResource(R.string.terms_agree), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val finish: (Boolean) -> Unit = { enableLock ->
            store.completeOnboarding()
            if (enableLock) store.setAppLock(true)
            onFinished()
        }
        Button(
            onClick = {
                when (step) {
                    0, 2 -> step += 1
                    1 -> if (agreed) step += 1
                    else -> finish(true)
                }
            },
            enabled = step != 1 || agreed,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(
                stringResource(
                    when (step) {
                        0 -> R.string.get_started
                        1 -> R.string.terms_accept
                        2 -> R.string.setup_next
                        else -> R.string.app_lock_enable
                    },
                ),
            )
        }
        if (step == 3) {
            TextButton(onClick = { finish(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_lock_skip))
            }
        }
    }
    }
}

@Composable
private fun WelcomeStep() {
    Text(stringResource(R.string.first_run_title), style = MaterialTheme.typography.headlineLarge)
    Text(
        stringResource(R.string.welcome_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.welcome_from),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun TermsCopy() {
    Text(stringResource(R.string.terms_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.terms_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeStep() {
    val store = (LocalContext.current.applicationContext as CabalApp).themeStore
    val id by store.id.collectAsStateWithLifecycle()
    val layout by store.layout.collectAsStateWithLifecycle()
    val tabs by store.tabs.collectAsStateWithLifecycle()
    Text(stringResource(R.string.setup_theme_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.setup_theme_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChipRow(
        options = ThemeId.entries.map { it to themeName(it) },
        selected = id,
        onSelect = store::select,
    )
    Text(stringResource(R.string.theme_layout), style = MaterialTheme.typography.titleMedium)
    ChipRow(
        options = listOf(
            LaunchLayout.PANELS to stringResource(R.string.theme_panels),
            LaunchLayout.TABS to stringResource(R.string.theme_tabs),
            LaunchLayout.LIST to stringResource(R.string.theme_list),
        ),
        selected = layout,
        onSelect = store::setLayout,
    )
    Text(stringResource(R.string.theme_tab_style), style = MaterialTheme.typography.titleMedium)
    ChipRow(
        options = listOf(
            TabStyle.UNDERLINE to stringResource(R.string.theme_underline),
            TabStyle.PILLS to stringResource(R.string.theme_pills),
        ),
        selected = tabs,
        onSelect = store::setTabs,
    )
}

@Composable
private fun LockStep() {
    Text(stringResource(R.string.setup_lock_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.setup_lock_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun themeName(id: ThemeId): String = stringResource(
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
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun StepDots(step: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val on = index == step
            Box(
                modifier = Modifier
                    .size(if (on) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }
    }
}
