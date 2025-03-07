package com.ismartcoding.plain.services

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.view.OrientationEventListener
import android.view.Surface
import androidx.core.graphics.scale
import androidx.lifecycle.LifecycleService
import com.ismartcoding.lib.channel.sendEvent
import com.ismartcoding.lib.extensions.isPortrait
import com.ismartcoding.lib.extensions.parcelable
import com.ismartcoding.lib.isQPlus
import com.ismartcoding.lib.logcat.LogCat
import com.ismartcoding.plain.BuildConfig
import com.ismartcoding.plain.R
import com.ismartcoding.plain.helpers.NotificationHelper
import com.ismartcoding.plain.mediaProjectionManager
import com.ismartcoding.plain.web.websocket.EventType
import com.ismartcoding.plain.web.websocket.WebSocketEvent
import java.io.ByteArrayOutputStream
import java.util.*

class ScreenMirrorService : LifecycleService() {
    private var widthPortrait = 720
    private var heightPortrait = 1280
    private var widthLandscape = 1280
    private var heightLandscape = 720
    private var mScreenDensity = 0
    private var mResultCode = 0
    private var mResultData: Intent? = null
    private var mBitmap: Bitmap? = null
    private lateinit var orientationEventListener: OrientationEventListener
    private var isPortrait = true

