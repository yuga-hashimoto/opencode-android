package com.opencode.android.feature.assistant

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.opencode.android.MainActivity
import com.opencode.android.OpenCodeApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeWordPackInstrumentedTest {
    private lateinit var app: OpenCodeApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Context>() as OpenCodeApplication
        app.wakeWordListeningController.stop()
        app.wakeWordPackManager.delete()
        app.settings.wakeWordListeningEnabled = false
    }

    @After
    fun tearDown() {
        app.wakeWordListeningController.stop()
        app.wakeWordPackManager.delete()
        app.settings.wakeWordListeningEnabled = false
    }

    @Test
    fun embeddedPublicKeyInstallsSignedFixtureAndForegroundListenerStartsAndStops() {
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        val manifest = testAssets.open("wakeword-fixture-manifest.json")
            .bufferedReader()
            .use { Gson().fromJson(it, WakeWordPackManifest::class.java) }
        val archive = testAssets.open("wakeword-fixture.zip").use { it.readBytes() }

        val installed = app.wakeWordPackManager.install(manifest, archive)

        assertEquals("instrumented-open-code", installed.id)
        assertEquals("1.0.0", installed.version)
        assertEquals(listOf("hey open code", "open code"), installed.phrases)
        assertTrue(app.wakeWordPackManager.isInstalled())

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            app.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${app.packageName} RECORD_AUDIO allow"
        ).close()

        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                app.wakeWordListeningController.start()
            }
            waitUntil { app.settings.wakeWordListeningEnabled }
            assertTrue(app.settings.wakeWordListeningEnabled)

            scenario.onActivity {
                app.wakeWordListeningController.stop()
            }
            waitUntil { !app.settings.wakeWordListeningEnabled }
            assertFalse(app.settings.wakeWordListeningEnabled)
        }
    }

    private fun waitUntil(
        timeoutMillis: Long = 10_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100L)
        }
        throw AssertionError("Condition was not met within ${timeoutMillis}ms")
    }
}
