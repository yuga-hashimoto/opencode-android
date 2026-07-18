package com.opencode.android.runtime.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalRuntimeProcessLauncherTest {
    @Test
    fun `guest environment exposes Alpine tools and root home`() {
        val environment = localRuntimeEnvironment(
            suiteEnvironment = mapOf(
                "LD_LIBRARY_PATH" to "/native/lib",
                "PATH" to "/system/bin",
                "HOME" to "/android/home"
            ),
            prootTmp = File("/android/proot-tmp")
        )

        assertEquals("/root", environment["HOME"])
        assertEquals(
            "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            environment["PATH"]
        )
        assertEquals("/tmp", environment["TMPDIR"])
        assertEquals("/root/.config", environment["XDG_CONFIG_HOME"])
        assertEquals("/android/proot-tmp", environment["PROOT_TMP_DIR"])
        assertEquals("/native/lib", environment["LD_LIBRARY_PATH"])
        assertTrue(environment["OPENCODE_DISABLE_AUTOUPDATE"] == "true")
    }
}
