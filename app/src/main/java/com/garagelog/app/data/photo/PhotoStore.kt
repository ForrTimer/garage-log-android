package com.garagelog.app.data.photo

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies a picked photo into app-private storage so it survives even if the
 * source URI (from the system photo picker) becomes unavailable later.
 */
class PhotoStore(private val context: Context) {

    private val photosDir: File by lazy {
        File(context.filesDir, "photos").apply { mkdirs() }
    }

    fun copyIntoAppStorage(sourceUri: Uri): String? {
        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
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
