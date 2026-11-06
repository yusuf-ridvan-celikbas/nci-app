package com.cansin.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cansin.app.R
import com.cansin.app.domain.PictureRules
import com.cansin.app.system.PictureStore
import com.cansin.app.ui.theme.PictureColors
import kotlinx.coroutines.launch

/** Provided by MainActivity; null in previews. */
val LocalPictures = staticCompositionLocalOf<PictureStore?> { null }

/** The picture saved under [key], reloaded whenever pictures change; null if none. */
@Composable
fun rememberPicture(key: String?): ImageBitmap? {
    val store = LocalPictures.current ?: return null
    val version by store.version.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, key, version) {
        value = key?.let { store.load(it)?.asImageBitmap() }
    }
    return bitmap
}

/** Fills a (clipped) +1 with the picture and a dark layer so the label on top stays readable. */
@Composable
fun PictureFill(picture: ImageBitmap) {
    Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(PictureColors.scrim))
}

/**
 * Photo Picker (no permission) → move & zoom → [onPicked] with the 384 px square.
 * Returns the function that starts it.
 */
@Composable
fun rememberPicturePicker(onPicked: suspend (Bitmap) -> Unit): () -> Unit {
    val store = LocalPictures.current
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && store != null) scope.launch { source = store.decode(resolver, uri) }
    }
    source?.let { bitmap ->
        PictureCropDialog(
            source = bitmap,
            onSave = { cropped ->
                source = null
                scope.launch { onPicked(cropped) }
            },
            onDismiss = { source = null },
        )
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Pinch / drag (or the slider) to place the photo in the circle. */
@Composable
fun PictureCropDialog(source: Bitmap, onSave: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val store = LocalPictures.current ?: return
    val image = remember(source) { source.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Circle size in px, set while laying out (not state: it only follows the screen size).
    val diameterPx = remember { floatArrayOf(1f) }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)
    val ring = MaterialTheme.colorScheme.primary
    val ringWidth = with(LocalDensity.current) { 2.dp.toPx() }
    fun clamp(o: Offset): Offset = PictureRules.clampOffset(source.width, source.height, diameterPx[0], zoom, o.x, o.y).let { Offset(it.first, it.second) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.picture_crop_title), style = MaterialTheme.typography.titleLarge)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
                    val density = LocalDensity.current
                    val diameter = with(density) { minOf(maxWidth, maxHeight).toPx() } * 0.85f
                    diameterPx[0] = diameter
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(source) {
                                detectTransformGestures { _, pan, change, _ ->
                                    zoom = PictureRules.clampZoom(zoom * change)
                                    offset = clamp(offset + pan)
                                }
                            },
                    ) {
                        val k = PictureRules.coverScale(source.width, source.height, diameter) * zoom
                        withTransform({
                            translate(center.x + offset.x, center.y + offset.y)
                            scale(k, k, Offset.Zero)
                            translate(-source.width / 2f, -source.height / 2f)
                        }) { drawImage(image) }
                        // Dim everything outside the circle (even-odd path), leaving the photo inside untouched.
                        val r = diameter / 2
                        val frame = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
                        }
                        drawPath(frame, scrim)
                        drawCircle(ring, diameter / 2, center, style = Stroke(ringWidth))
                    }
                }
                Slider(
                    value = zoom,
                    onValueChange = { zoom = PictureRules.clampZoom(it); offset = clamp(offset) },
                    valueRange = 1f..PictureRules.MAX_ZOOM,
                )
                Text(
                    stringResource(R.string.picture_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val square = PictureRules.crop(source.width, source.height, diameterPx[0], zoom, offset.x, offset.y)
                        onSave(store.crop(source, square))
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }
}

/** Avatar + "Choose picture" / "Change" + "Remove": Settings and the setup dialogs. */
@Composable
fun PictureRow(picture: ImageBitmap?, label: String, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            picture?.let { Image(it, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        OutlinedButton(onClick = onPick) {
            Text(stringResource(if (picture == null) R.string.picture_choose else R.string.picture_change))
        }
        if (picture != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.picture_remove)) }
    }
}
