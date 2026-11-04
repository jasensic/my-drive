package com.jasensic.mydrive.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumableDownloadTest {
    private val payload = ByteArray(600_000) { (it % 251).toByte() }
    private val ranges = mutableListOf<String?>()
    private var honorRange = true
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val remote = RetrofitRemoteFileSource()

    @Before
    fun start() {
        dir = Files.createTempDirectory("mydrive-download").toFile()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == "/file") serve(request) else MockResponse().setResponseCode(404)
        }
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun serve(request: RecordedRequest): MockResponse {
        val range = request.getHeader("Range")
        ranges += range
        val start = if (honorRange) range?.removePrefix("bytes=")?.removeSuffix("-")?.toLongOrNull() else null
        return when {
            start != null && start >= payload.size ->
                MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${payload.size}")
            start != null ->
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                    .setBody(Buffer().write(payload, start.toInt(), payload.size - start.toInt()))
            else -> MockResponse().setBody(Buffer().write(payload))
        }
    }

    private fun url(path: String = "/file") = server.url(path).toString()

    private fun download(dest: File, onProgress: ((Long, Long) -> Unit)? = null) =
        runBlocking { remote.downloadTo(url(), "token", dest.absolutePath, onProgress) }

    @Test
    fun downloadsThroughAPartFileAndLeavesNoneBehind() {
        val dest = File(dir, "a")
        download(dest)
        assertArrayEquals(payload, dest.readBytes())
        assertFalse(File(dir, "a.part").exists())
        assertEquals(listOf<String?>(null), ranges)
    }

    @Test
    fun resumesFromThePartialBytesWithARangeRequest() {
        val dest = File(dir, "a")
        File(dir, "a.part").writeBytes(payload.copyOf(400_000))
        val seen = mutableListOf<Long>()
        download(dest) { read, _ -> seen += read }
        assertArrayEquals(payload, dest.readBytes())
        assertEquals(listOf<String?>("bytes=400000-"), ranges)
        // Progress counts the bytes already on disk, so it never restarts from zero.
        assertTrue(seen.first() > 400_000)
        assertEquals(payload.size.toLong(), seen.last())
    }

    @Test
    fun startsOverWhenTheServerIgnoresRange() {
        honorRange = false
        val dest = File(dir, "a")
        File(dir, "a.part").writeBytes(ByteArray(1_000) { 7 })
        download(dest)
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun startsOverWhenThePartIsLongerThanTheFile() {
        val dest = File(dir, "a")
        File(dir, "a.part").writeBytes(ByteArray(payload.size + 10) { 7 })
        download(dest)
        assertArrayEquals(payload, dest.readBytes())
        assertEquals(listOf<String?>("bytes=${payload.size + 10}-", null), ranges)
    }

    @Test
    fun finishesAPartThatWasAlreadyComplete() {
        val dest = File(dir, "a")
        File(dir, "a.part").writeBytes(payload)
        download(dest)
        assertArrayEquals(payload, dest.readBytes())
        assertEquals(listOf<String?>("bytes=${payload.size}-"), ranges)
    }

    @Test
    fun reportsHttpErrorsAndKeepsNoFile() {
        val dest = File(dir, "a")
        val err = runCatching {
            runBlocking { remote.downloadTo(url("/missing"), "token", dest.absolutePath) }
        }.exceptionOrNull()
        assertEquals("download failed: 404", err?.message)
        assertFalse(dest.exists())
    }
}
