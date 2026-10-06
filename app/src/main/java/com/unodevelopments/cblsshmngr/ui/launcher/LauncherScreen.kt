package com.unodevelopments.cblsshmngr.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.ui.theme.AppTabRow
import com.unodevelopments.cblsshmngr.ui.theme.LaunchLayout
import com.unodevelopments.cblsshmngr.ui.theme.LocalLook

@Composable
fun LauncherScreen(
    onSsh: () -> Unit,
    onDatabase: () -> Unit,
    onThemes: () -> Unit,
    onInfo: () -> Unit,
) {
    val look = LocalLook.current
    val app = LocalContext.current.applicationContext as CabalApp
    val appLock by app.themeStore.appLock.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                )
                Text(
                    text = stringResource(R.string.launcher_tagline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.themes)) },
                        onClick = {
                            menuOpen = false
                            onThemes()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.info)) },
                        onClick = {
                            menuOpen = false
                            onInfo()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (appLock) R.string.app_lock_disable else R.string.app_lock_enable))
                        },
                        onClick = {
                            menuOpen = false
                            app.themeStore.setAppLock(!appLock)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        when (look.layout) {
            LaunchLayout.PANELS -> {
                DestinationPanel(
                    title = stringResource(R.string.ssh),
                    detail = stringResource(R.string.launcher_ssh_detail),
                    icon = Icons.Outlined.Computer,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = onSsh,
                )
                Spacer(Modifier.height(14.dp))
                DestinationPanel(
                    title = stringResource(R.string.database),
                    detail = stringResource(R.string.launcher_database_detail),
                    icon = Icons.Outlined.Storage,
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onDatabase,
                )
            }
            LaunchLayout.TABS -> TabLaunch(onSsh, onDatabase, Modifier.weight(1f))
            LaunchLayout.LIST -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ListEntry(stringResource(R.string.ssh), stringResource(R.string.launcher_ssh_detail), Icons.Outlined.Computer, onSsh)
                ListEntry(stringResource(R.string.database), stringResource(R.string.launcher_database_detail), Icons.Outlined.Storage, onDatabase)
            }
        }
    }
}

@Composable
private fun TabLaunch(onSsh: () -> Unit, onDatabase: () -> Unit, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier) {
        AppTabRow(
            labels = listOf(stringResource(R.string.ssh), stringResource(R.string.database)),
            selected = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(16.dp))
        if (tab == 0) {
            DestinationPanel(
                title = stringResource(R.string.ssh),
                detail = stringResource(R.string.launcher_ssh_detail),
                icon = Icons.Outlined.Computer,
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onSsh,
            )
        } else {
            DestinationPanel(
                title = stringResource(R.string.database),
                detail = stringResource(R.string.launcher_database_detail),
                icon = Icons.Outlined.Storage,
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onDatabase,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (tab == 0) onSsh() else onDatabase() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.theme_open))
        }
    }
}

@Composable
private fun ListEntry(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationPanel(
    title: String,
    detail: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val look = LocalLook.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val onFill = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
    val background = if (look.solidPanels) {
        if (pressed) fill.copy(alpha = 0.86f) else fill
    } else {
        if (pressed) fill.copy(alpha = 0.18f) else fill.copy(alpha = 0.12f)
    }
    val foreground = if (look.solidPanels) onFill else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(look.corner))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(foreground.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(26.dp))
        }
        Column {
            Text(title, color = foreground, fontWeight = FontWeight.Medium, fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = foreground.copy(alpha = 0.78f), fontSize = 15.sp)
        }
    }
}
