package app.hoyu.shot.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import app.hoyu.shot.model.CapturedPhotoInfo
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryViewer(
    photos: List<CapturedPhotoInfo>,
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

    // Reverse photo list so:
    // Page 0 = Oldest photo
    // Page (orderedPhotos.size - 1) = Newest photo
    // Page orderedPhotos.size = Phantom exit page (swiping right-to-left past newest photo returns to camera)
    val orderedPhotos = remember(photos) { photos.reversed() }

    val pagerState = rememberPagerState(
        initialPage = (orderedPhotos.size - 1).coerceAtLeast(0),
        pageCount = { orderedPhotos.size + 1 }
    )

    // Detect swipe past newest photo into phantom page and smoothly exit
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == orderedPhotos.size) {
            onDismiss()
        }
    }

    // Ensure we start on the newest photo when opening
    LaunchedEffect(photos) {
        if (orderedPhotos.isNotEmpty()) {
            pagerState.scrollToPage(orderedPhotos.size - 1)
        }
    }

    val currentPhotoIndex = pagerState.currentPage.coerceAtMost(orderedPhotos.size - 1)
    val currentPhoto = orderedPhotos.getOrNull(currentPhotoIndex) ?: orderedPhotos.last()
    var isUiChromeVisible by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(2.dp)

    // Dynamic rotation: In landscape (90 or 270), swap dimensions so the entire viewer layout
    // (top bar, bottom bar, photo) is oriented naturally to the user's holding direction!
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        val isLandscape = rotationDegrees == 90f || rotationDegrees == 270f
        val viewportWidth = if (isLandscape) maxHeight else maxWidth
        val viewportHeight = if (isLandscape) maxWidth else maxHeight

        Box(
            modifier = Modifier
                .requiredSize(viewportWidth, viewportHeight)
                .align(Alignment.Center)
                .rotate(rotationDegrees)
        ) {
            // Horizontal Pager for smooth swipe left/right photo browsing
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (page < orderedPhotos.size) {
                    val photo = orderedPhotos[page]
                    ZoomablePhotoItem(
                        photo = photo,
                        onToggleChrome = { isUiChromeVisible = !isUiChromeVisible }
                    )
                } else {
                    // Phantom transparent exit page: reveals camera underneath
                    Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                }
            }

            // Top ProShot-style EXIF Bar: ALWAYS at the TOP of the user's rotated view
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
                        .then(if (rotationDegrees == 0f) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // Top Row: Back button, centered specs, and date/time below specs
                    val mp = String.format(Locale.US, "%.1f MP", (currentPhoto.width * currentPhoto.height) / 1_000_000f)
                    val res = "${currentPhoto.width}x${currentPhoto.height}"
                    val aperture = currentPhoto.aperture.ifEmpty { "f/1.8" }
                    val isoStr = "ISO ${currentPhoto.iso}"
                    val shutterStr = currentPhoto.shutterSpeed
                    val sizeMb = if (currentPhoto.fileSizeBytes > 0) {
                        String.format(Locale.US, "%.1f MB", currentPhoto.fileSizeBytes / (1024f * 1024f))
                    } else "3.8 MB"
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(currentPhoto.timestamp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back to Camera",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Centered column containing parameters and date aligned directly underneath
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExifSpecItem(mp)
                                ExifSpecItem(res)
                                ExifSpecItem(aperture)
                                ExifSpecItem(isoStr)
                                ExifSpecItem(shutterStr)
                                ExifSpecItem(sizeMb)
                            }
                            Text(
                                text = dateStr,
                                color = Color(0xFF71717A),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Spacer to balance back button so specs are centered
                        Spacer(modifier = Modifier.size(34.dp))
                    }
                }
            }

            // Bottom ProShot-style Action Bar: ALWAYS at the BOTTOM of the user's rotated view
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
                        .then(if (rotationDegrees == 0f) Modifier.navigationBarsPadding() else Modifier)
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
                            fontFamily = FontFamily.Monospace
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
                                modifier = Modifier.size(18.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(photo.uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.uriString) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(photo.uriString)
                val exifRotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                var sample = 1
                while (boundsOpts.outWidth / (sample * 2) >= 2560 || boundsOpts.outHeight / (sample * 2) >= 2560) {
                    sample *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }

                if (raw != null) {
                    bitmap = if (exifRotation != 0f) {
                        val matrix = Matrix().apply { postRotate(exifRotation) }
                        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                        if (rotated != raw) raw.recycle()
                        rotated
                    } else {
                        raw
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Double tap to zoom in / reset to 1x with smooth spring animation
                detectTapGestures(
                    onDoubleTap = {
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.1f) {
                                coroutineScope.launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                                coroutineScope.launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                coroutineScope.launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            } else {
                                coroutineScope.launch { scaleAnim.animateTo(2.5f, spring(stiffness = Spring.StiffnessMedium)) }
                                coroutineScope.launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                coroutineScope.launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            }
                        }
                    },
                    onTap = { onToggleChrome() }
                )
            }
            .pointerInput(Unit) {
                // 1:1 Instant finger-tracking pinch-to-zoom (zero spring delay)
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || scaleAnim.value > 1.05f) {
                        coroutineScope.launch {
                            val newScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                            scaleAnim.snapTo(newScale)
                            val maxPanX = (newScale - 1f) * size.width / 2f
                            val maxPanY = (newScale - 1f) * size.height / 2f
                            val newX = (offsetXAnim.value + pan.x).coerceIn(-maxPanX, maxPanX)
                            val newY = (offsetYAnim.value + pan.y).coerceIn(-maxPanY, maxPanY)
                            offsetXAnim.snapTo(newX)
                            offsetYAnim.snapTo(newY)
                        }
                    }
                }
            },
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
                        scaleX = scaleAnim.value,
                        scaleY = scaleAnim.value,
                        translationX = offsetXAnim.value,
                        translationY = offsetYAnim.value
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
