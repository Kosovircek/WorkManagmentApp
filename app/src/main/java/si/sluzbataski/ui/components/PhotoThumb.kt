package si.sluzbataski.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.sluzbataski.photo.PhotoStorage

/** Naloži sliko v ozadju (ne blokira vmesnika). */
@Composable
fun rememberPhoto(path: String?, maxSize: Int): Bitmap? {
    val bmp by produceState<Bitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { PhotoStorage.loadThumbnail(it, maxSize) } }
    }
    return bmp
}

/**
 * Predogled slike taska. Klik odpre sliko čez cel zaslon.
 */
@Composable
fun PhotoThumb(path: String, modifier: Modifier = Modifier) {
    val bmp = rememberPhoto(path, 600)
    var full by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)).clickable { full = true },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), "Slika taska", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
        else Text("Nalagam sliko …")
    }
    if (full) {
        Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val big = rememberPhoto(path, 2000)
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { full = false }, contentAlignment = Alignment.Center) {
                big?.let { Image(it.asImageBitmap(), "Slika taska", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}
