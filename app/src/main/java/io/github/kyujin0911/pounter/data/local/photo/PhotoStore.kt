package io.github.kyujin0911.pounter.data.local.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.kyujin0911.pounter.data.detector.DetectionFailure
import io.github.kyujin0911.pounter.data.detector.DetectionFailureReason
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PhotoStore(private val context: Context) {
    private val directory = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File {
        require(name == File(name).name && name.endsWith(".jpg"))
        return File(directory, name)
    }

    suspend fun import(uri: Uri): Triple<String, Int, Int> =
        withContext(Dispatchers.IO) {
            val raw = File.createTempFile("import-", ".source", context.cacheDir)
            var normalized: Bitmap? = null
            var decoded: Bitmap? = null
            var destination: File? = null
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    raw.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0L
                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            totalBytes += bytesRead
                            if (totalBytes > 50L * 1024 * 1024)
                                throw IOException("사진 파일은 50MB 이하여야 합니다.")
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                } ?: throw IOException("사진을 읽을 수 없습니다. 다시 선택해주세요.")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(raw.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                    throw IOException("지원하지 않는 사진입니다.")
                var sampleSize = 1
                while (max(bounds.outWidth, bounds.outHeight) / sampleSize > 3000) sampleSize *= 2
                decoded =
                    BitmapFactory.decodeFile(
                        raw.path,
                        BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ) ?: throw IOException("사진을 열 수 없습니다.")
                val exif = ExifInterface(raw)
                val matrix =
                    Matrix().apply {
                        if (exif.isFlipped) postScale(-1f, 1f)
                        if (exif.rotationDegrees != 0) postRotate(exif.rotationDegrees.toFloat())
                    }
                normalized =
                    Bitmap.createBitmap(
                        decoded!!,
                        0,
                        0,
                        decoded!!.width,
                        decoded!!.height,
                        matrix,
                        true,
                    )
                val name = "${UUID.randomUUID()}.jpg"
                destination = file(name)
                destination!!.outputStream().use { out ->
                    if (!normalized!!.compress(Bitmap.CompressFormat.JPEG, 95, out))
                        throw IOException("사진 저장에 실패했습니다.")
                }
                Triple(name, normalized!!.width, normalized!!.height)
            } catch (e: Exception) {
                destination?.delete()
                throw e
            } finally {
                if (
                    uri.authority == "${context.packageName}.files" &&
                        uri.pathSegments.firstOrNull() == "captures"
                ) {
                    uri.lastPathSegment
                        ?.takeIf { it == File(it).name }
                        ?.let { File(context.cacheDir, "captures/$it").delete() }
                }
                raw.delete()
                if (normalized !== decoded) normalized?.recycle()
                decoded?.recycle()
            }
        }

    suspend fun bitmap(name: String, maxSidePx: Int = 2048): Bitmap =
        withContext(Dispatchers.IO) {
            val path = file(name).path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSidePx) sampleSize *= 2
            BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: throw IOException("저장된 사진을 읽을 수 없습니다.")
        }

    suspend fun analysisPhoto(name: String, maxSidePx: Int, maxDecodedPixels: Long): AnalysisPhoto =
        readOwnedAnalysisPhoto {
            if (maxSidePx <= 0 || maxDecodedPixels <= 0)
                throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
            try {
                val path = file(name).path
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                    throw DetectionFailure(DetectionFailureReason.PHOTO_READ)
                var sampleSize = 1
                while (
                    max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSidePx
                ) sampleSize *= 2
                val decodedWidth = (bounds.outWidth.toLong() + sampleSize - 1) / sampleSize
                val decodedHeight = (bounds.outHeight.toLong() + sampleSize - 1) / sampleSize
                if (decodedWidth * decodedHeight > maxDecodedPixels)
                    throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
                ensureActive()
                val bitmap =
                    BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        },
                    ) ?: throw DetectionFailure(DetectionFailureReason.PHOTO_READ)
                try {
                    ensureActive()
                    if (bitmap.width.toLong() * bitmap.height > maxDecodedPixels)
                        throw DetectionFailure(DetectionFailureReason.INVALID_INPUT)
                    AnalysisPhoto(bitmap, bounds.outWidth, bounds.outHeight)
                } catch (failure: Throwable) {
                    bitmap.recycle()
                    throw failure
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: DetectionFailure) {
                throw failure
            } catch (failure: Exception) {
                throw DetectionFailure(DetectionFailureReason.PHOTO_READ, failure)
            }
        }

    fun removeOrphans(referenced: Set<String>) {
        directory
            .listFiles()
            ?.filter { it.extension == "jpg" && it.name !in referenced }
            ?.forEach { delete(it.name) }
    }

    fun delete(name: String) {
        val photoFile = file(name)
        if (photoFile.exists() && !photoFile.delete())
            throw IOException("사진 삭제에 실패했습니다. 다시 시도해주세요.")
    }
}
