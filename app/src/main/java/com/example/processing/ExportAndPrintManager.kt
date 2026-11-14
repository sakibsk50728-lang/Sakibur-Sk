package com.example.processing

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.PrintLayoutCalculator
import com.example.model.SheetPage
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportResult(
  val fileName: String,
  val shareUri: Uri,
  val mimeType: String,
  val savedLocationLabel: String,
)

object ExportAndPrintManager {

  suspend fun exportPdf300Dpi(
    context: Context,
    pages: List<SheetPage>,
  ): ExportResult =
    withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      val fileName = "EasyStudio_Print_${timestamp}.pdf"

      val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val cacheFile = File(exportsDir, fileName)

      val document = buildPdfDocument(pages)
      try {
        FileOutputStream(cacheFile).use { out -> document.writeTo(out) }

        var savedLabel = "App Share Cache"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values =
            ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, fileName)
              put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
              put(MediaStore.Downloads.RELATIVE_PATH, "Download/EasyStudioPro")
            }
          val resolver = context.contentResolver
          val mediaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
          if (mediaUri != null) {
            resolver.openOutputStream(mediaUri)?.use { out ->
              cacheFile.inputStream().use { input -> input.copyTo(out) }
            }
            savedLabel = "Downloads/EasyStudioPro/$fileName"
          }
        }
        val shareUri =
          FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile,
          )
        ExportResult(
          fileName = fileName,
          shareUri = shareUri,
          mimeType = "application/pdf",
          savedLocationLabel = savedLabel,
        )
      } finally {
        document.close()
      }
    }

  suspend fun exportJpg300Dpi(
    context: Context,
    pages: List<SheetPage>,
  ): ExportResult =
    withContext(Dispatchers.IO) {
      val timestamp = System.currentTimeMillis()
      val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
      var firstShareUri: Uri? = null
      var firstFileName = "EasyStudio_300DPI_${timestamp}.jpg"
      var savedLabel = "Pictures/EasyStudioPro"

      pages.forEachIndexed { index, page ->
        val pageName =
          if (pages.size == 1) firstFileName
          else "EasyStudio_300DPI_${timestamp}_p${index + 1}.jpg"
        if (index == 0) firstFileName = pageName

        val sheetBitmap = StudioImageRenderer.renderSheetBitmap(page, dpiScale = 1.0f)
        try {
          val cacheFile = File(exportsDir, pageName)
          FileOutputStream(cacheFile).use { out ->
            sheetBitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
          }
          if (index == 0) {
            firstShareUri =
              FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile,
              )
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
              ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, pageName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EasyStudioPro")
              }
            val resolver = context.contentResolver
            val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (mediaUri != null) {
              resolver.openOutputStream(mediaUri)?.use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out) }
              }
              savedLabel = "Pictures/EasyStudioPro/$pageName"
            }
          } else {
            savedLabel = "Ready to Share ($pageName)"
          }
        } finally {
          sheetBitmap.recycle()
        }
      }

      ExportResult(
        fileName = firstFileName,
        shareUri =
          firstShareUri
            ?: throw IllegalStateException("No sheet pages generated for JPG export"),
        mimeType = "image/jpeg",
        savedLocationLabel =
          if (pages.size > 1) "${pages.size} sheets saved to Pictures/EasyStudioPro"
          else savedLabel,
      )
    }

  fun launchShareIntent(context: Context, result: ExportResult) {
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = result.mimeType
        putExtra(Intent.EXTRA_STREAM, result.shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    val chooser = Intent.createChooser(shareIntent, "Share ${result.fileName}")
    context.startActivity(chooser)
  }

  fun launchSystemPrint(context: Context, pages: List<SheetPage>) {
    if (pages.isEmpty()) return
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val firstPage = pages.first()
    val isLandscape = firstPage.paperWidthMm > firstPage.paperHeightMm

    val mediaSize =
      when {
        firstPage.paperWidthMm <= 160f && firstPage.paperHeightMm <= 160f ->
          if (isLandscape) PrintAttributes.MediaSize.NA_INDEX_4X6.asLandscape()
          else PrintAttributes.MediaSize.NA_INDEX_4X6
        else ->
          if (isLandscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
          else PrintAttributes.MediaSize.ISO_A4
      }

    val attributes =
      PrintAttributes.Builder()
        .setMediaSize(mediaSize)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    val adapter =
      object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback,
          extras: Bundle?,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
          }
          val info =
            PrintDocumentInfo.Builder("EasyStudio_Print_Sheet.pdf")
              .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
              .setPageCount(pages.size)
              .build()
          callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
          pageRanges: Array<out PageRange>,
          destination: ParcelFileDescriptor,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback,
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
          }
          val doc = buildPdfDocument(pages)
          try {
            FileOutputStream(destination.fileDescriptor).use { out: OutputStream ->
              doc.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            callback.onWriteFailed(e.message)
          } finally {
            doc.close()
          }
        }
      }

    printManager.print("EasyStudio Pro Print Sheet", adapter, attributes)
  }

  private fun buildPdfDocument(pages: List<SheetPage>): PdfDocument {
    val document = PdfDocument()
    val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 0.5f // 0.5pt hairline light-gray cutting border
      }

    for (page in pages) {
      val pageWidthPt = PrintLayoutCalculator.mmToPdfPoints(page.paperWidthMm).roundToInt()
      val pageHeightPt = PrintLayoutCalculator.mmToPdfPoints(page.paperHeightMm).roundToInt()
      val pageInfo =
        PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, page.pageIndex + 1).create()
      val pdfPage = document.startPage(pageInfo)
      val canvas = pdfPage.canvas
      canvas.drawColor(Color.WHITE)

      for (placement in page.placements) {
        val left = PrintLayoutCalculator.mmToPdfPoints(placement.xMm)
        val top = PrintLayoutCalculator.mmToPdfPoints(placement.yMm)
        val right = PrintLayoutCalculator.mmToPdfPoints(placement.xMm + placement.widthMm)
        val bottom = PrintLayoutCalculator.mmToPdfPoints(placement.yMm + placement.heightMm)
        val destRect = RectF(left, top, right, bottom)
        canvas.drawBitmap(placement.tileBitmap, null, destRect, tilePaint)
        canvas.drawRect(destRect, borderPaint)
      }

      document.finishPage(pdfPage)
    }
    return document
  }
}
