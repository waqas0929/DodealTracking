package com.dodeal.updater.net

import com.dodeal.updater.UpdaterLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/**
 * Downloads an APK to app-private storage with a temporary .part filename and HTTP range resume.
 * On success the file is renamed to the final name; corrupt/failed files are deleted by the caller
 * after verification. Never downloads to shared/world-readable storage.
 */
class Downloader(private val client: OkHttpClient) {

    fun tempFile(dir: File, pkg: String, version: Long) = File(dir, "update_${pkg}_$version.apk.part")
    fun finalFile(dir: File, pkg: String, version: Long) = File(dir, "update_${pkg}_$version.apk")

    suspend fun download(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = if (target.exists()) target.length() else 0L
            val reqBuilder = Request.Builder().url(url)
            if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
            UpdaterLog.i("DOWNLOAD_STARTED offset=$existing")

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    UpdaterLog.w("DOWNLOAD_HTTP_${resp.code}")
                    return@withContext false
                }
                val append = resp.code == 206 && existing > 0
                val body = resp.body ?: return@withContext false
                val total = (body.contentLength().takeIf { it > 0 } ?: -1L) + if (append) existing else 0
                RandomAccessFile(target, "rw").use { raf ->
                    if (append) raf.seek(existing) else raf.setLength(0)
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var written = existing
                        var lastLog = 0L
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            raf.write(buf, 0, n); written += n
                            if (total > 0 && written - lastLog > total / 10) {
                                lastLog = written
                                UpdaterLog.i("DOWNLOAD_PROGRESS ${written * 100 / total}%")
                            }
                        }
                    }
                }
            }
            UpdaterLog.i("DOWNLOAD_COMPLETE")
            true
        } catch (t: Throwable) {
            UpdaterLog.e("DOWNLOAD_FAILED", t)
            false
        }
    }
}
