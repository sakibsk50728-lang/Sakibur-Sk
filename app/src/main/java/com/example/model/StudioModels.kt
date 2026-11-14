package com.example.model

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class StudioWorkspaceTab(val label: String) {
  EDITOR("Studio Editor"),
  PRINT_SHEET("Print Sheet"),
}

enum class EditorToolTab(val label: String) {
  DIMENSIONS("Size"),
  BACKGROUND("Backdrop"),
  RETOUCH("Retouch"),
  NAME_STRIP("Name & Date"),
}

enum class DimensionPreset(
  val label: String,
  val subtitle: String,
  val widthMm: Float,
  val heightMm: Float,
) {
  PASSPORT("Passport", "35 × 45 mm", 35f, 45f),
  STAMP("Stamp", "25 × 30 mm", 25f, 30f),
  VISA("Visa", "51 × 51 mm", 51f, 51f),
  CUSTOM("Custom", "Manual mm", 35f, 45f),
}

enum class BackgroundSwatch(val label: String, val colorInt: Int?) {
  ORIGINAL("Original", null),
  PURE_WHITE("Pure White", AndroidColor.parseColor("#FFFFFF")),
  STUDIO_BLUE("Studio Blue", AndroidColor.parseColor("#1D70B8")),
  SKY_BLUE("Sky Blue", AndroidColor.parseColor("#38BDF8")),
  LIGHT_GRAY("Light Gray", AndroidColor.parseColor("#E2E8F0")),
  CUSTOM("Custom", null),
}

enum class PaperSize(
  val label: String,
  val subtitle: String,
  val widthMm: Float,
  val heightMm: Float,
) {
  PAPER_4X6("4×6 inch", "101.6 × 152.4 mm", 101.6f, 152.4f),
  PAPER_A4("A4 Sheet", "210 × 297 mm", 210f, 297f),
}

data class PhotoEdits(
  val preset: DimensionPreset = DimensionPreset.PASSPORT,
  val widthMm: Float = 35f,
  val heightMm: Float = 45f,
  val zoomPercent: Float = 100f,
  val offsetX: Float = 0f,
  val offsetY: Float = 0f,
  val brightness: Float = 0f, // -50f .. +50f
  val contrast: Float = 1f, // 0.5f .. 1.8f
  val saturation: Float = 1f, // 0.0f .. 2.0f
  val bgSwatch: BackgroundSwatch = BackgroundSwatch.ORIGINAL,
  val customBgColor: Color = Color(0xFF0EA5E9),
  val showNameStrip: Boolean = false,
  val personName: String = "",
  val photoDate: String = "",
  val showAlignmentGuide: Boolean = true,
) {
  val aspectRatio: Float
    get() = (widthMm.coerceIn(10f, 300f) / heightMm.coerceIn(10f, 300f))

  val effectiveBgColorInt: Int?
    get() =
      when (bgSwatch) {
        BackgroundSwatch.ORIGINAL -> null
        BackgroundSwatch.CUSTOM -> customBgColor.toArgb()
        else -> bgSwatch.colorInt
      }
}

data class PrintQueueItem(
  val id: String,
  val label: String,
  val tileBitmap: Bitmap,
  val widthMm: Float,
  val heightMm: Float,
  val copies: Int,
)

data class SheetPlacement(
  val itemId: String,
  val tileBitmap: Bitmap,
  val xMm: Float,
  val yMm: Float,
  val widthMm: Float,
  val heightMm: Float,
)

data class SheetPage(
  val pageIndex: Int,
  val paperWidthMm: Float,
  val paperHeightMm: Float,
  val placements: List<SheetPlacement>,
)

data class AutoFitResult(
  val cols: Int,
  val rows: Int,
  val maxCopiesPerSheet: Int,
  val paperWidthMm: Float,
  val paperHeightMm: Float,
  val rotatedPaper: Boolean,
)

object PrintLayoutCalculator {
  const val DPI = 300f
  private const val MM_PER_INCH = 25.4f
  private const val POINTS_PER_INCH = 72f
  const val DEFAULT_MARGIN_MM = 3.0f
  const val DEFAULT_GAP_MM = 1.5f

  fun mmToPx300(mm: Float): Int = max(1, (mm * DPI / MM_PER_INCH).roundToInt())

