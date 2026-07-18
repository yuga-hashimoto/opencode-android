package com.opencode.android.runtime.local

import java.io.File
import java.io.FileOutputStream

internal fun activateRuntimeEnvironment(
    active: File,
    staging: File,
    rollback: File,
    moveDirectory: (File, File) -> Unit = ::moveRuntimeDirectory,
    finalizeActivation: (File) -> Unit
) {
    require(staging.isDirectory) { "Runtime staging environment is missing" }
    requireSameParent(active, staging, rollback)
    deleteRecursivelyRequired(rollback)

    var previousMoved = false
    var stagingActivated = false
    try {
        if (active.exists()) {
            moveDirectory(active, rollback)
            previousMoved = true
        }
        moveDirectory(staging, active)
        stagingActivated = true
        finalizeActivation(active)
        deleteRecursivelyRequired(rollback)
    } catch (error: Throwable) {
        if (stagingActivated && active.exists()) {
            runCatching { deleteRecursivelyRequired(active) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
        }
        if (previousMoved && rollback.exists() && !active.exists()) {
            runCatching { moveDirectory(rollback, active) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
        }
        throw error
    }
}

internal fun recoverInterruptedRuntimeEnvironment(
    active: File,
    rollback: File,
    topLevelMetadata: File,
    moveDirectory: (File, File) -> Unit = ::moveRuntimeDirectory
): Boolean {
    if (!rollback.isDirectory) return false
    requireSameParent(active, rollback)
    val failed = File(active.parentFile, active.name + ".failed")
    deleteRecursivelyRequired(failed)
    if (active.exists()) moveDirectory(active, failed)
    try {
        moveDirectory(rollback, active)
        val restoredMetadata = File(active, "metadata.json")
        require(restoredMetadata.isFile) {
            "Rollback runtime metadata is missing"
        }
        replaceFileAtomically(restoredMetadata, topLevelMetadata)
        require(topLevelMetadata.isFile && topLevelMetadata.readBytes().contentEquals(restoredMetadata.readBytes())) {
            "Restored runtime metadata verification failed"
        }
        deleteRecursivelyRequired(failed)
        return true
    } catch (error: Throwable) {
        if (!active.exists() && failed.exists()) {
            runCatching { moveDirectory(failed, active) }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
        }
        throw error
    }
}

internal fun replaceFileAtomically(source: File, destination: File) {
    require(source.isFile) { "Replacement source is missing: ${source.name}" }
    destination.parentFile?.mkdirs()
    val staged = File(destination.parentFile, destination.name + ".staged")
    val backup = File(destination.parentFile, destination.name + ".backup")
    staged.delete()
    if (!destination.exists() && backup.exists()) {
        require(backup.renameTo(destination)) {
            "Unable to recover ${destination.name} backup"
        }
    }
    FileOutputStream(staged).use { output ->
        source.inputStream().use { input -> input.copyTo(output) }
        output.fd.sync()
    }
    var previousMoved = false
    try {
        if (destination.exists()) {
            if (backup.exists()) {
                require(backup.delete()) { "Unable to remove stale ${backup.name}" }
            }
            require(destination.renameTo(backup)) {
                "Unable to preserve ${destination.name}"
            }
            previousMoved = true
        }
        require(staged.renameTo(destination)) {
            "Unable to activate ${destination.name}"
        }
        backup.delete()
    } catch (error: Throwable) {
        destination.delete()
        if (previousMoved && backup.exists()) {
            runCatching {
                require(backup.renameTo(destination)) {
                    "Unable to restore ${destination.name}"
                }
            }.exceptionOrNull()?.let(error::addSuppressed)
        }
        throw error
    } finally {
        staged.delete()
        if (destination.exists()) backup.delete()
    }
}

private fun moveRuntimeDirectory(source: File, destination: File) {
    require(source.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
        "Atomic runtime directory move requires the same parent"
    }
    require(!destination.exists()) { "Runtime destination already exists: ${destination.name}" }
    require(source.renameTo(destination)) {
        "Unable to move ${source.name} to ${destination.name}"
    }
}

private fun requireSameParent(vararg files: File) {
    val parents = files.mapNotNull { it.parentFile?.canonicalFile }.distinct()
    require(parents.size == 1) { "Runtime activation directories must share one parent" }
}

private fun deleteRecursivelyRequired(file: File) {
    if (file.exists()) {
        require(file.deleteRecursively()) { "Unable to delete ${file.name}" }
    }
}
