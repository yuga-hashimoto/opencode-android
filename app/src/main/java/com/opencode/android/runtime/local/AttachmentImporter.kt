package com.opencode.android.runtime.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.opencode.android.core.api.PromptAttachment
import java.io.File

/**
 * Copies a user-picked file into the runtime's shared workspace so the OpenCode
 * server can read it, returning a [PromptAttachment] referencing the server-side
 * /workspace path.
 */
class AttachmentImporter(
    private val context: Context,
    private val runtimeDirectory: File = File(context.filesDir, "runtime")
) {
    fun import(uri: Uri): PromptAttachment {
        val workspace = File(runtimeDirectory, "workspace").apply { mkdirs() }
        val filename = sanitize(queryDisplayName(uri) ?: "attachment-${System.currentTimeMillis()}")
        val destination = uniqueFile(workspace, filename)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open attachment input stream" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return PromptAttachment(
            filename = destination.name,
            mime = mime,
            url = "file:///workspace/${destination.name}"
        )
    }

    fun import(bitmap: Bitmap, filename: String = "image-${System.currentTimeMillis()}.jpg"): PromptAttachment {
        val workspace = File(runtimeDirectory, "workspace").apply { mkdirs() }
        val destination = uniqueFile(workspace, sanitize(filename))
        destination.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "Cannot encode attachment" }
        }
        return PromptAttachment(destination.name, "image/jpeg", "file:///workspace/${destination.name}")
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var counter = 1
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotBlank() && it != name }
        while (candidate.exists()) {
            val suffixed = if (extension != null) "$base-$counter.$extension" else "$base-$counter"
            candidate = File(dir, suffixed)
            counter++
        }
        return candidate
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "attachment" }
}
