package com.opencode.android.feature.assistant

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WakeWordPackManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.generateKeyPair()

    @Test
    fun `installs signed pack atomically and deletes it`() {
        val root = temp.newFolder("valid-pack")
        val manager = manager(root)
        val archive = validArchive(id = "open-code", version = "1.0.0")
        val manifest = signedManifest(archive, id = "open-code", version = "1.0.0")

        val installed = manager.install(manifest, archive)

        assertEquals("Open Code", installed.name)
        assertEquals("ja-JP", installed.languageTag)
        assertEquals(listOf("hey open code", "open code"), installed.phrases)
        assertTrue(File(installed.directory, "pack.json").isFile)
        assertEquals(installed, manager.installed())

        manager.delete()
        assertFalse(manager.isInstalled())
        assertNull(manager.installed())
    }

    @Test
    fun `rejects manifest signed by another key`() {
        val archive = validArchive()
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val unsigned = baseManifest(archive)
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(otherKey.private)
            update(WakeWordPackManager.signingPayload(unsigned))
            Base64.getEncoder().encodeToString(sign())
        }
        val manager = manager(temp.newFolder("wrong-key"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            manager.install(unsigned.copy(signature = signature), archive)
        }

        assertTrue(error.message.orEmpty().contains("signature verification"))
        assertFalse(manager.isInstalled())
    }

    @Test
    fun `tampering signed fields invalidates signature`() {
        val archive = validArchive()
        val signed = signedManifest(archive)
        val manager = manager(temp.newFolder("tampered"))

        assertThrows(IllegalArgumentException::class.java) {
            manager.install(signed.copy(version = "2.0.0"), archive)
        }
    }

    @Test
    fun `rejects hash size and non https errors before activation`() {
        val archive = validArchive()
        val manager = manager(temp.newFolder("invalid-metadata"))

        val badHash = signedManifest(archive, sha256 = "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {
            manager.install(badHash, archive)
        }

        val badSize = signedManifest(archive, sizeBytes = archive.size.toLong() + 1)
        assertThrows(IllegalArgumentException::class.java) {
            manager.install(badSize, archive)
        }

        val insecure = signedManifest(archive).copy(url = "http://example.com/pack.zip")
        assertThrows(IllegalArgumentException::class.java) {
            manager.install(insecure, archive)
        }
        assertFalse(manager.isInstalled())
    }

    @Test
    fun `rejects ZIP traversal and leaves previous active pack intact`() {
        val root = temp.newFolder("traversal")
        val manager = manager(root)
        val firstArchive = validArchive(id = "first", version = "1.0.0")
        manager.install(signedManifest(firstArchive, id = "first", version = "1.0.0"), firstArchive)

        val malicious = zip(
            mapOf(
                "pack.json" to descriptor("second", "2.0.0"),
                "../escape.txt" to "escape"
            )
        )
        val maliciousManifest = signedManifest(malicious, id = "second", version = "2.0.0")

        assertThrows(IllegalArgumentException::class.java) {
            manager.install(maliciousManifest, malicious)
        }

        assertEquals("first", manager.installed()?.id)
        assertFalse(File(root.parentFile, "escape.txt").exists())
    }

    @Test
    fun `rejects missing required file and descriptor mismatch`() {
        val manager = manager(temp.newFolder("required-files"))
        val withoutDescriptor = zip(mapOf("readme.txt" to "hello"))
        val manifest = signedManifest(
            archive = withoutDescriptor,
            requiredFiles = listOf("pack.json", "readme.txt")
        )

        assertThrows(IllegalArgumentException::class.java) {
            manager.install(manifest, withoutDescriptor)
        }

        val mismatched = zip(mapOf("pack.json" to descriptor("other", "1.0.0")))
        assertThrows(IllegalArgumentException::class.java) {
            manager.install(signedManifest(mismatched), mismatched)
        }
    }

    @Test
    fun `parses embedded RSA public key PEM`() {
        val pem = buildString {
            appendLine("-----BEGIN PUBLIC KEY-----")
            appendLine(Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.public.encoded))
            appendLine("-----END PUBLIC KEY-----")
        }

        val parsed = WakeWordPackManager.parseRsaPublicKeyPem(pem)

        assertTrue(parsed.encoded.contentEquals(keyPair.public.encoded))
    }

    private fun manager(root: File) = WakeWordPackManager(
        rootDirectory = root,
        trustedPublicKey = keyPair.public
    )

    private fun signedManifest(
        archive: ByteArray,
        id: String = "open-code",
        version: String = "1.0.0",
        url: String = "https://example.com/pack.zip",
        sha256: String = WakeWordPackManager.sha256Hex(archive),
        sizeBytes: Long = archive.size.toLong(),
        requiredFiles: List<String> = listOf("pack.json")
    ): WakeWordPackManifest {
        val unsigned = WakeWordPackManifest(
            id = id,
            name = "Open Code",
            version = version,
            url = url,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            requiredFiles = requiredFiles,
            signature = ""
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(WakeWordPackManager.signingPayload(unsigned))
        return unsigned.copy(signature = Base64.getEncoder().encodeToString(signer.sign()))
    }

    private fun baseManifest(archive: ByteArray) = WakeWordPackManifest(
        id = "open-code",
        name = "Open Code",
        version = "1.0.0",
        url = "https://example.com/pack.zip",
        sha256 = WakeWordPackManager.sha256Hex(archive),
        sizeBytes = archive.size.toLong(),
        requiredFiles = listOf("pack.json"),
        signature = ""
    )

    private fun validArchive(
        id: String = "open-code",
        version: String = "1.0.0"
    ): ByteArray = zip(mapOf("pack.json" to descriptor(id, version)))

    private fun descriptor(id: String, version: String): String =
        """{
          "schemaVersion": 1,
          "id": "$id",
          "version": "$version",
          "languageTag": "ja-JP",
          "phrases": ["Hey Open Code", "Open Code"]
        }""".trimIndent()

    private fun zip(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
