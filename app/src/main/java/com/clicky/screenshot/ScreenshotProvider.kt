package com.clicky.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper

class ScreenshotProvider(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplayId = 0

    fun createScreenCaptureIntent(): Intent {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun initializeMediaProjection(resultCode: Int, data: Intent) {
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)
    }

    suspend fun captureScreenshot(): Bitmap? {
        val projection = mediaProjection ?: return null

        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val reader = ImageReader.newInstance(width, height, android.graphics.ImageFormat.RGB_565, 2)
        imageReader = reader

        val virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ClickyScreenshot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )

        return waitForImage(reader)
    }

    private suspend fun waitForImage(reader: ImageReader): Bitmap? {
        return try {
            val image = reader.acquireNextImage() ?: return null
            val plane = image.planes[0]
            val buffer = plane.buffer

            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * reader.width

            val bitmap = Bitmap.createBitmap(
                reader.width + rowPadding / pixelStride,
                reader.height,
                Bitmap.Config.RGB_565
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, reader.width, reader.height)
            bitmap.recycle()
            image.close()
            cropped
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun release() {
        imageReader?.close()
        mediaProjection?.stop()
    }
}
