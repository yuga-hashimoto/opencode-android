package com.opencode.android.runtime.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRuntimeServiceCommandTest {
    @Test
    fun `maps public service actions to runtime commands`() {
        assertEquals(
            LocalRuntimeServiceCommand.InstallAndStart,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_INSTALL_AND_START)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Start,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_START)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Reinstall,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_REINSTALL)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Update,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_UPDATE)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Rollback,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_ROLLBACK)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Delete,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_DELETE)
        )
        assertEquals(
            LocalRuntimeServiceCommand.Stop,
            localRuntimeServiceCommand(LocalRuntimeService.ACTION_STOP)
        )
        assertEquals(LocalRuntimeServiceCommand.Restore, localRuntimeServiceCommand(null))
    }

    @Test
    fun `unknown action is ignored`() {
        assertEquals(LocalRuntimeServiceCommand.Ignore, localRuntimeServiceCommand("unknown"))
    }
}
