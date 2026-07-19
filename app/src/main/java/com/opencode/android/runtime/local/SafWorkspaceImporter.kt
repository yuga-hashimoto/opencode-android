package com.opencode.android.runtime.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Imports a SAF document tree into the local runtime workspace directory so
 * PRoot/OpenCode can access files as plain paths under /workspace.
 */
class SafWorkspaceImporter(
    private val context: Context,
    private val runtimeDirectory: File = File(context.filesDir, "runtime")
) {
    fun importTree(treeUri: Uri): File {
        val source = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Unable to open selected folder")
        val name = source.name?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "imported-${System.currentTimeMillis()}"
        val destination = File(runtimeDirectory, "workspace/$name").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        copyDocument(source, destination)
        return destination
    }

    private fun copyDocument(source: DocumentFile, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles().forEach { child ->
                val childName = child.name ?: return@forEach
                val childDest = File(destination, childName)
                if (child.isDirectory) {
                    copyDocument(child, childDest)
                } else if (child.isFile) {
                    copyFile(child, childDest)
                }
            }
        } else if (source.isFile) {
            copyFile(source, destination)
        }
    }

    private fun copyFile(source: DocumentFile, destination: File) {
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read ${source.uri}")
    }
}
