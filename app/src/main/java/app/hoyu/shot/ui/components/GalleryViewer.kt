package app.hoyu.shot.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hoyu.shot.model.CapturedPhotoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryViewer(
    photos: List<CapturedPhotoInfo>,
    initialIndex: Int = 0,
    rotationDegrees: Float = 0f,
    onDismiss: () -> Unit,
    onDeletePhoto: (CapturedPhotoInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.size - 1),
        pageCount = { photos.size }
    )

    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()
    var isUiChromeVisible by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(2.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Horizontal Pager for smooth swipe left/right photo browsing
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            ZoomablePhotoItem(
                photo = photo,
                onToggleChrome = { isUiChromeVisible = !isUiChromeVisible },
                onSwipeRightAtEdge = {
                    if (page == 0) {
                        onDismiss()
                    }
                }
            )
        }

        // Top ProShot-style EXIF Bar (Adaptive to phone rotation)
        AnimatedVisibility(
            visible = isUiChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD909090B)) // shadcn zinc-950 translucent
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Top Row: Back button and EXIF Specs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Camera",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationDegrees)
                        )
                    }

                    // EXIF parameters in ProShot format:
                    // MP | Resolution | Aperture | ISO | Shutter | File Size
                    val mp = String.format(Locale.US, "%.1f MP", (currentPhoto.width * currentPhoto.height) / 1_000_000f)
                    val res = "${currentPhoto.width}x${currentPhoto.height}"
                    val aperture = currentPhoto.aperture.ifEmpty { "f/1.8" }
                    val isoStr = "ISO ${currentPhoto.iso}"
                    val shutterStr = currentPhoto.shutterSpeed
                    val sizeMb = if (currentPhoto.fileSizeBytes > 0) {
                        String.format(Locale.US, "%.1f MB", currentPhoto.fileSizeBytes / (1024f * 1024f))
                    } else "3.8 MB"

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.rotate(rotationDegrees)
                    ) {
                        ExifSpecItem(mp)
                        ExifSpecItem(res)
                        ExifSpecItem(aperture)
                        ExifSpecItem(isoStr)
                        ExifSpecItem(shutterStr)
                        ExifSpecItem(sizeMb)
                    }
                }

                // Subline: Timestamp date
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(currentPhoto.timestamp))
                Text(
                    text = dateStr,
                    color = Color(0xFF71717A),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(start = 42.dp, top = 2.dp)
                        .rotate(rotationDegrees)
                )
            }
        }

        // Bottom ProShot-style Action Bar
        AnimatedVisibility(
            visible = isUiChromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD909090B))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom-Left: Filename Badge
                val name = currentPhoto.fileName.ifEmpty {
                    val parts = currentPhoto.uriString.split("/")
                    parts.lastOrNull() ?: "IMG_HoyuShot.jpg"
                }
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0xFF27272A), shape)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = name,
                        color = Color(0xFFFAFAFA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.rotate(rotationDegrees)
                    )
                }

                // Bottom-Right: Share & Delete buttons (Clean shadcn style)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button
                    IconButton(
                        onClick = { sharePhoto(context, currentPhoto.uriString) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(shape)
                            .background(Color(0x33FFFFFF))
                            .border(1.dp, Color(0xFF27272A), shape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotationDegrees)
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = { onDeletePhoto(currentPhoto) },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(shape)
                            .background(Color(0x33E11D48))
                            .border(1.dp, Color(0xFFE11D48), shape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF4D6D),
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(rotationDegrees)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifSpecItem(text: String) {
    Text(
        text = text,
        color = Color(0xFFFAFAFA),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun ZoomablePhotoItem(
    photo: CapturedPhotoInfo,
    onToggleChrome: () -> Unit,
    onSwipeRightAtEdge: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(photo.uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(photo.uriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 1
                    }
                    bitmap = BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1f) {
                        val maxPanX = (newScale - 1f) * size.width / 2f
                        val maxPanY = (newScale - 1f) * size.height / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                            (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                        )
                    } else {
                        offset = Offset.Zero
                        // If dragging right while not zoomed in at edge, return to camera
                        if (pan.x > 35f) {
                            onSwipeRightAtEdge()
                        }
                    }
                }
            }
            .clickable { onToggleChrome() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Photo Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                color = Color(0xFFE11D48),
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun sharePhoto(context: Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
