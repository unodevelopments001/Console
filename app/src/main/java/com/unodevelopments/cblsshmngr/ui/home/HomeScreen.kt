package com.unodevelopments.cblsshmngr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.data.SshConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onConnect: (Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as CabalApp
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(app.repository))
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SshConnection?>(null) }
    var menuConnection by remember { mutableStateOf<SshConnection?>(null) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(stringResource(R.string.ssh)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_connection),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            when (val saved = connections) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                emptyList<SshConnection>() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val shown = saved.filter { connection ->
                        query.isBlank() ||
                            connection.name.contains(query, ignoreCase = true) ||
                            connection.host.contains(query, ignoreCase = true) ||
                            connection.username.contains(query, ignoreCase = true)
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search)) },
                    )
                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.no_matches),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { connection ->
                        ConnectionTile(
                            connection = connection,
                            onConnect = { onConnect(connection.id) },
                            onLongPress = { menuConnection = connection },
                        )
                    }
                }
            }
        }
        }
    }

    menuConnection?.let { connection ->
        AlertDialog(
            onDismissRequest = { menuConnection = null },
            title = { Text(connection.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            menuConnection = null
                            onEdit(connection.id)
                        },
                    ) { Text(stringResource(R.string.edit)) }
                    TextButton(
                        onClick = {
                            menuConnection = null
                            pendingDelete = connection
                        },
                    ) { Text(stringResource(R.string.delete)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuConnection = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_connection_title)) },
            text = { Text(stringResource(R.string.delete_connection_body, connection.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(connection.id)
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

@Composable
private fun ConnectionTile(
    connection: SshConnection,
    onConnect: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onConnect, onLongClick = onLongPress)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Computer,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = connection.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
