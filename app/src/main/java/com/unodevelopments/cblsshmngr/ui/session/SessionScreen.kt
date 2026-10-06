package com.unodevelopments.cblsshmngr.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unodevelopments.cblsshmngr.data.SavedCommand
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.ui.files.FileBrowserPane
import com.unodevelopments.cblsshmngr.ui.files.FileEditorPane
import com.unodevelopments.cblsshmngr.ui.terminal.TerminalPane
import com.unodevelopments.cblsshmngr.ui.theme.AppTabRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(connectionId: Long, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as CabalApp
    val viewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(app, app.repository, connectionId),
    )
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val fingerprint by viewModel.pendingFingerprint.collectAsStateWithLifecycle()
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val browser by viewModel.browser.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val dropped by viewModel.dropped.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var commandsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbar.showSnackbar(message) }
    }

    val leave = {
        viewModel.disconnect()
        onDone()
    }
    BackHandler(enabled = editor == null, onBack = leave)

    Box(modifier = Modifier.fillMaxSize()) {
    if (editor != null) {
        FileEditorPane(
            state = editor!!,
            onDraft = viewModel::updateDraft,
            onSave = viewModel::saveEditor,
            onClose = viewModel::closeEditor,
        )
    } else Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(title.ifBlank { stringResource(R.string.app_name) }) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.disconnect),
                        )
                    }
                },
                actions = {
                    if (phase is SessionPhase.Connected && tab == 0) {
                        TextButton(onClick = { commandsOpen = true }) {
                            Text(stringResource(R.string.commands))
                        }
                    }
                    if (phase is SessionPhase.Connected) {
                        TextButton(onClick = leave) { Text(stringResource(R.string.disconnect)) }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = phase) {
            SessionPhase.Connecting -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.connecting))
                }
            }
            is SessionPhase.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (current.canRetry) {
                    Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
                    Spacer(Modifier.height(8.dp))
                }
                if (current.canForgetHostKey) {
                    Button(onClick = viewModel::forgetHostKeyAndRetry) {
                        Text(stringResource(R.string.forget_host_key))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = leave) { Text(stringResource(R.string.back)) }
            }
            SessionPhase.Connected -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                AppTabRow(
                    labels = listOf(stringResource(R.string.terminal), stringResource(R.string.files)),
                    selected = tab,
                    onSelect = { tab = it },
                )
                if (tab == 0) {
                    if (dropped) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.session_dropped),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = viewModel::retry) {
                                Text(stringResource(R.string.reconnect))
                            }
                        }
                    }
                    TerminalPane(
                        text = terminal,
                        status = status,
                        onSend = viewModel::send,
                        onResize = viewModel::resize,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FileBrowserPane(
                        state = browser,
                        onOpen = viewModel::openEntry,
                        onUp = viewModel::goUp,
                        onRefresh = viewModel::refreshFiles,
                        onUpload = viewModel::upload,
                        onDownload = viewModel::download,
                        onDelete = viewModel::delete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
        )
    }

    fingerprint?.let { value ->
        AlertDialog(
            onDismissRequest = { viewModel.onTrustDecision(false) },
            title = { Text(stringResource(R.string.trust_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.trust_body))
                    Text(value, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onTrustDecision(true) }) {
                    Text(stringResource(R.string.trust))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onTrustDecision(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (commandsOpen) {
        CommandsDialog(
            commands = commands,
            onSend = {
                viewModel.sendSaved(it)
                commandsOpen = false
            },
            onAdd = viewModel::addCommand,
            onDelete = viewModel::deleteCommand,
            onDismiss = { commandsOpen = false },
        )
    }
}

@Composable
private fun CommandsDialog(
    commands: List<SavedCommand>,
    onSend: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_commands)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (commands.isEmpty()) {
                    Text(
                        stringResource(R.string.no_commands),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(commands, key = { it.id }) { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { onSend(item.command) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        item.label,
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.command_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.command_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(label, command)
                    label = ""
                    command = ""
                },
                enabled = label.isNotBlank() && command.isNotBlank(),
            ) { Text(stringResource(R.string.add_command)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
