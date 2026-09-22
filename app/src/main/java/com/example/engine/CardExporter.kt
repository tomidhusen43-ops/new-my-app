package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object CardExporter {

    /**
     * Saves reconstructed card directly to user's device gallery (Pictures/CardCloneAI).
     */
    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String = "card_clone_${System.currentTimeMillis()}",
        format: String = "PNG",
        quality: Int = 95
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val isPng = format.equals("PNG", ignoreCase = true)
            val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val mimeType = if (isPng) "image/png" else "image/jpeg"
            val extension = if (isPng) ".png" else ".jpg"
            val cleanName = filename.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_") + extension

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CardCloneAI")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("MediaStore insert failed")

            resolver.openOutputStream(imageUri)?.use { outStream: OutputStream ->
                val success = bitmap.compress(compressFormat, quality.coerceIn(10, 100), outStream)
                if (!success) {
                    throw IllegalStateException("Bitmap compression failed")
                }
            } ?: throw IllegalStateException("Could not open output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Result.success(imageUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves image to cache directory and launches Android share sheet.
     */
    suspend fun exportAndShare(
        context: Context,
        bitmap: Bitmap,
        filename: String = "card_clone",
        format: String = "PNG",
        quality: Int = 95
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val isPng = format.equals("PNG", ignoreCase = true)
            val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val extension = if (isPng) ".png" else ".jpg"
            val mimeType = if (isPng) "image/png" else "image/jpeg"

            val cleanName = filename.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

            val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(imagesDir, "$cleanName$extension")

            FileOutputStream(file).use { out ->
                val success = bitmap.compress(compressFormat, quality.coerceIn(10, 100), out)
                if (!success) throw IllegalStateException("Bitmap encoding failed")
                out.flush()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, cleanName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Reconstructed Card")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
