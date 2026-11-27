package com.senecapp.data

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class OrganizationImageCacheInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val verification = runCatching {
            runBlocking(Dispatchers.IO) { withTimeout(15_000) { checkImageCache() } }
        }
        finish(if (verification.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putInt("failures", if (verification.isSuccess) 0 else 1)
            putString("stream", verification.exceptionOrNull()?.stackTraceToString()
                ?: "PASS organization image reused from memory and then from disk without network\n")
        })
    }

    private suspend fun checkImageCache() {
        val loader = SingletonImageLoader.get(targetContext)
        check(loader.diskCache?.maxSize == 64L * 1024 * 1024)
        val loopback = InetAddress.getByName("127.0.0.1")
        val url = ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 5_000
            val sourceUrl = "http://127.0.0.1:${server.localPort}/${UUID.randomUUID()}.png"
            val response = thread(name = "image-fixture", isDaemon = true) {
                runCatching {
                    server.accept().use { client ->
                        client.soTimeout = 5_000
                        val request = client.getInputStream().bufferedReader()
                        while (!request.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                        val png = Base64.decode(PIXEL_PNG, Base64.DEFAULT)
                        client.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n" +
                                "Content-Length: ${png.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(png)
                            flush()
                        }
                    }
                }
            }
            val request = ImageRequest.Builder(targetContext).data(sourceUrl).size(16).build()
            val downloaded = loader.execute(request) as SuccessResult
            check(downloaded.dataSource == DataSource.NETWORK)
            response.join(5_000)
            check(!response.isAlive)
            sourceUrl
        }

        val offline = ImageRequest.Builder(targetContext).data(url).size(16)
            .networkCachePolicy(CachePolicy.DISABLED).build()
        val fromMemory = loader.execute(offline) as SuccessResult
        check(fromMemory.dataSource == DataSource.MEMORY_CACHE)
        val fromDisk = loader.execute(offline.newBuilder().memoryCachePolicy(CachePolicy.DISABLED).build()) as SuccessResult
        check(fromDisk.dataSource == DataSource.DISK)
    }

    private companion object {
        const val PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4n8IBAAPRAWwJlrvmAAAAAElFTkSuQmCC"
    }
}
