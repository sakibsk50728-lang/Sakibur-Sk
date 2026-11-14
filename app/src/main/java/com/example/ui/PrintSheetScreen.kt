package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PaperSize
import com.example.model.PrintQueueItem
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanBright
import com.example.ui.theme.StudioCyanContainer
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioOnCyanContainer
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun PrintSheetScreen(
  state: StudioUiState,
  viewModel: StudioViewModel,
  isWideLayout: Boolean,
  modifier: Modifier = Modifier,
) {
  val sheetPreviewBlock: @Composable (Modifier) -> Unit = { mod ->
    SheetPreviewCard(
      state = state,
      onSelectPage = { viewModel.selectSheetPage(it) },
      onAddCurrentPhoto = { viewModel.addCurrentPhotoToQueue(navigateToSheet = false) },
      modifier = mod,
    )
  }

  val queueAndSettingsBlock: @Composable (Modifier) -> Unit = { mod ->
    Column(
      modifier = mod,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      PaperAndAutoFitCard(state = state, viewModel = viewModel)
      BulkPrintQueueCard(state = state, viewModel = viewModel)
    }
  }

  if (isWideLayout) {
    Row(
      modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      sheetPreviewBlock(Modifier.weight(1f))
      Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        queueAndSettingsBlock(Modifier.fillMaxWidth())
      }
    }
  } else {
    Column(
      modifier =
        modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      PaperAndAutoFitCard(state = state, viewModel = viewModel)
      sheetPreviewBlock(Modifier.fillMaxWidth())
      BulkPrintQueueCard(state = state, viewModel = viewModel)
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun PaperAndAutoFitCard(state: StudioUiState, viewModel: StudioViewModel) {
  val autoFit = state.currentAutoFit

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = StudioSurface,
    border = BorderStroke(1.dp, StudioBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Paper & Auto-Fit (300 DPI)",
          style = MaterialTheme.typography.titleMedium,
          color = StudioTextPrimary,
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = StudioSurfaceElevated,
          border = BorderStroke(1.dp, StudioBorder),
        ) {
          Text(
            text = "${state.sheetPages.size.coerceAtLeast(1)} sheet(s)",
            style = MonoTextStyle,
            color = StudioCyanBright,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      // Paper selector (4x6 vs A4)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        PaperSize.entries.forEach { paper ->
          val selected = state.selectedPaper == paper
          Surface(
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.selectPaper(paper) }
                .testTag("paper_${paper.name.lowercase()}"),
            shape = RoundedCornerShape(12.dp),
            color = if (selected) StudioCyanContainer else StudioSurfaceElevated,
            border = BorderStroke(1.dp, if (selected) StudioCyan else StudioBorder),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = paper.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) StudioOnCyanContainer else StudioTextPrimary,
              )
              Text(
                text = paper.subtitle,
                style = MonoTextStyle,
                color = if (selected) StudioCyanBright else StudioTextSecondary,
              )
            }
          }
        }
      }

      // Orientation chips
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Orientation:",
          style = MaterialTheme.typography.labelMedium,
          color = StudioTextSecondary,
        )
        val orientations = listOf(null to "Auto Fit", true to "Portrait", false to "Landscape")
        orientations.forEach { (value, label) ->
          val selected = state.forcePortrait == value
          FilterChip(
            selected = selected,
            onClick = { viewModel.setPaperOrientation(value) },
            label = { Text(label) },
            colors =
              FilterChipDefaults.filterChipColors(
                containerColor = StudioSurfaceElevated,
                labelColor = StudioTextSecondary,
                selectedContainerColor = StudioCyanContainer,
                selectedLabelColor = StudioOnCyanContainer,
              ),
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = StudioBorder,
                selectedBorderColor = StudioCyan,
              ),
          )
        }
      }

      // Smart Auto-Fit Calculator Banner
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.4f)),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Smart Auto-Fit Calculator",
              style = MaterialTheme.typography.labelLarge,
              color = StudioCyanBright,
            )
            Text(
              text =
                "${autoFit.cols} cols × ${autoFit.rows} rows = ${autoFit.maxCopiesPerSheet} max/sheet",
              style = MonoTextStyle,
              color = StudioTextPrimary,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = { viewModel.autoFillSingleSheet() },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = StudioCyan,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(40.dp).testTag("auto_fill_button"),
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesomeMotion,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Auto-Fill", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

@Composable
private fun SheetPreviewCard(
  state: StudioUiState,
  onSelectPage: (Int) -> Unit,
  onAddCurrentPhoto: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFF0D0F12),
    border = BorderStroke(1.dp, StudioBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "300 DPI Print Sheet Preview",
          style = MaterialTheme.typography.titleMedium,
          color = StudioTextPrimary,
        )
        Text(
          text = "0.5px cut borders",
          style = MonoTextStyle,
          color = StudioTextSecondary,
        )
      }

      if (state.sheetPages.isEmpty() || state.sheetPreviewBitmaps.isEmpty()) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .height(220.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(StudioSurface)
              .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            tint = StudioCyan,
            modifier = Modifier.size(40.dp),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Print Queue is Empty",
            style = MaterialTheme.typography.titleMedium,
            color = StudioTextPrimary,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Add your edited photo to auto-layout a 300 DPI sheet with hairline cutting guides.",
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(14.dp))
          Button(
            onClick = onAddCurrentPhoto,
            colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
            modifier = Modifier.height(48.dp).testTag("empty_add_photo_button"),
          ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Current Photo")
          }
        }
      } else {
        val safeIdx =
          state.selectedSheetPageIndex.coerceIn(0, state.sheetPreviewBitmaps.lastIndex)
        val previewBitmap = state.sheetPreviewBitmaps[safeIdx]
        val sheetPage = state.sheetPages[safeIdx]
        val imageBitmap = remember(previewBitmap) { previewBitmap.asImageBitmap() }
        val sheetAspect =
          (sheetPage.paperWidthMm / sheetPage.paperHeightMm).coerceIn(0.4f, 2.2f)

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(300.dp)
              .padding(4.dp),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = imageBitmap,
            contentDescription = "Print sheet page ${safeIdx + 1} preview",
            contentScale = ContentScale.Fit,
            modifier =
              Modifier.aspectRatio(sheetAspect)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                .testTag("sheet_preview_image"),
          )
        }

        if (state.sheetPages.size > 1) {
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          ) {
            state.sheetPages.forEachIndexed { idx, _ ->
              val selected = idx == safeIdx
              FilterChip(
                selected = selected,
                onClick = { onSelectPage(idx) },
                label = { Text("Page ${idx + 1}") },
                colors =
                  FilterChipDefaults.filterChipColors(
                    containerColor = StudioSurface,
                    labelColor = StudioTextSecondary,
                    selectedContainerColor = StudioCyanContainer,
                    selectedLabelColor = StudioOnCyanContainer,
                  ),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BulkPrintQueueCard(state: StudioUiState, viewModel: StudioViewModel) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = StudioSurface,
    border = BorderStroke(1.dp, StudioBorder),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Multi-Customer Print Queue",
            style = MaterialTheme.typography.titleMedium,
            color = StudioTextPrimary,
          )
          Text(
            text = "${state.printQueue.size} customer(s) · ${state.totalQueuedCopies} total prints",
            style = MaterialTheme.typography.bodyMedium,
            color = StudioTextSecondary,
          )
        }
        if (state.printQueue.isNotEmpty()) {
          TextButton(
            onClick = { viewModel.clearPrintQueue() },
            modifier = Modifier.testTag("clear_queue_button"),
          ) {
            Text("Clear", color = StudioError)
          }
        }
      }

      OutlinedButton(
        onClick = { viewModel.addCurrentPhotoToQueue(navigateToSheet = false) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, StudioCyan),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyanBright),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("queue_add_current_button"),
      ) {
        Icon(
          imageVector = Icons.Default.AddPhotoAlternate,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("+ Add Current Edited Photo to Sheet")
      }

      state.printQueue.forEach { item ->
        QueueItemRow(
          item = item,
          onDecrease = { viewModel.updateQueueItemCopies(item.id, item.copies - 1) },
          onIncrease = { viewModel.updateQueueItemCopies(item.id, item.copies + 1) },
          onRemove = { viewModel.removeQueueItem(item.id) },
        )
      }
    }
  }
}

