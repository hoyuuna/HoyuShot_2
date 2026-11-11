package app.hoyu.shot.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import app.hoyu.shot.model.CapturedPhotoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuickThumbnail(
    photoInfo: CapturedPhotoInfo?,
    rotationDegrees: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(photoInfo?.uriString) { mutableStateOf<Bitmap?>(null) }
    val shape = RoundedCornerShape(2.dp)

    LaunchedEffect(photoInfo?.uriString) {
        val uriStr = photoInfo?.uriString ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriStr)
                val exifRotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f

                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
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
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Fallback for DNG / RAW files without a BitmapFactory-decodable preview
                    bitmap = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Box(
        modifier = modifier
            .size(52.dp)
            .rotate(rotationDegrees)
            .clip(shape)
            .background(Color(0x33000000))
            .border(1.dp, Color(0xFF27272A), shape)
            .clickable(enabled = photoInfo != null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Last Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (photoInfo?.isRaw == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xFFE11D48), shape)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "RAW",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, Color(0xFF52525B), shape)
            )
        }
    }
}