    private var mMediaProjection: MediaProjection? = null
    private var mImageReaderPortrait: ImageReader? = null
    private var mImageReaderLanscape: ImageReader? = null
    private var mImageReaderHandlerThread: HandlerThread? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var handler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationHelper.ensureDefaultChannel()
        isPortrait = isPortrait()
        val metrics = resources.displayMetrics
        if (isPortrait) {
            widthPortrait = metrics.widthPixels
            heightPortrait = metrics.heightPixels
        } else {
            widthLandscape = metrics.heightPixels
            heightLandscape = metrics.widthPixels
        }
        mScreenDensity = metrics.densityDpi
        orientationEventListener =
            object : OrientationEventListener(this) {
                override fun onOrientationChanged(orientation: Int) {
                    val newIsPortrait = isPortrait()
                    if (isPortrait != newIsPortrait) {
                        isPortrait = newIsPortrait
                        resize()
                    }
                }
            }
        val notification =
            NotificationHelper.createServiceNotification(
                this,
                "${BuildConfig.APPLICATION_ID}.action.stop_screen_mirror",
                getString(R.string.screen_mirror_service_is_running),
            )
        if (isQPlus()) {
            startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(3, notification)
        }
    }

    @SuppressLint("WrongConstant")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            mResultCode = intent.getIntExtra("code", -1)
            mResultData = intent.parcelable("data")
        }

        mImageReaderHandlerThread = HandlerThread("ImageReader")
        mImageReaderHandlerThread?.start()
        handler = Handler(mImageReaderHandlerThread!!.looper)
        orientationEventListener.enable()
        doMirror()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
        mImageReaderHandlerThread?.quitSafely()
        mBitmap = null
        orientationEventListener.disable()
    }

    private fun bitmapToBase64Image(
        bitmap: Bitmap,
        width: Int,
        height: Int,
    ): String {
        val maxSize = 100 * 1024 // 100KB
        val maxWidth = 500
        val needCompress =
            if (isPortrait) {
                width > maxWidth
            } else {
                height > maxWidth
            }

        var newBitmap = bitmap
        if (needCompress) {
            val scaleRatio = if (isPortrait) maxWidth.toFloat() / width.toFloat() else maxWidth.toFloat() / height.toFloat()
            val newWidth = (width * scaleRatio).toInt()
            val newHeight = (height * scaleRatio).toInt()
            newBitmap = bitmap.scale(newWidth, newHeight, true)
        }

        val outputStream = ByteArrayOutputStream()
        var quality = 40
        newBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        while (outputStream.toByteArray().size > maxSize && quality > 0) {
            outputStream.reset()
            quality -= 5
            newBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }

        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64String"
    }

    private fun resize() {
        val width =
            if (isPortrait) {
                widthPortrait
            } else {
                widthLandscape
            }
        val height =
            if (isPortrait) {
                heightPortrait
            } else {
                heightLandscape
            }

        mVirtualDisplay?.surface = if (isPortrait) mImageReaderPortrait!!.surface else mImageReaderLanscape!!.surface
        mVirtualDisplay?.resize(width, height, mScreenDensity)
    }

    private fun doMirror() {
        mMediaProjection = mediaProjectionManager.getMediaProjection(mResultCode, mResultData!!)
        val width =
            if (isPortrait) {
                widthPortrait
            } else {
                widthLandscape
            }
        val height =
            if (isPortrait) {
                heightPortrait
            } else {
                heightLandscape
            }
        mImageReaderPortrait = ImageReader.newInstance(widthPortrait, heightPortrait, PixelFormat.RGBA_8888, 2)
        mImageReaderLanscape = ImageReader.newInstance(widthLandscape, heightLandscape, PixelFormat.RGBA_8888, 2)
        mMediaProjection?.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                }
            },
            null,
        )
        mVirtualDisplay =
            mMediaProjection?.createVirtualDisplay(
                "ScreenMirroringService", width, height, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                if (isPortrait) mImageReaderPortrait!!.surface else mImageReaderLanscape!!.surface,
                object : VirtualDisplay.Callback() {
                },
                null,
            )

        mImageReaderPortrait?.setOnImageAvailableListener({
            try {
                val image = it.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val pixelStride = planes[0].pixelStride
                    val buffer = planes[0].buffer
                    val rowStride = planes[0].rowStride
                    val newWidth = rowStride / pixelStride

                    mBitmap = Bitmap.createBitmap(newWidth, heightPortrait, Bitmap.Config.ARGB_8888)
                    mBitmap?.copyPixelsFromBuffer(buffer)
                    if (mBitmap != null && instance != null && isPortrait) {
                        sendEvent(
                            WebSocketEvent(EventType.SCREEN_MIRRORING, bitmapToBase64Image(mBitmap!!, newWidth, heightPortrait), false),
                        )
                    }
                    image.close()
                }
            } catch (ex: Exception) {
                LogCat.e(ex)
            }
        }, handler!!)

        mImageReaderLanscape?.setOnImageAvailableListener({
            try {
                val image = it.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val pixelStride = planes[0].pixelStride
                    val buffer = planes[0].buffer
                    val rowStride = planes[0].rowStride
                    val newWidth = rowStride / pixelStride

                    mBitmap = Bitmap.createBitmap(newWidth, heightLandscape, Bitmap.Config.ARGB_8888)
                    mBitmap?.copyPixelsFromBuffer(buffer)
                    if (mBitmap != null && instance != null && !isPortrait) {
                        sendEvent(
                            WebSocketEvent(EventType.SCREEN_MIRRORING, bitmapToBase64Image(mBitmap!!, newWidth, heightLandscape), false),
                        )
                    }
                    image.close()
                }
            } catch (ex: Exception) {
                LogCat.e(ex)
            }
        }, handler!!)
    }

    private fun release() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay?.release()
            mVirtualDisplay = null
        }
        mImageReaderPortrait?.setOnImageAvailableListener(null, null)
        mImageReaderLanscape?.setOnImageAvailableListener(null, null)
        mImageReaderPortrait = null
        mImageReaderLanscape = null
        if (mMediaProjection != null) {
            mMediaProjection?.stop()
            mMediaProjection = null
        }
    }

    fun stop() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun getLatestImageBase64(): String {
        if (mBitmap == null) {
            return ""
        }

        val outputStream = ByteArrayOutputStream()
        mBitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64String"
    }

    companion object {
        var instance: ScreenMirrorService? = null
    }
}
