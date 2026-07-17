package com.smartisan.music.ui.components

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class MediaStoreDeleteItem(
    val mediaId: String,
    val uri: Uri,
)

internal class MediaStoreDeleteCoordinator internal constructor(
    private val requestDelete: (List<MediaStoreDeleteItem>) -> Unit,
) {
    fun delete(items: List<MediaStoreDeleteItem>) {
        requestDelete(items)
    }
}

@Composable
internal fun rememberMediaStoreDeleteCoordinator(
    onDeleted: (Set<String>) -> Unit,
    onNotDeleted: (Set<String>) -> Unit,
): MediaStoreDeleteCoordinator {
    val context = LocalContext.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentOnNotDeleted by rememberUpdatedState(onNotDeleted)
    var pendingRequest by remember { mutableStateOf<PendingDeleteRequest?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }
    var confirmationMode by remember { mutableStateOf<DeleteConfirmationMode?>(null) }

    fun finishPendingRequest(request: PendingDeleteRequest, failedIds: Set<String> = emptySet()) {
        pendingRequest = null
        confirmationMode = null
        if (request.deletedIds.isNotEmpty()) {
            currentOnDeleted(request.deletedIds)
        }
        val notDeletedIds = request.items.mapTo(linkedSetOf(), MediaStoreDeleteItem::mediaId) + failedIds
        if (notDeletedIds.isNotEmpty()) {
            currentOnNotDeleted(notDeletedIds)
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingRequest ?: return@rememberLauncherForActivityResult
        if (granted) {
            retryToken += 1
        } else {
            finishPendingRequest(request)
        }
    }
    val confirmationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val request = pendingRequest ?: return@rememberLauncherForActivityResult
        when {
            result.resultCode != Activity.RESULT_OK -> finishPendingRequest(request)
            confirmationMode == DeleteConfirmationMode.SystemDeletesBatch -> {
                pendingRequest = null
                confirmationMode = null
                currentOnDeleted(
                    request.deletedIds + request.items.map(MediaStoreDeleteItem::mediaId),
                )
            }
            else -> retryToken += 1
        }
    }

    LaunchedEffect(retryToken) {
        val request = pendingRequest ?: return@LaunchedEffect
        when (val step = withContext(Dispatchers.IO) { context.nextDeleteStep(request) }) {
            is DeleteStep.Completed -> {
                pendingRequest = null
                confirmationMode = null
                val allDeletedIds = request.deletedIds + step.deletedIds
                if (allDeletedIds.isNotEmpty()) {
                    currentOnDeleted(allDeletedIds)
                }
                if (step.failedIds.isNotEmpty()) {
                    currentOnNotDeleted(step.failedIds)
                }
            }
            DeleteStep.RequestLegacyWritePermission -> {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            is DeleteStep.RequestConfirmation -> {
                pendingRequest = request.copy(
                    items = step.remainingItems,
                    deletedIds = request.deletedIds + step.deletedIds,
                )
                confirmationMode = step.mode
                runCatching {
                    confirmationLauncher.launch(
                        IntentSenderRequest.Builder(step.intentSender).build(),
                    )
                }.onFailure {
                    pendingRequest?.let(::finishPendingRequest)
                }
            }
        }
    }

    return remember {
        MediaStoreDeleteCoordinator { items ->
            val normalizedItems = items
                .filter { item -> item.mediaId.isNotBlank() }
                .distinctBy(MediaStoreDeleteItem::mediaId)
            if (normalizedItems.isNotEmpty()) {
                pendingRequest = PendingDeleteRequest(items = normalizedItems)
                retryToken += 1
            }
        }
    }
}

private data class PendingDeleteRequest(
    val items: List<MediaStoreDeleteItem>,
    val deletedIds: Set<String> = emptySet(),
)

private sealed interface DeleteStep {
    data class Completed(
        val deletedIds: Set<String>,
        val failedIds: Set<String>,
    ) : DeleteStep

    data object RequestLegacyWritePermission : DeleteStep

    data class RequestConfirmation(
        val intentSender: IntentSender,
        val remainingItems: List<MediaStoreDeleteItem>,
        val deletedIds: Set<String>,
        val mode: DeleteConfirmationMode,
    ) : DeleteStep
}

private enum class DeleteConfirmationMode {
    GrantsSingleItem,
    SystemDeletesBatch,
}

private fun Context.nextDeleteStep(request: PendingDeleteRequest): DeleteStep {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Api30.createDeleteRequest(this, request.items)
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Api29.deleteOrRequestAccess(this, request.items)
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED -> DeleteStep.RequestLegacyWritePermission
        else -> deleteDirectly(request.items)
    }
}

private fun Context.deleteDirectly(items: List<MediaStoreDeleteItem>): DeleteStep.Completed {
    val deletedIds = linkedSetOf<String>()
    val failedIds = linkedSetOf<String>()
    items.forEach { item ->
        runCatching {
            contentResolver.delete(item.uri, null, null)
        }.onSuccess {
            deletedIds += item.mediaId
        }.onFailure {
            failedIds += item.mediaId
        }
    }
    return DeleteStep.Completed(deletedIds, failedIds)
}

@RequiresApi(Build.VERSION_CODES.Q)
private object Api29 {
    fun deleteOrRequestAccess(context: Context, items: List<MediaStoreDeleteItem>): DeleteStep {
        val deletedIds = linkedSetOf<String>()
        items.forEachIndexed { index, item ->
            try {
                context.contentResolver.delete(item.uri, null, null)
                deletedIds += item.mediaId
            } catch (error: RecoverableSecurityException) {
                return DeleteStep.RequestConfirmation(
                    intentSender = error.userAction.actionIntent.intentSender,
                    remainingItems = items.drop(index),
                    deletedIds = deletedIds,
                    mode = DeleteConfirmationMode.GrantsSingleItem,
                )
            } catch (_: SecurityException) {
                return DeleteStep.Completed(
                    deletedIds = deletedIds,
                    failedIds = items.drop(index).mapTo(linkedSetOf(), MediaStoreDeleteItem::mediaId),
                )
            }
        }
        return DeleteStep.Completed(deletedIds, emptySet())
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private object Api30 {
    fun createDeleteRequest(context: Context, items: List<MediaStoreDeleteItem>): DeleteStep {
        return runCatching {
            MediaStore.createDeleteRequest(
                context.contentResolver,
                items.map(MediaStoreDeleteItem::uri),
            )
        }.fold(
            onSuccess = { pendingIntent ->
                DeleteStep.RequestConfirmation(
                    intentSender = pendingIntent.intentSender,
                    remainingItems = items,
                    deletedIds = emptySet(),
                    mode = DeleteConfirmationMode.SystemDeletesBatch,
                )
            },
            onFailure = {
                DeleteStep.Completed(
                    deletedIds = emptySet(),
                    failedIds = items.mapTo(linkedSetOf(), MediaStoreDeleteItem::mediaId),
                )
            },
        )
    }
}
