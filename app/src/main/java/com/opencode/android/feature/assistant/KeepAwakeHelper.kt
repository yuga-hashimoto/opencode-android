package com.opencode.android.feature.assistant

import android.app.Activity
import android.view.WindowManager

object KeepAwakeHelper {

    fun enableKeepAwake(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun disableKeepAwake(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
