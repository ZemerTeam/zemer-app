package com.jtech.zemer.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtech.zemer.LocalDatabase
import com.jtech.zemer.R
import com.jtech.zemer.db.entities.PlaylistFolderEntity
import com.jtech.zemer.db.entities.PlaylistFolderWithCount
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementScreen(
    onNavigateBack: () -> Unit,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val folders by database.allFoldersWithCount().collectAsState(initial = emptyList())
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<PlaylistFolderWithCount?>(null) }
    var deletingFolder by remember { mutableStateOf<PlaylistFolderWithCount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlist_folders)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = stringResource(R.string.create_folder),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (folders.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.storage),
                            contentDescription = null,
                            modifier = Modifier.height(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_playlist_folders),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(folders, key = { it.id }) { folder ->
                FolderListItem(
                    folder = folder,
                    onRename = { editingFolder = folder },
                    onDelete = { deletingFolder = folder },
                )
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = stringResource(R.string.create_folder),
            initial = "",
            onConfirm = { name ->
                coroutineScope.launch {
                    database.query {
                        upsertFolder(
                            PlaylistFolderEntity(
                                name = name,
                                position = folders.size,
                            )
                        )
                    }
                }
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingFolder?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.rename_folder),
            initial = folder.name,
            onConfirm = { name ->
                coroutineScope.launch {
                    database.query {
                        upsertFolder(
                            PlaylistFolderEntity(
                                id = folder.id,
                                name = name,
                                position = folder.position,
                                createdAt = folder.createdAt,
                            )
                        )
                    }
                }
                editingFolder = null
            },
            onDismiss = { editingFolder = null },
        )
    }

    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.delete_folder)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_folder_confirm,
                        folder.name,
                        folder.playlistCount,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        database.query {
                            deleteFolder(folder.id)
                        }
                    }
                    deletingFolder = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderListItem(
    folder: PlaylistFolderWithCount,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.storage),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = pluralStringResource(R.plurals.n_playlists, folder.playlistCount, folder.playlistCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRename) {
            Icon(
                painter = painterResource(R.drawable.edit),
                contentDescription = stringResource(R.string.rename_folder),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
