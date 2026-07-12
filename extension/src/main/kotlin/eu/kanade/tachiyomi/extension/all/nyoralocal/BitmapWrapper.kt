package eu.kanade.tachiyomi.extension.all.nyoralocal

import android.graphics.Canvas
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.io.OutputStream
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Rect as AndroidRect

/**
 * Android-backed implementation of kotatsu-parsers' [Bitmap], so the on-device
 * extension can descramble image sources (e.g. MangaFire splits each page into a
 * shuffled grid and the parser reassembles it via drawBitmap). nyora-shared's JVM
 * host can't do this (no AWT/Android graphics), but a Mihon extension runs on
 * Android, so we just wrap android.graphics.Bitmap. Mirrors nyora-android's
 * BitmapWrapper.
 */
class BitmapWrapper private constructor(
    private val androidBitmap: AndroidBitmap,
) : Bitmap, AutoCloseable {

    private val canvas by lazy { Canvas(androidBitmap) }

    override val height: Int get() = androidBitmap.height
    override val width: Int get() = androidBitmap.width

    override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
        val android = (sourceBitmap as BitmapWrapper).androidBitmap
        canvas.drawBitmap(android, src.toAndroidRect(), dst.toAndroidRect(), null)
    }

    override fun close() = androidBitmap.recycle()

    fun compressTo(output: OutputStream) {
        androidBitmap.compress(AndroidBitmap.CompressFormat.JPEG, 90, output)
    }

    companion object {
        fun create(width: Int, height: Int) =
            BitmapWrapper(AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888))

        fun create(bitmap: AndroidBitmap) =
            BitmapWrapper(if (bitmap.isMutable) bitmap else bitmap.copy(AndroidBitmap.Config.ARGB_8888, true))

        private fun Rect.toAndroidRect() = AndroidRect(left, top, right, bottom)
    }
}
