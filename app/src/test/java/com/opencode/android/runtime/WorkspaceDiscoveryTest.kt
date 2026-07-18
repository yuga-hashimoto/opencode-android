package com.opencode.android.runtime

import com.opencode.android.core.api.OpenCodeProject
import com.opencode.android.core.api.OpenCodeSession
import com.opencode.android.core.api.OpenCodeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceDiscoveryTest {
 @Test
 fun `merges current sessions and project worktrees without global root or duplicates`() {
 val result = mergeWorkspaceRefs(
 currentDirectory = "/root",
 sessions = listOf(
 OpenCodeSession(
 id = "s1",
 title = "Root",
 directory = "/root",
 time = OpenCodeTime(created = 1)
 ),
 OpenCodeSession(
 id = "s2",
 title = "App",
 directory = "/workspace/app",
 time = OpenCodeTime(created = 2)
 )
 ),
 projects = listOf(
 OpenCodeProject(id = "global", worktree = "/"),
 OpenCodeProject(id = "demo", worktree = "/root/demo", name = "Demo project"),
 OpenCodeProject(id = "duplicate", worktree = "/workspace/app")
 )
 )

 assertEquals(
 listOf(
 WorkspaceRef("/root", "root", "/root"),
 WorkspaceRef("/workspace/app", "app", "/workspace/app"),
 WorkspaceRef("/root/demo", "Demo project", "/root/demo")
 ),
 result
 )
 }
}