@Composable
private fun QueueItemRow(
  item: PrintQueueItem,
  onDecrease: () -> Unit,
  onIncrease: () -> Unit,
  onRemove: () -> Unit,
) {
  val thumb = remember(item.tileBitmap) { item.tileBitmap.asImageBitmap() }

  Surface(
    modifier = Modifier.fillMaxWidth().testTag("queue_item_${item.id}"),
    shape = RoundedCornerShape(12.dp),
    color = StudioSurfaceElevated,
    border = BorderStroke(1.dp, StudioBorder),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Image(
        bitmap = thumb,
        contentDescription = item.label,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(width = 44.dp, height = 56.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp)),
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.label,
          style = MaterialTheme.typography.labelLarge,
          color = StudioTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${formatMmLabel(item.widthMm)}×${formatMmLabel(item.heightMm)} mm",
          style = MonoTextStyle,
          color = StudioCyanBright,
        )
      }

      // Copy stepper
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onDecrease,
          enabled = item.copies > 1,
          modifier = Modifier.size(48.dp).testTag("decrease_copies_${item.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.Remove,
            contentDescription = "Decrease copies",
            tint = if (item.copies > 1) StudioTextPrimary else StudioTextMuted,
          )
        }
        Text(
          text = "${item.copies}",
          style = MonoTextStyle,
          color = StudioTextPrimary,
          modifier = Modifier.width(26.dp),
          textAlign = TextAlign.Center,
        )
        IconButton(
          onClick = onIncrease,
          modifier = Modifier.size(48.dp).testTag("increase_copies_${item.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Increase copies",
            tint = StudioCyanBright,
          )
        }
      }

      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(48.dp).testTag("remove_queue_item_${item.id}"),
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = "Remove customer from queue",
          tint = StudioError,
        )
      }
    }
  }
}
