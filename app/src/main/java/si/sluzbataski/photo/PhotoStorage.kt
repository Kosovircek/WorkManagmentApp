package si.sluzbataski.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Shranjevanje slik taskov.
 *
 * Slike so v notranjem pomnilniku aplikacije (files/photos), zato jih druge aplikacije
 * ne vidijo. Kamera dobi začasen "content://" naslov prek FileProviderja (glej
 * res/xml/file_paths.xml), kamor zapiše posneto sliko.
 */
object PhotoStorage {
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    private fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    /** Ustvari novo prazno datoteko za sliko. */
    fun newPhotoFile(context: Context): File =
        File(dir(context), "IMG_${System.currentTimeMillis()}.jpg")

    /** content:// naslov datoteke, ki ga lahko podamo kameri. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

    /** Kopira sliko iz galerije (content uri) v notranji pomnilnik. */
    fun importFromUri(context: Context, uri: Uri): File? = try {
        val target = newPhotoFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        target
    } catch (e: Exception) {
        null
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    /**
     * Naloži pomanjšano sliko (da ne porabimo preveč pomnilnika) in jo pravilno zavrti
     * glede na EXIF podatke kamere.
     */
    fun loadThumbnail(path: String, maxSize: Int): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rotation = runCatching {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }
}
