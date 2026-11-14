package com.example.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object BackgroundRemover {

  suspend fun removeBackground(source: Bitmap): Bitmap {
    val options =
      SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()
    val segmenter = Segmentation.getClient(options)

    return try {
      val inputImage = InputImage.fromBitmap(source, 0)
      val mask = suspendCancellableCoroutine { cont ->
        segmenter
          .process(inputImage)
          .addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
          }
          .addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
          }
      }

      withContext(Dispatchers.Default) {
        val maskWidth = mask.width
        val maskHeight = mask.height
        val buffer = mask.buffer
        buffer.rewind()

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskFloats = FloatArray(maskWidth * maskHeight)
        buffer.asFloatBuffer().get(maskFloats)

        for (y in 0 until height) {
          val my = (y * maskHeight / height).coerceIn(0, maskHeight - 1)
          val rowOffset = y * width
          val maskRowOffset = my * maskWidth
          for (x in 0 until width) {
            val mx = (x * maskWidth / width).coerceIn(0, maskWidth - 1)
            val confidence = maskFloats[maskRowOffset + mx]
            val alpha = smoothStep(0.28f, 0.76f, confidence)
            val pixel = pixels[rowOffset + x]
            val origAlpha = Color.alpha(pixel)
            val finalAlpha = (origAlpha * alpha).toInt().coerceIn(0, 255)
            pixels[rowOffset + x] =
              (finalAlpha shl 24) or (pixel and 0x00FFFFFF)
          }
        }

        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
          setPixels(pixels, 0, width, 0, 0, width, height)
        }
      }
    } finally {
      segmenter.close()
    }
  }

  private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }
}
