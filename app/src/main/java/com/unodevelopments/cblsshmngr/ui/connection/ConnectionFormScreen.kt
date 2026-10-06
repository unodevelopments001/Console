package com.unodevelopments.cblsshmngr.ui.connection

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionFormScreen(connectionId: Long?, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as CabalApp
    val viewModel: ConnectionFormViewModel = viewModel(
        factory = ConnectionFormViewModel.factory(app.repository, connectionId),
    )
    val messages = FormMessages(
        nickname = stringResource(R.string.error_nickname),
        host = stringResource(R.string.error_host),
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
                            if (viewModel.editing) R.string.edit_connection else R.string.new_connection,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
            OutlinedTextField(
                value = viewModel.host,
                onValueChange = { viewModel.host = it },
                label = { Text(stringResource(R.string.host)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.port,
                onValueChange = { viewModel.port = it.filter { ch -> ch.isDigit() }.take(5) },
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
            viewModel.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { viewModel.save(messages) },
                enabled = !viewModel.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
