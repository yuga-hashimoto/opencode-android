package com.opencode.android.ui

import androidx.navigation.NavHostController

internal fun topLevelRouteFor(route: String?, topLevelRoutes: Set<String>): String? = when (route) {
    "workspace-detail", "local-runtime-management" -> "home"
    "session-detail" -> "activity"
    else -> route?.takeIf { it in topLevelRoutes }
}

internal fun NavHostController.navigateTopLevel(route: String, homeRoute: String) {
    navigate(route) {
        popUpTo(homeRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
