package com.opencode.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopLevelNavigationTest {
    private val topLevelRoutes = setOf("home", "chat", "activity", "settings")

    @Test
    fun `nested workspace routes select home`() {
        assertEquals("home", topLevelRouteFor("workspace-detail", topLevelRoutes))
        assertEquals("home", topLevelRouteFor("local-runtime-management", topLevelRoutes))
    }

    @Test
    fun `nested session route selects activity`() {
        assertEquals("activity", topLevelRouteFor("session-detail", topLevelRoutes))
    }

    @Test
    fun `unknown route has no selected tab`() {
        assertNull(topLevelRouteFor("unknown", topLevelRoutes))
    }
}
