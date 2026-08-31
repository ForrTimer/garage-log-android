package com.garagelog.app.data.photo

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies a picked photo into app-private storage so it survives even if the
 * source URI (from the system photo picker) becomes unavailable later.
 * Files are named by photo id (not a random name) so Drive sync can derive
 * a local destination path for a remote photo without a separate index.
 */
class PhotoStore(private val context: Context) {

    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    fun fileForPhotoId(photoId: String): File = File(photosDir, "$photoId.jpg")

    fun copyIntoAppStorage(sourceUri: Uri, photoId: String): String? {
        val destFile = fileForPhotoId(photoId)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun delete(filePath: String) {
        runCatching { File(filePath).delete() }
    }
}