  fun mmToPdfPoints(mm: Float): Float = mm * POINTS_PER_INCH / MM_PER_INCH

  fun calculateBestFit(
    paper: PaperSize,
    photoWidthMm: Float,
    photoHeightMm: Float,
    marginMm: Float = DEFAULT_MARGIN_MM,
    gapMm: Float = DEFAULT_GAP_MM,
  ): AutoFitResult {
    val safeW = photoWidthMm.coerceIn(10f, 200f)
    val safeH = photoHeightMm.coerceIn(10f, 280f)

    fun fitFor(pw: Float, ph: Float): Pair<Int, Int> {
      val availW = (pw - marginMm * 2f).coerceAtLeast(safeW)
      val availH = (ph - marginMm * 2f).coerceAtLeast(safeH)
      val cols = max(1, floor((availW + gapMm) / (safeW + gapMm)).toInt())
      val rows = max(1, floor((availH + gapMm) / (safeH + gapMm)).toInt())
      return cols to rows
    }

    val (pCols, pRows) = fitFor(paper.widthMm, paper.heightMm)
    val (lCols, lRows) = fitFor(paper.heightMm, paper.widthMm)
    val portraitTotal = pCols * pRows
    val landscapeTotal = lCols * lRows

    return if (landscapeTotal > portraitTotal) {
      AutoFitResult(
        cols = lCols,
        rows = lRows,
        maxCopiesPerSheet = landscapeTotal,
        paperWidthMm = paper.heightMm,
        paperHeightMm = paper.widthMm,
        rotatedPaper = true,
      )
    } else {
      AutoFitResult(
        cols = pCols,
        rows = pRows,
        maxCopiesPerSheet = portraitTotal,
        paperWidthMm = paper.widthMm,
        paperHeightMm = paper.heightMm,
        rotatedPaper = false,
      )
    }
  }

  fun packQueueOntoSheets(
    queue: List<PrintQueueItem>,
    paper: PaperSize,
    forcePortrait: Boolean? = null,
    marginMm: Float = DEFAULT_MARGIN_MM,
    gapMm: Float = DEFAULT_GAP_MM,
  ): List<SheetPage> {
    if (queue.isEmpty()) return emptyList()

    val first = queue.first()
    val autoFit = calculateBestFit(paper, first.widthMm, first.heightMm, marginMm, gapMm)
    val useLandscape =
      when (forcePortrait) {
        true -> false
        false -> true
        null -> autoFit.rotatedPaper
      }
    val sheetW = if (useLandscape) paper.heightMm else paper.widthMm
    val sheetH = if (useLandscape) paper.widthMm else paper.heightMm

    val pages = mutableListOf<SheetPage>()
    var currentPlacements = mutableListOf<SheetPlacement>()
    var cursorX = marginMm
    var cursorY = marginMm
    var rowMaxH = 0f

    fun flushPage() {
      if (currentPlacements.isNotEmpty()) {
        pages.add(
          SheetPage(
            pageIndex = pages.size,
            paperWidthMm = sheetW,
            paperHeightMm = sheetH,
            placements = currentPlacements,
          )
        )
        currentPlacements = mutableListOf()
      }
      cursorX = marginMm
      cursorY = marginMm
      rowMaxH = 0f
    }

    for (item in queue) {
      val itemW = item.widthMm.coerceAtLeast(10f)
      val itemH = item.heightMm.coerceAtLeast(10f)
      repeat(item.copies.coerceAtLeast(0)) {
        if (cursorX + itemW > sheetW - marginMm + 0.05f) {
          cursorX = marginMm
          cursorY += rowMaxH + gapMm
          rowMaxH = 0f
        }
        if (cursorY + itemH > sheetH - marginMm + 0.05f) {
          flushPage()
        }
        currentPlacements.add(
          SheetPlacement(
            itemId = item.id,
            tileBitmap = item.tileBitmap,
            xMm = cursorX,
            yMm = cursorY,
            widthMm = itemW,
            heightMm = itemH,
          )
        )
        cursorX += itemW + gapMm
        rowMaxH = max(rowMaxH, itemH)
      }
    }

    if (currentPlacements.isNotEmpty()) {
      flushPage()
    }

    return pages
  }
}
