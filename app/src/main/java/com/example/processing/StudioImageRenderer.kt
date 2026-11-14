package com.example.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.R
import com.example.model.PhotoEdits
import com.example.model.PrintLayoutCalculator
import com.example.model.SheetPage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StudioImageRenderer {

  suspend fun loadBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 1800,
  ): Bitmap? =
    withContext(Dispatchers.IO) {
      val boundsOptions =
        BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, boundsOptions)
      }
      if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
        return@withContext null
      }

      var sampleSize = 1
      val largest = max(boundsOptions.outWidth, boundsOptions.outHeight)
      while (largest / sampleSize > maxDimension) {
        sampleSize *= 2
      }

      val decodeOptions =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      val decoded =
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

      val orientation =
        context.contentResolver.openInputStream(uri)?.use { stream ->
          ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
          )
        } ?: ExifInterface.ORIENTATION_NORMAL

      rotateAndScaleIfNeeded(decoded, orientation, maxDimension)
    }

  suspend fun loadSamplePortrait(context: Context): Bitmap =
    withContext(Dispatchers.IO) {
      val opts =
        BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      BitmapFactory.decodeResource(context.resources, R.drawable.img_sample_portrait, opts)
    }

  private fun rotateAndScaleIfNeeded(
    bitmap: Bitmap,
    exifOrientation: Int,
    maxDimension: Int,
  ): Bitmap {
    val matrix = Matrix()
    when (exifOrientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    }

    val rotated =
      if (!matrix.isIdentity) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
          if (it != bitmap) bitmap.recycle()
        }
      } else {
        bitmap
      }

    val maxSide = max(rotated.width, rotated.height)
    return if (maxSide > maxDimension) {
      val scale = maxDimension.toFloat() / maxSide.toFloat()
      val scaledW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
      val scaledH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(rotated, scaledW, scaledH, true).also {
        if (it != rotated) rotated.recycle()
      }
    } else {
      rotated
    }
  }

  fun createRetouchColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float,
  ): ColorMatrix {
    val result = ColorMatrix()

    // 1. Saturation
    val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
    result.postConcat(satMatrix)

    // 2. Contrast + Brightness
    val translate = (-0.5f * contrast + 0.5f) * 255f + brightness * 2.0f
    val bcMatrix =
      ColorMatrix(
        floatArrayOf(
          contrast, 0f, 0f, 0f, translate,
          0f, contrast, 0f, 0f, translate,
          0f, 0f, contrast, 0f, translate,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    result.postConcat(bcMatrix)
    return result
  }

  fun renderCroppedTile300Dpi(
    sourceBitmap: Bitmap,
    foregroundCutout: Bitmap?,
    edits: PhotoEdits,
  ): Bitmap {
    val targetW = PrintLayoutCalculator.mmToPx300(edits.widthMm)
    val targetH = PrintLayoutCalculator.mmToPx300(edits.heightMm)
    val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val bgColor = edits.effectiveBgColorInt
    val activeBitmap =
      if (bgColor != null && foregroundCutout != null) {
        canvas.drawColor(bgColor)
        foregroundCutout
      } else {
        canvas.drawColor(Color.WHITE)
        sourceBitmap
      }

    // Reserve bottom strip height if Name & Date strip is active
    val hasStrip =
      edits.showNameStrip && (edits.personName.isNotBlank() || edits.photoDate.isNotBlank())
    val stripHeightPx = if (hasStrip) (targetH * 0.145f).roundToInt().coerceAtLeast(36) else 0
    val photoAreaH = (targetH - stripHeightPx).coerceAtLeast(1)

    // Cover-fit base scale into (targetW, photoAreaH), then apply user zoom (50%..200%) & pan
    val baseScale =
      max(
        targetW.toFloat() / activeBitmap.width.toFloat(),
        photoAreaH.toFloat() / activeBitmap.height.toFloat(),
      )
    val finalScale = baseScale * (edits.zoomPercent / 100f)

    val drawW = activeBitmap.width * finalScale
    val drawH = activeBitmap.height * finalScale
    val centerX = targetW / 2f + edits.offsetX * (targetW * 0.5f)
    val centerY = photoAreaH / 2f + edits.offsetY * (photoAreaH * 0.5f)

    val destRect =
      RectF(
        centerX - drawW / 2f,
        centerY - drawH / 2f,
        centerX + drawW / 2f,
        centerY + drawH / 2f,
      )

    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter =
          ColorMatrixColorFilter(
            createRetouchColorMatrix(
              brightness = edits.brightness,
              contrast = edits.contrast,
              saturation = edits.saturation,
            )
          )
      }

    canvas.save()
    canvas.clipRect(0f, 0f, targetW.toFloat(), photoAreaH.toFloat())
    canvas.drawBitmap(activeBitmap, null, destRect, paint)
    canvas.restore()

    if (hasStrip) {
      drawNameDateStrip(
        canvas = canvas,
        width = targetW.toFloat(),
        totalHeight = targetH.toFloat(),
        stripHeight = stripHeightPx.toFloat(),
        name = edits.personName.trim(),
        date = edits.photoDate.trim(),
      )
    }

    return output
  }

  private fun drawNameDateStrip(
    canvas: Canvas,
    width: Float,
    totalHeight: Float,
    stripHeight: Float,
    name: String,
    date: String,
  ) {
    val topY = totalHeight - stripHeight
    val bgPaint =
      Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
      }
    canvas.drawRect(0f, topY, width, totalHeight, bgPaint)

    val linePaint =
      Paint().apply {
        color = Color.parseColor("#CBD5E1")
        strokeWidth = max(1f, totalHeight * 0.002f)
      }
    canvas.drawLine(0f, topY, width, topY, linePaint)

    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
      }

    val centerX = width / 2f
    if (name.isNotBlank() && date.isNotBlank()) {
      val nameSize = min(stripHeight * 0.38f, width * 0.11f)
      textPaint.textSize = nameSize
      // Shrink to fit width if long name
      while (textPaint.measureText(name.uppercase()) > width * 0.90f && textPaint.textSize > 10f) {
        textPaint.textSize *= 0.9f
      }
      val nameBaseline = topY + stripHeight * 0.45f
      canvas.drawText(name.uppercase(), centerX, nameBaseline, textPaint)

      textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      textPaint.textSize = min(stripHeight * 0.30f, width * 0.09f)
      val dateBaseline = topY + stripHeight * 0.84f
      canvas.drawText("DOP: $date", centerX, dateBaseline, textPaint)
    } else {
      val singleText = if (name.isNotBlank()) name.uppercase() else "DOP: $date"
      textPaint.textSize = min(stripHeight * 0.44f, width * 0.12f)
      while (textPaint.measureText(singleText) > width * 0.90f && textPaint.textSize > 10f) {
        textPaint.textSize *= 0.9f
      }
      val metrics = textPaint.fontMetrics
      val baseline = topY + stripHeight / 2f - (metrics.ascent + metrics.descent) / 2f
      canvas.drawText(singleText, centerX, baseline, textPaint)
    }
  }

  fun renderSheetBitmap(
    page: SheetPage,
    dpiScale: Float = 1f,
  ): Bitmap {
    val fullW = PrintLayoutCalculator.mmToPx300(page.paperWidthMm)
    val fullH = PrintLayoutCalculator.mmToPx300(page.paperHeightMm)
    val outW = (fullW * dpiScale).roundToInt().coerceAtLeast(1)
    val outH = (fullH * dpiScale).roundToInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = max(1f, 1.2f * dpiScale)
      }

    for (placement in page.placements) {
      val left = (PrintLayoutCalculator.mmToPx300(placement.xMm) * dpiScale)
      val top = (PrintLayoutCalculator.mmToPx300(placement.yMm) * dpiScale)
      val w = (PrintLayoutCalculator.mmToPx300(placement.widthMm) * dpiScale)
      val h = (PrintLayoutCalculator.mmToPx300(placement.heightMm) * dpiScale)
      val rect = RectF(left, top, left + w, top + h)

      canvas.drawBitmap(placement.tileBitmap, null, rect, tilePaint)
      canvas.drawRect(rect, borderPaint)
    }

    return bitmap
  }
}
