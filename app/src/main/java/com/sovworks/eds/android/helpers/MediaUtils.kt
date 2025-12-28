package com.sovworks.eds.android.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import android.os.Build
import android.util.Log
import com.sovworks.eds.fs.File
import java.io.ByteArrayOutputStream

object MediaUtils {
    private const val TAG = "MediaUtils"
    private const val THUMBNAIL_SIZE = 256

    fun createThumbnail(file: File): ByteArray? {
        return try {
            val fileName = file.getName().lowercase()
            val bitmap = when {
                isImage(fileName) -> createImageThumbnail(file)
                isVideo(fileName) -> createVideoThumbnail(file)
                else -> null
            }
            bitmap?.let { compressBitmap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating thumbnail for ${file.getName()}", e)
            null
        }
    }

    private fun isImage(fileName: String): Boolean {
        return listOf("jpg", "jpeg", "png", "webp", "gif").any { fileName.endsWith(it) }
    }

    private fun isVideo(fileName: String): Boolean {
        return listOf("mp4", "mkv", "avi", "mov", "3gp").any { fileName.endsWith(it) }
    }

    private fun createImageThumbnail(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        file.getInputStream().use { BitmapFactory.decodeStream(it, null, options) }
        
        options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
        options.inJustDecodeBounds = false
        
        return file.getInputStream().use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun createVideoThumbnail(file: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val retriever = MediaMetadataRetriever()
            return try {
                val rao = file.getRandomAccessIO(File.AccessMode.Read)
                retriever.setDataSource(object : MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        rao.seek(position)
                        return rao.read(buffer, offset, size)
                    }

                    override fun getSize(): Long = try {
                        file.getSize()
                    } catch (e: Exception) {
                        -1L
                    }

                    override fun close() = rao.close()
                })
                retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create video thumbnail for ${file.getName()}", e)
                null
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                }
            }
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val result = stream.toByteArray()
        bitmap.recycle()
        return result
    }
}
