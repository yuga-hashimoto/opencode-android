package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionAggregationTest {
    @Test
    fun `merges directory scoped sessions by id and newest update first`() {
        val root = OpenCodeSession(
            id = "root",
            title = "Root",
            directory = "/root",
            time = OpenCodeTime(created = 10, updated = 15)
        )
        val demoOld = OpenCodeSession(
            id = "demo",
            title = "Demo old",
            directory = "/root/demo",
            time = OpenCodeTime(created = 20, updated = 21)
        )
        val demoNew = demoOld.copy(
            title = "Demo new",
            time = OpenCodeTime(created = 20, updated = 30)
        )

        val result = mergeSessionLists(
            listOf(
                listOf(root, demoOld),
                listOf(demoNew)
            )
        )

        assertEquals(listOf("demo", "root"), result.map { it.id })
        assertEquals("Demo new", result.first().title)
    }
}
