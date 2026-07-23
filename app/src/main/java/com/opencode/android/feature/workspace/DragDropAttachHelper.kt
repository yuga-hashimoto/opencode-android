package com.opencode.android.feature.workspace

import android.view.DragEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

object DragDropAttachHelper {

    @Composable
    fun dragDropFileAttachModifier(onFileDropped: (String) -> Unit): Modifier {
        val view = LocalView.current
        DisposableEffect(onFileDropped) {
            val listener = android.view.View.OnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DROP -> {
                        val clipData = event.clipData ?: return@OnDragListener false
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri ?: continue
                            val path = uri.path ?: uri.toString()
                            onFileDropped(path)
                            return@OnDragListener true
                        }
                        false
                    }
                    DragEvent.ACTION_DRAG_STARTED,
                    DragEvent.ACTION_DRAG_ENTERED,
                    DragEvent.ACTION_DRAG_LOCATION,
                    DragEvent.ACTION_DRAG_EXITED,
                    DragEvent.ACTION_DRAG_ENDED -> true
                    else -> false
                }
            }
            view.setOnDragListener(listener)
            onDispose { view.setOnDragListener(null) }
        }
        return Modifier
    }
}
