@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dparadox.paradoxuploader.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dparadox.paradoxuploader.R
import com.dparadox.paradoxuploader.domain.model.Folder
import com.dparadox.paradoxuploader.domain.model.FolderMeta
import com.dparadox.paradoxuploader.domain.util.Licenses
import com.dparadox.paradoxuploader.ui.components.FieldShape

/**
 * Creates a folder ([initial] == null) or edits one. The title, description, tags and license
 * are set once here and used for every file uploaded into the folder.
 */
@Composable
fun FolderEditorDialog(
    initial: Folder?,
    onDismiss: () -> Unit,
    onSave: (name: String, meta: FolderMeta) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(initial?.tags.orEmpty()) }
    var license by rememberSaveable { mutableStateOf(initial?.licenseUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (initial == null) Icons.Rounded.CreateNewFolder else Icons.Rounded.Edit, contentDescription = null) },
        title = { Text(stringResource(if (initial == null) R.string.folder_new else R.string.folder_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.folder_defaults_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    stringResource(R.string.folder_defaults_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.folder_title_label)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.folder_description_label)) },
                    minLines = 2,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.folder_tags_label)) },
                    supportingText = { Text(stringResource(R.string.tags_hint)) },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.folder_license_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = license.isEmpty(),
                        onClick = { license = "" },
                        label = { Text(stringResource(R.string.license_none)) }
                    )
                    Licenses.ALL.forEach { option ->
                        FilterChip(
                            selected = license == option.url,
                            onClick = { license = option.url },
                            label = { Text(option.label) }
                        )
                    }
                }
                if (initial != null) {
                    Text(
                        stringResource(R.string.folder_meta_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        name,
                        FolderMeta(
                            title = title.ifBlank { null },
                            description = description.ifBlank { null },
                            tags = tags.ifBlank { null },
                            licenseUrl = license.ifBlank { null }
                        )
                    )
                }
            ) { Text(stringResource(if (initial == null) R.string.folder_create else R.string.folder_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
