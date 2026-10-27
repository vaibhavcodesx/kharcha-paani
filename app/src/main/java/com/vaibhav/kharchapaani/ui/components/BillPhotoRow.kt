package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/** Where a new bill photo comes from. Neither needs a permission: the system camera and picker do. */
enum class BillPhotoSource { Camera, Gallery }

/**
 * The add sheet's stringResource(R.string.bill_photo_copy_label) row (docs/designs/bill-photos.md): Add when there's none, a thumbnail
 * that opens the photo full screen when there is, and the states in between.
 */
@Composable
fun BillPhotoRow(
    photo: BillPhoto,
    onAdd: (BillPhotoSource) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by remember { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf(false) }

    SheetRow(label = stringResource(R.string.bill_photo_copy_label), position = RowPosition.Single, modifier = modifier.testTag("bill_photo_row")) {
        when (photo) {
            BillPhoto.None -> Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bill_photo_copy_add_description)) { choosing = true }
                        .padding(vertical = Spacing.s)
                        .testTag("bill_photo_add"),
                ) {
                    Text(stringResource(R.string.bill_photo_copy_add), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                    Text(stringResource(R.string.bill_photo_copy_optional), style = MaterialTheme.typography.bodySmall, color = SubMutedWarmGray)
                }
                SourceMenu(expanded = choosing, onDismiss = { choosing = false }, onPick = { choosing = false; onAdd(it) })
            }
            BillPhoto.Processing -> {
                Box(Modifier.size(Thumbnail).clip(KpShapes.small).background(CharcoalElevated).shimmerLoading())
                Text(stringResource(R.string.bill_photo_copy_saving), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.m).weight(1f))
            }
            is BillPhoto.Kept -> {
                AsyncImage(
                    model = photo.file,
                    contentDescription = stringResource(R.string.bill_photo_copy_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Thumbnail)
                        .clip(KpShapes.small)
                        .background(CharcoalElevated)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bill_photo_copy_open)) { viewing = true }
                        .testTag("bill_photo_thumbnail"),
                )
                Text(
                    stringResource(R.string.bill_photo_copy_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubMutedWarmGray,
                    modifier = Modifier.padding(start = Spacing.m).weight(1f),
                )
                RemoveButton(onRemove)
            }
            BillPhoto.Missing -> {
                Text(stringResource(R.string.bill_photo_copy_missing), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove, modifier = Modifier.testTag("bill_photo_forget")) {
                    Text(stringResource(R.string.bill_photo_copy_remove), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            }
        }
    }

    val kept = photo as? BillPhoto.Kept
    if (viewing && kept != null) {
        BillPhotoViewer(
            photo = kept,
            onDismiss = { viewing = false },
            onReplace = { source -> viewing = false; onAdd(source) },
            onRemove = { viewing = false; onRemove() },
        )
    }
}

@Composable
private fun SourceMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (BillPhotoSource) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = CharcoalElevated, shape = KpShapes.medium) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bill_photo_copy_take), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText) },
            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MutedWarmGray) },
            onClick = { onPick(BillPhotoSource.Camera) },
            modifier = Modifier.testTag("bill_photo_take"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bill_photo_copy_choose), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText) },
            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MutedWarmGray) },
            onClick = { onPick(BillPhotoSource.Gallery) },
            modifier = Modifier.testTag("bill_photo_choose"),
        )
    }
}

@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    val removeDescription = stringResource(R.string.bill_photo_copy_remove_description)
    IconButton(
        onClick = onRemove,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = removeDescription }
            .testTag("bill_photo_remove"),
    ) {
        Icon(KpIcons.Close, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(18.dp))
    }
}

/** The photo full screen, fitted first; pinch or double-tap to zoom. */
@Composable
fun BillPhotoViewer(
    photo: BillPhoto.Kept,
    onDismiss: () -> Unit,
    onReplace: (BillPhotoSource) -> Unit,
    onRemove: () -> Unit,
) {
    val closeDescription = stringResource(R.string.bill_photo_copy_close)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Column(Modifier.fillMaxSize().background(Color.Black).testTag("bill_photo_viewer")) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.s), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = closeDescription }) {
                    Icon(KpIcons.Close, contentDescription = null, tint = OffWhiteText)
                }
            }
            AsyncImage(
                model = photo.file,
                contentDescription = stringResource(R.string.bill_photo_copy_label),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                            offset = Offset.Zero
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().background(CharcoalCard).navigationBarsPadding().padding(vertical = Spacing.s),
            ) {
                TextButton(onClick = { onReplace(BillPhotoSource.Camera) }) {
                    Text(stringResource(R.string.bill_photo_copy_take_new), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                }
                TextButton(onClick = { onReplace(BillPhotoSource.Gallery) }) {
                    Text(stringResource(R.string.bill_photo_copy_another), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                }
                Spacer(Modifier.width(Spacing.s))
                TextButton(onClick = onRemove, modifier = Modifier.testTag("bill_photo_viewer_remove")) {
                    Text(stringResource(R.string.bill_photo_copy_remove), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            }
        }
    }
}

private val Thumbnail = 56.dp
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
