package com.dark.neuroverse.util

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream

@SuppressLint("Range")
fun uriToFile(context: Context, uri: Uri): File {
    // Case 1: file:// Uri → direct
    if ("file" == uri.scheme) {
        return File(requireNotNull(uri.path))
    }

    // Case 2: content:// Uri → handle DownloadsProvider separately
    if ("content" == uri.scheme) {
        // DownloadsProvider special case
        if (uri.authority == "com.android.providers.downloads.documents") {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                Log.d("UriToFile", "DownloadsProvider docId = $docId")

                if (docId.startsWith("msf:") || docId.startsWith("raw:")) {
                    // raw:/msf: cases
                    val possiblePath = docId.removePrefix("raw:").removePrefix("msf:")
                    if (File(possiblePath).exists()) {
                        Log.d("UriToFile", "Resolved Downloads raw/msf path = $possiblePath")
                        return File(possiblePath)
                    }
                } else {
                    // Try numeric ID
                    val id = docId.toLongOrNull()
                    if (id != null) {
                        val contentUri = ContentUris.withAppendedId(
                            "content://downloads/public_downloads".toUri(),
                            id
                        )
                        context.contentResolver.query(
                            contentUri,
                            arrayOf(MediaStore.MediaColumns.DATA),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val path =
                                    cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA))
                                if (!path.isNullOrEmpty()) {
                                    Log.d("UriToFile", "Resolved Downloads path = $path")
                                    return File(path)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UriToFile", "Failed resolving DownloadsProvider", e)
            }
        }

        // Generic MediaStore query (works for images, audio, etc.)
        try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Files.FileColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    if (columnIndex != -1) {
                        val path = cursor.getString(columnIndex)
                        if (!path.isNullOrEmpty()) {
                            Log.d("UriToFile", "Resolved MediaStore path = $path")
                            return File(path)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UriToFile", "Generic MediaStore resolve failed", e)
        }
    }

    // Case 3: fallback → copy into cache
    val fileName =
        queryDisplayName(context.contentResolver, uri) ?: "temp_${System.currentTimeMillis()}"
    val tempFile = File(context.cacheDir, fileName)

    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    Log.d("UriToFile", "Fallback copy created at ${tempFile.absolutePath}")
    return tempFile
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) return cursor.getString(index)
        }
    }
    return null
}


fun isZipByName(name: String?): Boolean = name?.lowercase()?.endsWith(".zip") == true

fun isZipByMime(mime: String?): Boolean =
    mime == "application/zip" || mime == "application/x-zip-compressed"

// Check magic number: PK\x03\x04
fun isZipByHeader(resolver: ContentResolver, uri: Uri): Boolean = try {
    resolver.openInputStream(uri)?.use { ins ->
        val sig = ByteArray(4)
        val n = ins.read(sig)
        n == 4 && sig[0] == 'P'.code.toByte() && sig[1] == 'K'.code.toByte() && sig[2] == 3.toByte() && sig[3] == 4.toByte()
    } ?: false
} catch (_: Exception) {
    false
}
