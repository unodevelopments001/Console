package com.unodevelopments.cblsshmngr.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.ssh.RemoteFile
import com.unodevelopments.cblsshmngr.ui.session.BrowserState
import com.unodevelopments.cblsshmngr.ui.session.parentPath

@Composable
fun FileBrowserPane(
    state: BrowserState,
    onOpen: (RemoteFile) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onUpload: (android.net.Uri) -> Unit,
    onDownload: (RemoteFile, android.net.Uri) -> Unit,
    onDelete: (RemoteFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onUpload(uri)
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val entry = pendingDownload
        pendingDownload = null
        if (uri != null && entry != null) onDownload(entry, uri)
    }
    val canGoUp = parentPath(state.path) != null

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onUp, enabled = canGoUp) {
                Text(stringResource(R.string.up))
            }
            Text(
                text = state.path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.upload))
            }
        }
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!state.loading && state.entries.isEmpty() && state.path.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_directory),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.path }) { entry ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = !state.loading) { onOpen(entry) },
                        leadingContent = {
                            Icon(
                                imageVector = if (entry.directory) {
                                    Icons.Filled.Folder
                                } else {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                contentDescription = null,
                            )
                        },
                        headlineContent = {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = if (entry.directory) {
                            null
                        } else {
                            { Text(formatSize(entry.size)) }
                        },
                        trailingContent = {
                            Row {
                                if (!entry.directory) {
                                    IconButton(onClick = {
                                        pendingDownload = entry
                                        saver.launch(entry.name)
                                    }) {
                                        Icon(
                                            Icons.Outlined.Download,
                                            contentDescription = stringResource(R.string.download),
                                        )
                                    }
                                }
                                IconButton(onClick = { pendingDelete = entry }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_remote_title, entry.name)) },
            text = { Text(stringResource(R.string.delete_remote_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kilobytes = bytes / 1024.0
    if (kilobytes < 1024) return "%.1f KB".format(kilobytes)
    val megabytes = kilobytes / 1024.0
    if (megabytes < 1024) return "%.1f MB".format(megabytes)
    return "%.1f GB".format(megabytes / 1024.0)
}
