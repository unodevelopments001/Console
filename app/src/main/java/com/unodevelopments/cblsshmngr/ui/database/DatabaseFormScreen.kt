package com.unodevelopments.cblsshmngr.ui.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseFormScreen(connectionId: Long?, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as CabalApp
    val viewModel: DatabaseFormViewModel = viewModel(
        factory = DatabaseFormViewModel.factory(app.sqlRepository, connectionId),
    )
    val sshConnections by viewModel.sshConnections.collectAsStateWithLifecycle()
    var pickingSsh by remember { mutableStateOf(false) }
    val messages = DatabaseFormMessages(
        nickname = stringResource(R.string.error_nickname),
        ssh = stringResource(R.string.error_ssh_required),
        port = stringResource(R.string.error_port),
        username = stringResource(R.string.error_username),
        password = stringResource(R.string.error_password),
        generic = stringResource(R.string.error_generic),
    )
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Text(
                        stringResource(
                            if (viewModel.editing) R.string.edit_database else R.string.new_database,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sshConnections.isEmpty()) {
                Text(stringResource(R.string.no_ssh_yet))
            } else {
                OutlinedTextField(
                    value = viewModel.sshLabel(sshConnections),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.ssh_connection)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { pickingSsh = true }) {
                    Text(stringResource(R.string.choose_ssh))
                }
            }
            OutlinedTextField(
                value = viewModel.sqlHost,
                onValueChange = { viewModel.sqlHost = it },
                label = { Text(stringResource(R.string.sql_host)) },
                supportingText = { Text(stringResource(R.string.sql_host_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.sqlPort,
                onValueChange = { viewModel.sqlPort = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text(stringResource(R.string.port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.username,
                onValueChange = { viewModel.username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = if (viewModel.editing) {
                    { Text(stringResource(R.string.password_keep)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.error?.let { Text(it) }
            Button(
                onClick = { viewModel.save(messages) },
                enabled = !viewModel.saving && sshConnections.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (pickingSsh) {
        AlertDialog(
            onDismissRequest = { pickingSsh = false },
            title = { Text(stringResource(R.string.choose_ssh)) },
            text = {
                Column {
                    sshConnections.forEach { connection ->
                        TextButton(onClick = {
                            viewModel.sshConnectionId = connection.id
                            pickingSsh = false
                        }) { Text(connection.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingSsh = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
