package com.raoprime.downloadivo.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class RecoveryIntegrationTest {

    private val expectedSha256 = "052e838c51cc2ea60c6e6b3d5a0914c328a74df06bd9ecc46a8d849c388d68c3"
    private val expectedTotalSize = 5952000L
    private val serverBaseUrl = "http://127.0.0.1:8899"

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testParseContentRangeHeaders() {
        assertEquals(1984000L, DownloadEngine.parseContentRangeStart("bytes 1984000-5951999/5952000"))
        assertEquals(5952000L, DownloadEngine.parseContentRangeTotal("bytes 1984000-5951999/5952000"))
        assertEquals(0L, DownloadEngine.parseContentRangeStart("bytes 0-499/500"))
        assertEquals(500L, DownloadEngine.parseContentRangeTotal("bytes 0-499/500"))
        assertNull(DownloadEngine.parseContentRangeStart(null as String?))
        assertNull(DownloadEngine.parseContentRangeTotal("invalid-header"))
    }

    @Test
    fun testResumableTransferWithRange206() = runBlocking {
        val testFile = File.createTempFile("test_resume_", ".part")
        testFile.deleteOnExit()

        // 1. Download initial 1/3 (1,984,000 bytes) from real test server to simulate interrupted transfer
        val oneThirdBytes = 1984000L
        val conn = URL("$serverBaseUrl/test/disposable-file").openConnection()
        conn.setRequestProperty("Range", "bytes=0-${oneThirdBytes - 1}")
        conn.connect()
        conn.getInputStream().use { input ->
            FileOutputStream(testFile).use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                }
            }
        }
        assertEquals("Initial saved bytes before interrupt must be 1/3 of total", oneThirdBytes, testFile.length())

        // 2. Resume download from verified offset using DownloadEngine
        var lastMetrics: StreamTransferMetrics? = null
        val result = DownloadEngine.downloadStreamToFile(
            targetUrl = "$serverBaseUrl/test/disposable-file",
            format = "best",
            destinationFile = testFile,
            expectedTotalBytes = expectedTotalSize,
            isCancelled = { false },
            onProgress = { _, _, _, _, _ -> },
            onTransferMetrics = { metrics -> lastMetrics = metrics }
        )

        assertTrue("Download should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val metrics = lastMetrics
        assertNotNull("Metrics should be recorded", metrics)

        // 3. Verify exact resume contract:
        // - Saved byte count before reconnect
        // - Offset requested on reconnect
        // - Bytes transferred after reconnect
        // - Total file length
        assertEquals("Saved byte count before reconnect must match partial bytes", oneThirdBytes, metrics!!.initialSavedBytes)
        assertEquals("Offset requested on reconnect must match saved bytes", oneThirdBytes, metrics.offsetRequested)
        assertEquals("Bytes transferred after reconnect must be remaining 2/3 (3,968,000 bytes)", expectedTotalSize - oneThirdBytes, metrics.bytesTransferredThisSession)
        assertEquals("Total file length must equal expected total (5,952,000 bytes)", expectedTotalSize, metrics.totalFileBytes)
        assertTrue("Transfer must be verified as resumed (HTTP 206)", metrics.isResumeSupported)
        assertEquals("Completed file length on disk must match server file size", expectedTotalSize, testFile.length())

        // 4. Verify cryptographic SHA-256 integrity of the completed file
        val actualSha256 = computeSha256(testFile)
        assertEquals("Completed file SHA-256 checksum must match expected", expectedSha256, actualSha256)
    }

    @Test
    fun testNonResumableSourceCleanRestart200() = runBlocking {
        val testFile = File.createTempFile("test_no_resume_", ".part")
        testFile.deleteOnExit()

        // 1. Partial file exists from previous interrupted session (1,984,000 bytes of partial/outdated data)
        val partialBytes = 1984000L
        FileOutputStream(testFile).use { out ->
            out.write(ByteArray(partialBytes.toInt()) { 0xAA.toByte() })
        }
        assertEquals(partialBytes, testFile.length())

        // 2. Request download from endpoint that ignores Range and returns HTTP 200
        var lastMetrics: StreamTransferMetrics? = null
        val result = DownloadEngine.downloadStreamToFile(
            targetUrl = "$serverBaseUrl/test/no-resume-file",
            format = "best",
            destinationFile = testFile,
            expectedTotalBytes = expectedTotalSize,
            isCancelled = { false },
            onProgress = { _, _, _, _, _ -> },
            onTransferMetrics = { metrics -> lastMetrics = metrics }
        )

        assertTrue("Download should succeed via clean restart: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val metrics = lastMetrics
        assertNotNull("Metrics should be recorded", metrics)

        // 3. Invariant check: Never append a full response to a partial file!
        assertEquals("Saved bytes before attempt", partialBytes, metrics!!.initialSavedBytes)
        assertFalse("Must NOT claim resume when server sends 200 OK", metrics.isResumeSupported)
        assertEquals("Must retransfer full file on clean restart", expectedTotalSize, metrics.bytesTransferredThisSession)
        assertEquals("Final file length must be exact size without corrupt appending", expectedTotalSize, testFile.length())

        // 4. Verify SHA-256 checksum passes
        val actualSha256 = computeSha256(testFile)
        assertEquals("Clean restart file SHA-256 must match expected", expectedSha256, actualSha256)
    }
}
