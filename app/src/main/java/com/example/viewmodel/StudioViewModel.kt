package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoFitResult
import com.example.model.BackgroundSwatch
import com.example.model.DimensionPreset
import com.example.model.EditorToolTab
import com.example.model.PaperSize
import com.example.model.PhotoEdits
import com.example.model.PrintLayoutCalculator
import com.example.model.PrintQueueItem
import com.example.model.SheetPage
import com.example.model.StudioWorkspaceTab
import com.example.processing.BackgroundRemover
import com.example.processing.ExportAndPrintManager
import com.example.processing.ExportResult
import com.example.processing.StudioImageRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudioUiState(
  val activeWorkspace: StudioWorkspaceTab = StudioWorkspaceTab.EDITOR,
  val activeToolTab: EditorToolTab = EditorToolTab.DIMENSIONS,
  val sourceBitmap: Bitmap? = null,
  val foregroundCutoutBitmap: Bitmap? = null,
  val isSamplePhoto: Boolean = true,
  val isLoadingPhoto: Boolean = false,
  val isRemovingBackground: Boolean = false,
  val isExporting: Boolean = false,
  val edits: PhotoEdits = PhotoEdits(),
  val customWidthText: String = "35",
  val customHeightText: String = "45",
  val selectedPaper: PaperSize = PaperSize.PAPER_4X6,
  val forcePortrait: Boolean? = null, // null = Auto Best Fit
  val printQueue: List<PrintQueueItem> = emptyList(),
  val sheetPages: List<SheetPage> = emptyList(),
  val sheetPreviewBitmaps: List<Bitmap> = emptyList(),
  val selectedSheetPageIndex: Int = 0,
  val lastExportResult: ExportResult? = null,
  val snackbarMessage: String? = null,
) {
  val currentAutoFit: AutoFitResult
    get() {
      val refW = printQueue.firstOrNull()?.widthMm ?: edits.widthMm
      val refH = printQueue.firstOrNull()?.heightMm ?: edits.heightMm
      return PrintLayoutCalculator.calculateBestFit(
        paper = selectedPaper,
        photoWidthMm = refW,
        photoHeightMm = refH,
      )
    }

  val totalQueuedCopies: Int
    get() = printQueue.sumOf { it.copies }
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(StudioUiState())
  val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

  init {
    loadSamplePortrait()
  }

  fun setWorkspaceTab(tab: StudioWorkspaceTab) {
    _uiState.update { it.copy(activeWorkspace = tab) }
  }

  fun setToolTab(tab: EditorToolTab) {
    _uiState.update { it.copy(activeToolTab = tab) }
  }

  fun clearSnackbar() {
    _uiState.update { it.copy(snackbarMessage = null) }
  }

  fun loadSamplePortrait() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingPhoto = true) }
      try {
        val sample = StudioImageRenderer.loadSamplePortrait(getApplication())
        _uiState.update {
          it.copy(
            sourceBitmap = sample,
            foregroundCutoutBitmap = null,
            isSamplePhoto = true,
            isLoadingPhoto = false,
            edits =
              it.edits.copy(
                zoomPercent = 100f,
                offsetX = 0f,
                offsetY = 0f,
                bgSwatch = BackgroundSwatch.ORIGINAL,
              ),
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoadingPhoto = false,
            snackbarMessage = "Failed to load sample portrait: ${e.message}",
          )
        }
      }
    }
  }

  fun loadPhotoFromUri(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingPhoto = true) }
      try {
        val bitmap = StudioImageRenderer.loadBitmapFromUri(getApplication(), uri)
        if (bitmap == null) {
          _uiState.update {
            it.copy(isLoadingPhoto = false, snackbarMessage = "Could not decode selected image")
          }
          return@launch
        }
        _uiState.update {
          it.copy(
            sourceBitmap = bitmap,
            foregroundCutoutBitmap = null,
            isSamplePhoto = false,
            isLoadingPhoto = false,
            edits =
              it.edits.copy(
                zoomPercent = 100f,
                offsetX = 0f,
                offsetY = 0f,
                bgSwatch = BackgroundSwatch.ORIGINAL,
              ),
            snackbarMessage = "Photo loaded (${bitmap.width}×${bitmap.height} px)",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoadingPhoto = false,
            snackbarMessage = "Error loading photo: ${e.message}",
          )
        }
      }
    }
  }

  fun selectDimensionPreset(preset: DimensionPreset) {
    _uiState.update { state ->
      if (preset == DimensionPreset.CUSTOM) {
        state.copy(edits = state.edits.copy(preset = DimensionPreset.CUSTOM))
      } else {
        state.copy(
          edits =
            state.edits.copy(
              preset = preset,
              widthMm = preset.widthMm,
              heightMm = preset.heightMm,
            ),
          customWidthText = formatMm(preset.widthMm),
          customHeightText = formatMm(preset.heightMm),
        )
      }
    }
  }

  fun updateCustomWidth(text: String) {
    val parsed = text.toFloatOrNull()
    _uiState.update { state ->
      val validMm =
        if (parsed != null && parsed in 10f..200f) parsed else state.edits.widthMm
      state.copy(
        customWidthText = text,
        edits =
          state.edits.copy(
            preset = DimensionPreset.CUSTOM,
            widthMm = validMm,
          ),
      )
    }
  }

  fun updateCustomHeight(text: String) {
    val parsed = text.toFloatOrNull()
    _uiState.update { state ->
      val validMm =
        if (parsed != null && parsed in 10f..280f) parsed else state.edits.heightMm
      state.copy(
        customHeightText = text,
        edits =
          state.edits.copy(
            preset = DimensionPreset.CUSTOM,
            heightMm = validMm,
          ),
      )
    }
  }

  fun updateZoom(zoomPercent: Float) {
    _uiState.update {
      it.copy(edits = it.edits.copy(zoomPercent = zoomPercent.coerceIn(50f, 200f)))
    }
  }

  fun updateOffset(offsetX: Float, offsetY: Float) {
    _uiState.update {
      it.copy(
        edits =
          it.edits.copy(
            offsetX = offsetX.coerceIn(-1f, 1f),
            offsetY = offsetY.coerceIn(-1f, 1f),
          )
      )
    }
  }

  fun addPanDelta(deltaXNormalized: Float, deltaYNormalized: Float) {
    _uiState.update { state ->
      val nextX = (state.edits.offsetX + deltaXNormalized).coerceIn(-1f, 1f)
      val nextY = (state.edits.offsetY + deltaYNormalized).coerceIn(-1f, 1f)
      state.copy(edits = state.edits.copy(offsetX = nextX, offsetY = nextY))
    }
  }

  fun resetFraming() {
    _uiState.update {
      it.copy(edits = it.edits.copy(zoomPercent = 100f, offsetX = 0f, offsetY = 0f))
    }
  }

  fun toggleAlignmentGuide() {
    _uiState.update {
      it.copy(edits = it.edits.copy(showAlignmentGuide = !it.edits.showAlignmentGuide))
    }
  }

  fun triggerBackgroundRemoval(targetSwatchAfter: BackgroundSwatch? = null) {
    val source = _uiState.value.sourceBitmap ?: return
    if (_uiState.value.isRemovingBackground) return

    // If cutout already cached, just apply the requested swatch
    if (_uiState.value.foregroundCutoutBitmap != null) {
      val nextSwatch =
        targetSwatchAfter
          ?: if (_uiState.value.edits.bgSwatch == BackgroundSwatch.ORIGINAL) {
            BackgroundSwatch.PURE_WHITE
          } else {
            _uiState.value.edits.bgSwatch
          }
      _uiState.update { it.copy(edits = it.edits.copy(bgSwatch = nextSwatch)) }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isRemovingBackground = true) }
      try {
        val cutout = BackgroundRemover.removeBackground(source)
        val nextSwatch = targetSwatchAfter ?: BackgroundSwatch.PURE_WHITE
        _uiState.update {
          it.copy(
            foregroundCutoutBitmap = cutout,
            isRemovingBackground = false,
            edits = it.edits.copy(bgSwatch = nextSwatch),
            snackbarMessage = "AI background removed on-device",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isRemovingBackground = false,
            snackbarMessage = "Background removal failed: ${e.message}",
          )
        }
      }
    }
  }

  fun selectBackgroundSwatch(swatch: BackgroundSwatch) {
    if (swatch == BackgroundSwatch.ORIGINAL) {
      _uiState.update { it.copy(edits = it.edits.copy(bgSwatch = BackgroundSwatch.ORIGINAL)) }
      return
    }
    if (_uiState.value.foregroundCutoutBitmap == null) {
      triggerBackgroundRemoval(targetSwatchAfter = swatch)
    } else {
      _uiState.update { it.copy(edits = it.edits.copy(bgSwatch = swatch)) }
    }
  }

  fun updateCustomBgColor(color: Color) {
    if (_uiState.value.foregroundCutoutBitmap == null) {
      _uiState.update { it.copy(edits = it.edits.copy(customBgColor = color)) }
      triggerBackgroundRemoval(targetSwatchAfter = BackgroundSwatch.CUSTOM)
    } else {
      _uiState.update {
        it.copy(
          edits = it.edits.copy(bgSwatch = BackgroundSwatch.CUSTOM, customBgColor = color)
        )
      }
    }
  }

  fun updateBrightness(value: Float) {
    _uiState.update {
      it.copy(edits = it.edits.copy(brightness = value.coerceIn(-50f, 50f)))
    }
  }

  fun updateContrast(value: Float) {
    _uiState.update {
      it.copy(edits = it.edits.copy(contrast = value.coerceIn(0.5f, 1.8f)))
    }
  }

  fun updateSaturation(value: Float) {
    _uiState.update {
      it.copy(edits = it.edits.copy(saturation = value.coerceIn(0f, 2f)))
    }
  }

  fun applyAutoStudioRetouch() {
    _uiState.update {
      it.copy(
        edits = it.edits.copy(brightness = 10f, contrast = 1.14f, saturation = 1.08f),
        snackbarMessage = "Auto Studio Retouch applied",
      )
    }
  }

  fun resetRetouch() {
    _uiState.update {
      it.copy(edits = it.edits.copy(brightness = 0f, contrast = 1f, saturation = 1f))
    }
  }

  fun setShowNameStrip(enabled: Boolean) {
    _uiState.update { state ->
      val defaultDate =
        if (enabled && state.edits.photoDate.isBlank()) {
          SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        } else {
          state.edits.photoDate
        }
      state.copy(
        edits = state.edits.copy(showNameStrip = enabled, photoDate = defaultDate)
      )
    }
  }

  fun updatePersonName(name: String) {
    _uiState.update { it.copy(edits = it.edits.copy(personName = name)) }
  }

  fun updatePhotoDate(date: String) {
    _uiState.update { it.copy(edits = it.edits.copy(photoDate = date)) }
  }

  fun fillTodayDate() {
    val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
    _uiState.update { it.copy(edits = it.edits.copy(photoDate = today)) }
  }

  fun addCurrentPhotoToQueue(navigateToSheet: Boolean = false) {
    val state = _uiState.value
    val source = state.sourceBitmap ?: return

    viewModelScope.launch {
      val tile =
        withContext(Dispatchers.Default) {
          StudioImageRenderer.renderCroppedTile300Dpi(
            sourceBitmap = source,
            foregroundCutout = state.foregroundCutoutBitmap,
            edits = state.edits,
          )
        }

      val label =
        when {
          state.edits.personName.isNotBlank() -> state.edits.personName.trim()
          else -> "Customer ${state.printQueue.size + 1}"
        }

      val defaultCopies =
        if (state.printQueue.isEmpty()) {
          PrintLayoutCalculator.calculateBestFit(
              paper = state.selectedPaper,
              photoWidthMm = state.edits.widthMm,
              photoHeightMm = state.edits.heightMm,
            )
            .maxCopiesPerSheet
        } else {
          4
        }

      val newItem =
        PrintQueueItem(
          id = UUID.randomUUID().toString(),
          label = label,
          tileBitmap = tile,
          widthMm = state.edits.widthMm,
          heightMm = state.edits.heightMm,
          copies = defaultCopies,
        )

      val updatedQueue = state.printQueue + newItem
      recomputeSheets(
        updatedQueue = updatedQueue,
        paper = state.selectedPaper,
        forcePortrait = state.forcePortrait,
        nextWorkspace =
          if (navigateToSheet) StudioWorkspaceTab.PRINT_SHEET else state.activeWorkspace,
        message = "Added \"$label\" ($defaultCopies× ${formatMm(newItem.widthMm)}×${formatMm(newItem.heightMm)} mm) to print queue",
      )
    }
  }

  fun selectPaper(paper: PaperSize) {
    val state = _uiState.value
    recomputeSheets(
      updatedQueue = state.printQueue,
      paper = paper,
      forcePortrait = state.forcePortrait,
    )
  }

  fun setPaperOrientation(forcePortrait: Boolean?) {
    val state = _uiState.value
    recomputeSheets(
      updatedQueue = state.printQueue,
      paper = state.selectedPaper,
      forcePortrait = forcePortrait,
    )
  }

  fun updateQueueItemCopies(itemId: String, newCopies: Int) {
    val clamped = newCopies.coerceIn(1, 100)
    val state = _uiState.value
    val updatedQueue =
      state.printQueue.map { item ->
        if (item.id == itemId) item.copy(copies = clamped) else item
      }
    recomputeSheets(
      updatedQueue = updatedQueue,
      paper = state.selectedPaper,
      forcePortrait = state.forcePortrait,
    )
  }

  fun removeQueueItem(itemId: String) {
    val state = _uiState.value
    val updatedQueue = state.printQueue.filterNot { it.id == itemId }
    recomputeSheets(
      updatedQueue = updatedQueue,
      paper = state.selectedPaper,
      forcePortrait = state.forcePortrait,
    )
  }

  fun clearPrintQueue() {
    recomputeSheets(
      updatedQueue = emptyList(),
      paper = _uiState.value.selectedPaper,
      forcePortrait = _uiState.value.forcePortrait,
      message = "Print queue cleared",
    )
  }

  fun autoFillSingleSheet() {
    val state = _uiState.value
    if (state.printQueue.isEmpty()) {
      addCurrentPhotoToQueue(navigateToSheet = false)
      return
    }
    val autoFit = state.currentAutoFit
    val itemCount = state.printQueue.size
    val perItem = (autoFit.maxCopiesPerSheet / itemCount).coerceAtLeast(1)
    val remainder = autoFit.maxCopiesPerSheet - (perItem * itemCount)
    val updatedQueue =
      state.printQueue.mapIndexed { idx, item ->
        val extra = if (idx < remainder) 1 else 0
        item.copy(copies = perItem + extra)
      }
    recomputeSheets(
      updatedQueue = updatedQueue,
      paper = state.selectedPaper,
      forcePortrait = state.forcePortrait,
      message =
        "Auto-filled ${autoFit.maxCopiesPerSheet} photos (${autoFit.cols}×${autoFit.rows}) onto ${state.selectedPaper.label}",
    )
  }

  fun selectSheetPage(index: Int) {
    _uiState.update { state ->
      val safeIdx = index.coerceIn(0, (state.sheetPages.size - 1).coerceAtLeast(0))
      state.copy(selectedSheetPageIndex = safeIdx)
    }
  }

  private fun recomputeSheets(
    updatedQueue: List<PrintQueueItem>,
    paper: PaperSize,
    forcePortrait: Boolean?,
    nextWorkspace: StudioWorkspaceTab = _uiState.value.activeWorkspace,
    message: String? = null,
  ) {
    viewModelScope.launch {
      val pages =
        withContext(Dispatchers.Default) {
          PrintLayoutCalculator.packQueueOntoSheets(
            queue = updatedQueue,
            paper = paper,
            forcePortrait = forcePortrait,
          )
        }
      val previews =
        withContext(Dispatchers.Default) {
          pages.map { page ->
            // Render 150 DPI preview for fast, sharp UI rendering
            StudioImageRenderer.renderSheetBitmap(page, dpiScale = 0.45f)
          }
        }
      _uiState.update { state ->
        state.copy(
          activeWorkspace = nextWorkspace,
          selectedPaper = paper,
          forcePortrait = forcePortrait,
          printQueue = updatedQueue,
          sheetPages = pages,
          sheetPreviewBitmaps = previews,
          selectedSheetPageIndex =
            state.selectedSheetPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
          snackbarMessage = message ?: state.snackbarMessage,
        )
      }
    }
  }

  fun exportPdf() {
    val pages = _uiState.value.sheetPages
    if (pages.isEmpty()) {
      _uiState.update {
        it.copy(snackbarMessage = "Add at least one photo to the print queue first")
      }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isExporting = true) }
      try {
        val result = ExportAndPrintManager.exportPdf300Dpi(getApplication(), pages)
        _uiState.update {
          it.copy(
            isExporting = false,
            lastExportResult = result,
            snackbarMessage = "300 DPI PDF saved (${result.savedLocationLabel})",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isExporting = false, snackbarMessage = "PDF export failed: ${e.message}")
        }
      }
    }
  }

  fun exportJpg() {
    val pages = _uiState.value.sheetPages
    if (pages.isEmpty()) {
      _uiState.update {
        it.copy(snackbarMessage = "Add at least one photo to the print queue first")
      }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isExporting = true) }
      try {
        val result = ExportAndPrintManager.exportJpg300Dpi(getApplication(), pages)
        _uiState.update {
          it.copy(
            isExporting = false,
            lastExportResult = result,
            snackbarMessage = "300 DPI JPG saved (${result.savedLocationLabel})",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(isExporting = false, snackbarMessage = "JPG export failed: ${e.message}")
        }
      }
    }
  }

  private fun formatMm(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
  }
}
