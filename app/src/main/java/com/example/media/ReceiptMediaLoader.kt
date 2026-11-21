package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "ReceiptMediaLoader"
private const val MAX_BITMAP_DIMENSION = 1024
private const val JPEG_QUALITY = 82

class ReceiptMediaLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class PreparedReceiptMedia(
    val jpegBytes: ByteArray,
    val isPdf: Boolean
)

object ReceiptMediaLoader {

    fun createTempCameraUri(context: Context): Uri {
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val photoFile = File(receiptsDir, "receipt_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
    }

    fun isLikelyPdfUri(context: Context, uri: Uri, explicitMimeType: String?): Boolean {
        if (explicitMimeType?.contains("pdf", ignoreCase = true) == true) {
            return true
        }
        val resolvedType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (resolvedType?.contains("pdf", ignoreCase = true) == true) {
            return true
        }
        val displayName = queryDisplayName(context, uri)
        return displayName?.endsWith(".pdf", ignoreCase = true) == true ||
            uri.toString().endsWith(".pdf", ignoreCase = true)
    }

    suspend fun loadReceiptMedia(
        context: Context,
        uri: Uri,
        explicitMimeType: String? = null
    ): PreparedReceiptMedia = withContext(Dispatchers.IO) {
        val tempFile = copyUriToTempFile(context, uri)
        try {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw ReceiptMediaLoadException("O arquivo selecionado está vazio ou inacessível.")
            }

            val isPdf = hasPdfMagicBytes(tempFile) || isLikelyPdfUri(context, uri, explicitMimeType)
            val bitmap = if (isPdf) {
                renderFirstPdfPage(tempFile)
            } else {
                decodeImageFromFile(tempFile)
            }

            try {
                val jpegBytes = compressBitmapToJpeg(bitmap)
                Log.d(
                    TAG,
                    "Prepared receipt media: isPdf=$isPdf, dims=${bitmap.width}x${bitmap.height}, bytes=${jpegBytes.size}"
                )
                PreparedReceiptMedia(jpegBytes = jpegBytes, isPdf = isPdf)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (e: ReceiptMediaLoadException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load receipt media from $uri", e)
            throw ReceiptMediaLoadException(
                "Não foi possível processar o arquivo selecionado (${e.localizedMessage ?: "formato não suportado"}).",
                e
            )
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    private fun copyUriToTempFile(context: Context, uri: Uri): File {
        val tempDir = File(context.cacheDir, "media_tmp").apply { mkdirs() }
        val tempFile = File.createTempFile("receipt_", ".bin", tempDir)

        val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
            }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
                    ParcelFileDescriptor.AutoCloseInputStream(pfd)
                }
            }.getOrNull()
            ?: throw ReceiptMediaLoadException("Não foi possível abrir o arquivo selecionado.")

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 16 * 1024)
            }
        }
        return tempFile
    }

    private fun hasPdfMagicBytes(file: File): Boolean {
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(5)
                val read = stream.read(header)
                read >= 4 &&
                    header[0] == '%'.code.toByte() &&
                    header[1] == 'P'.code.toByte() &&
                    header[2] == 'D'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun renderFirstPdfPage(pdfFile: File): Bitmap {
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) {
                    throw ReceiptMediaLoadException("O arquivo PDF não possui páginas.")
                }
                renderer.openPage(0).use { page ->
                    val baseWidth = max(page.width, 1)
                    val baseHeight = max(page.height, 1)
                    val targetScale = min(
                        2.0f,
                        MAX_BITMAP_DIMENSION.toFloat() / max(baseWidth, baseHeight).toFloat()
                    ).coerceAtLeast(0.5f)

                    val renderWidth = (baseWidth * targetScale).roundToInt().coerceIn(1, MAX_BITMAP_DIMENSION)
                    val renderHeight = (baseHeight * targetScale).roundToInt().coerceIn(1, MAX_BITMAP_DIMENSION)

                    val bitmap = Bitmap.createBitmap(
                        renderWidth,
                        renderHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    // Solid white background keeps dark text on transparent Pix PDFs crisp and readable
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun decodeImageFromFile(imageFile: File): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedWithImageDecoder = runCatching {
                val source = ImageDecoder.createSource(imageFile)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Software allocator is mandatory so Bitmap.compress() never fails on HARDWARE bitmaps
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false

                    val srcWidth = info.size.width
                    val srcHeight = info.size.height
                    val maxSide = max(srcWidth, srcHeight)
                    if (maxSide > MAX_BITMAP_DIMENSION && srcWidth > 0 && srcHeight > 0) {
                        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxSide.toFloat()
                        val targetW = max((srcWidth * scale).roundToInt(), 1)
                        val targetH = max((srcHeight * scale).roundToInt(), 1)
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            }.onFailure { err ->
                Log.w(TAG, "ImageDecoder failed, falling back to BitmapFactory", err)
            }.getOrNull()

            if (decodedWithImageDecoder != null) {
                return decodedWithImageDecoder
            }
        }

        // Fallback for API < 28 or edge-case files
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)

        val sampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxDimension = MAX_BITMAP_DIMENSION
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            ?: BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw ReceiptMediaLoadException("Não foi possível decodificar a imagem selecionada.")

        val maxSide = max(decoded.width, decoded.height)
        if (maxSide <= MAX_BITMAP_DIMENSION) {
            return decoded
        }

        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxSide.toFloat()
        val scaledWidth = max((decoded.width * scale).roundToInt(), 1)
        val scaledHeight = max((decoded.height * scale).roundToInt(), 1)
        val scaled = Bitmap.createScaledBitmap(decoded, scaledWidth, scaledHeight, true)
        if (scaled !== decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        val bitmapToCompress = if (bitmap.hasAlpha()) {
            val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            flattened
        } else {
            bitmap
        }

        try {
            val ok = bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (!ok || output.size() == 0) {
                throw ReceiptMediaLoadException("Falha ao preparar a imagem para envio.")
            }
            return output.toByteArray()
        } finally {
            if (bitmapToCompress !== bitmap && !bitmapToCompress.isRecycled) {
                bitmapToCompress.recycle()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sampleSize = 1
        var currentMax = max(width, height)
        while (currentMax / 2 >= maxDimension) {
            sampleSize *= 2
            currentMax /= 2
        }
        return sampleSize
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}
