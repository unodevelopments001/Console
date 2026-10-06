package com.unodevelopments.cblsshmngr.ui.database

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.unodevelopments.cblsshmngr.sql.toCsv
import com.unodevelopments.cblsshmngr.ui.theme.AppTabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.R
import com.unodevelopments.cblsshmngr.sql.ColumnInfo
import com.unodevelopments.cblsshmngr.sql.DbObject
import com.unodevelopments.cblsshmngr.sql.QueryResult
import com.unodevelopments.cblsshmngr.sql.TableData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSessionScreen(connectionId: Long, onDone: () -> Unit) {
    val app = LocalContext.current.applicationContext as CabalApp
    val viewModel: DatabaseSessionViewModel = viewModel(
        factory = DatabaseSessionViewModel.factory(app, app.sqlRepository, connectionId),
    )
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val leave = {
        viewModel.disconnect()
        onDone()
    }
    BackHandler {
        if (!viewModel.back()) leave()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = { Text(screenTitle(screen, title)) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = { SessionActions(screen, viewModel) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (val current = phase) {
                DbPhase.Connecting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DbPhase.Failed -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(current.message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (current.canRetry) {
                        Button(
                            onClick = viewModel::reconnect,
                            modifier = Modifier.padding(top = 20.dp),
                        ) { Text(stringResource(R.string.reconnect)) }
                    }
                }
                DbPhase.Ready -> SessionBody(screen, viewModel)
            }
        }
    }
}

@Composable
private fun screenTitle(screen: DbScreen, fallback: String): String {
    return when (screen) {
        DbScreen.Databases -> stringResource(R.string.databases)
        is DbScreen.Objects -> screen.database
        is DbScreen.Table -> screen.name
        is DbScreen.Procedure -> screen.name
        is DbScreen.Query -> stringResource(R.string.query)
    }.ifBlank { fallback }
}

@Composable
private fun SessionActions(screen: DbScreen, viewModel: DatabaseSessionViewModel) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    when (screen) {
        is DbScreen.Objects -> TextButton(onClick = viewModel::openQuery) { Text(stringResource(R.string.query)) }
        is DbScreen.Table -> {
            TextButton(onClick = viewModel::openQuery) { Text(stringResource(R.string.query)) }
            if (!screen.view) {
                TextButton(onClick = { confirm = Confirm.Truncate(screen.name) }) { Text(stringResource(R.string.truncate)) }
            }
            TextButton(onClick = { confirm = Confirm.DropTable(screen.name, screen.view) }) {
                Text(stringResource(R.string.drop))
            }
        }
        is DbScreen.Procedure -> {
            TextButton(onClick = viewModel::saveProcedure) { Text(stringResource(R.string.save)) }
            TextButton(onClick = { confirm = Confirm.DropProcedure(screen.name, screen.function) }) {
                Text(stringResource(R.string.drop))
            }
        }
        is DbScreen.Query -> TextButton(onClick = viewModel::runQuery) { Text(stringResource(R.string.run)) }
        DbScreen.Databases -> Unit
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title()) },
            text = { Text(action.body()) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        is Confirm.Truncate -> viewModel.truncateTable()
                        is Confirm.DropTable -> viewModel.dropTable()
                        is Confirm.DropProcedure -> viewModel.dropProcedure()
                    }
                }) { Text(action.actionLabel()) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SessionBody(screen: DbScreen, viewModel: DatabaseSessionViewModel) {
    when (screen) {
        DbScreen.Databases -> DatabaseList(viewModel)
        is DbScreen.Objects -> ObjectList(viewModel)
        is DbScreen.Table -> TablePane(viewModel)
        is DbScreen.Procedure -> ProcedurePane(viewModel)
        is DbScreen.Query -> QueryPane(viewModel)
    }
}

@Composable
private fun DatabaseList(viewModel: DatabaseSessionViewModel) {
    val databases by viewModel.databases.collectAsStateWithLifecycle()
    var dropName by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(databases, key = { it }) { name ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { viewModel.openDatabase(name) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(name, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { dropName = name }) { Text(stringResource(R.string.drop)) }
            }
        }
    }
    dropName?.let { name ->
        AlertDialog(
            onDismissRequest = { dropName = null },
            title = { Text(stringResource(R.string.drop_db_title, name)) },
            text = { Text(stringResource(R.string.drop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    dropName = null
                    viewModel.dropDatabase(name)
                }) { Text(stringResource(R.string.drop)) }
            },
            dismissButton = {
                TextButton(onClick = { dropName = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectList(viewModel: DatabaseSessionViewModel) {
    val tables by viewModel.tables.collectAsStateWithLifecycle()
    val views by viewModel.views.collectAsStateWithLifecycle()
    val procedures by viewModel.procedures.collectAsStateWithLifecycle()
    val functions by viewModel.functions.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var drop by remember { mutableStateOf<Pair<DbObjectKind, DbObject>?>(null) }
    val kinds = listOf(DbObjectKind.Table, DbObjectKind.View, DbObjectKind.Procedure, DbObjectKind.Function)
    val labels = listOf(
        stringResource(R.string.tables),
        stringResource(R.string.views),
        stringResource(R.string.procedures),
        stringResource(R.string.functions),
    )
    val empty = listOf(R.string.no_tables, R.string.no_views, R.string.no_procedures, R.string.no_functions)
    val lists = listOf(tables, views, procedures, functions)
    Column(Modifier.fillMaxSize()) {
        AppTabRow(
            labels = labels,
            selected = tab,
            onSelect = { tab = it },
        )
        val items = lists[tab]
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(empty[tab]),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.label }) { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                when (kinds[tab]) {
                                    DbObjectKind.Table -> viewModel.openTable(item)
                                    DbObjectKind.View -> viewModel.openView(item)
                                    DbObjectKind.Procedure -> viewModel.openProcedure(item)
                                    DbObjectKind.Function -> viewModel.openFunction(item)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(item.label, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { drop = kinds[tab] to item }) {
                            Text(stringResource(R.string.drop))
                        }
                    }
                }
            }
        }
    }
    drop?.let { (kind, item) ->
        val title = when (kind) {
            DbObjectKind.Table -> R.string.drop_table_title
            DbObjectKind.View -> R.string.drop_view_title
            DbObjectKind.Procedure -> R.string.drop_proc_title
            DbObjectKind.Function -> R.string.drop_function_title
        }
        AlertDialog(
            onDismissRequest = { drop = null },
            title = { Text(stringResource(title, item.name)) },
            text = { Text(stringResource(R.string.drop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    drop = null
                    viewModel.dropListed(kind, item)
                }) { Text(stringResource(R.string.drop)) }
            },
            dismissButton = {
                TextButton(onClick = { drop = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private sealed interface Confirm {
    data class Truncate(val name: String) : Confirm
    data class DropTable(val name: String, val view: Boolean) : Confirm
    data class DropProcedure(val name: String, val function: Boolean) : Confirm
}

@Composable
private fun Confirm.title(): String = when (this) {
    is Confirm.Truncate -> stringResource(R.string.truncate_title, name)
    is Confirm.DropTable -> stringResource(if (view) R.string.drop_view_title else R.string.drop_table_title, name)
    is Confirm.DropProcedure -> stringResource(
        if (function) R.string.drop_function_title else R.string.drop_proc_title,
        name,
    )
}

@Composable
private fun Confirm.body(): String = when (this) {
    is Confirm.Truncate -> stringResource(R.string.truncate_body)
    else -> stringResource(R.string.drop_body)
}

@Composable
private fun Confirm.actionLabel(): String = when (this) {
    is Confirm.Truncate -> stringResource(R.string.truncate)
    else -> stringResource(R.string.drop)
}

@Composable
private fun TablePane(viewModel: DatabaseSessionViewModel) {
    val data by viewModel.table.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val readOnly = (screen as? DbScreen.Table)?.view == true
    val objectName = (screen as? DbScreen.Table)?.name ?: "table"
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val table = data ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        writeCsv(context, uri, table.columns.map { it.name }, table.rows)
        viewModel.report(context.getString(R.string.exported))
    }
    var editing by remember { mutableStateOf<List<String?>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<String?>?>(null) }
    val table = data
    if (table == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!readOnly) {
                TextButton(onClick = { creating = true }) { Text(stringResource(R.string.add_row)) }
                if (table.primaryKeys.isEmpty()) {
                    Text(
                        stringResource(R.string.no_primary_key),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextButton(onClick = { exporter.launch("$objectName.csv") }) {
                Text(stringResource(R.string.export_csv))
            }
        }
        val horizontal = rememberScrollState()
        val vertical = rememberScrollState()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(vertical)
                .horizontalScroll(horizontal),
        ) {
            Row {
                table.columns.forEach { column ->
                    Text(
                        column.name,
                        modifier = Modifier.width(160.dp).padding(8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            table.rows.forEach { row ->
                Row {
                    row.forEach { cell ->
                        TextButton(
                            onClick = { if (!readOnly) editing = row },
                            enabled = !readOnly,
                            modifier = Modifier.width(160.dp),
                        ) {
                            Text(
                                cell ?: stringResource(R.string.null_value),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (!readOnly) {
                        TextButton(onClick = { pendingDelete = row }) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        RowEditor(
            columns = table.columns,
            original = if (creating) null else editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { values ->
                viewModel.saveRow(if (creating) null else editing, values)
                creating = false
                editing = null
            },
        )
    }
    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_row_title)) },
            text = { Text(stringResource(R.string.drop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteRow(row)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RowEditor(
    columns: List<ColumnInfo>,
    original: List<String?>?,
    onDismiss: () -> Unit,
    onSave: (List<String?>) -> Unit,
) {
    val values = remember(original) {
        columns.indices.map { index -> mutableStateOf(original?.getOrNull(index).orEmpty()) }.toMutableList()
    }
    val nulls = remember(original) {
        columns.indices.map { index -> mutableStateOf(original != null && original.getOrNull(index) == null) }.toMutableList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.add_row else R.string.edit)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                columns.forEachIndexed { index, column ->
                    OutlinedTextField(
                        value = if (nulls[index].value) "" else values[index].value,
                        onValueChange = {
                            values[index].value = it
                            nulls[index].value = false
                        },
                        enabled = !column.identity && !nulls[index].value,
                        label = { Text(column.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!column.identity) {
                        TextButton(onClick = { nulls[index].value = !nulls[index].value }) {
                            Text(stringResource(if (nulls[index].value) R.string.clear_null else R.string.set_null))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(columns.indices.map { index -> if (nulls[index].value) null else values[index].value })
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ProcedurePane(viewModel: DatabaseSessionViewModel) {
    val script by viewModel.script.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = script,
        onValueChange = viewModel::updateScript,
        modifier = Modifier.fillMaxSize().padding(12.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun QueryPane(viewModel: DatabaseSessionViewModel) {
    val sql by viewModel.querySql.collectAsStateWithLifecycle()
    val result by viewModel.queryResult.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val grid = result as? QueryResult.Grid ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        writeCsv(context, uri, grid.columns, grid.rows)
        viewModel.report(context.getString(R.string.exported))
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (history.isNotEmpty()) {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { item ->
                    TextButton(onClick = { viewModel.useHistory(item.sql) }) {
                        Text(
                            item.sql.replace('\n', ' '),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = sql,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier.fillMaxWidth().weight(0.4f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::runQuery) { Text(stringResource(R.string.run)) }
            if (result is QueryResult.Grid) {
                TextButton(onClick = { exporter.launch("query.csv") }) {
                    Text(stringResource(R.string.export_csv))
                }
            }
        }
        when (val current = result) {
            null -> Unit
            is QueryResult.Affected -> Text(stringResource(R.string.rows_affected, current.count))
            is QueryResult.Grid -> ResultGrid(current)
        }
    }
}

private fun writeCsv(
    context: android.content.Context,
    uri: Uri,
    columns: List<String>,
    rows: List<List<String?>>,
) {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(toCsv(columns, rows).toByteArray(Charsets.UTF_8))
    }
}

@Composable
private fun ColumnScope.ResultGrid(grid: QueryResult.Grid) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(vertical)
            .horizontalScroll(horizontal),
    ) {
        Row {
            grid.columns.forEach { name ->
                Text(name, modifier = Modifier.width(160.dp).padding(8.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        grid.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        cell ?: stringResource(R.string.null_value),
                        modifier = Modifier.width(160.dp).padding(8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
