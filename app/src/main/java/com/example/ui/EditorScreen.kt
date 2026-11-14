package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BackgroundSwatch
import com.example.model.DimensionPreset
import com.example.model.EditorToolTab
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanBright
import com.example.ui.theme.StudioCyanContainer
import com.example.ui.theme.StudioOnCyanContainer
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
  state: StudioUiState,
  viewModel: StudioViewModel,
  isWideLayout: Boolean,
  modifier: Modifier = Modifier,
) {
  var showCustomColorDialog by remember { mutableStateOf(false) }

  if (showCustomColorDialog) {
    CustomColorPickerDialog(
      initialColor = state.edits.customBgColor,
      onDismiss = { showCustomColorDialog = false },
      onColorConfirmed = { chosen ->
        showCustomColorDialog = false
        viewModel.updateCustomBgColor(chosen)
      },
    )
  }

  val previewBlock: @Composable (Modifier) -> Unit = { mod ->
    StudioPhotoPreviewCanvas(
      sourceBitmap = state.sourceBitmap,
      foregroundCutout = state.foregroundCutoutBitmap,
      edits = state.edits,
      isBusy = state.isLoadingPhoto || state.isRemovingBackground,
      busyLabel =
        if (state.isRemovingBackground) "Removing Background with AI…" else "Loading Studio Photo…",
      onPanZoomGesture = { zoomMult, dx, dy ->
        if (zoomMult != 1f) {
          viewModel.updateZoom(state.edits.zoomPercent * zoomMult)
        }
        viewModel.addPanDelta(dx, dy)
      },
      onToggleGuide = { viewModel.toggleAlignmentGuide() },
      onResetFraming = { viewModel.resetFraming() },
      modifier = mod,
    )
  }

  val controlsBlock: @Composable (Modifier) -> Unit = { mod ->
    Column(
      modifier = mod,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // 4-tab Tool Selector
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        EditorToolTab.entries.forEach { tab ->
          val selected = state.activeToolTab == tab
          val icon =
            when (tab) {
              EditorToolTab.DIMENSIONS -> Icons.Default.Crop
              EditorToolTab.BACKGROUND -> Icons.Default.Palette
              EditorToolTab.RETOUCH -> Icons.Default.Tune
              EditorToolTab.NAME_STRIP -> Icons.Default.Badge
            }
          FilterChip(
            selected = selected,
            onClick = { viewModel.setToolTab(tab) },
            label = { Text(tab.label) },
            leadingIcon = {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                containerColor = StudioSurface,
                labelColor = StudioTextSecondary,
                iconColor = StudioTextSecondary,
                selectedContainerColor = StudioCyanContainer,
                selectedLabelColor = StudioOnCyanContainer,
                selectedLeadingIconColor = StudioCyanBright,
              ),
            border =
              FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = StudioBorder,
                selectedBorderColor = StudioCyan,
              ),
            modifier = Modifier.testTag("tool_tab_${tab.name.lowercase()}"),
          )
        }
      }

      // Active Tool Card
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          when (state.activeToolTab) {
            EditorToolTab.DIMENSIONS -> DimensionsPanel(state, viewModel)
            EditorToolTab.BACKGROUND ->
              BackgroundPanel(
                state = state,
                onRemoveBg = { viewModel.triggerBackgroundRemoval() },
                onSelectSwatch = { viewModel.selectBackgroundSwatch(it) },
                onOpenCustomPicker = { showCustomColorDialog = true },
              )
            EditorToolTab.RETOUCH -> RetouchPanel(state, viewModel)
            EditorToolTab.NAME_STRIP -> NameStripPanel(state, viewModel)
          }
        }
      }
    }
  }

  if (isWideLayout) {
    Row(
      modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      previewBlock(Modifier.weight(1f))
      Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        controlsBlock(Modifier.fillMaxWidth())
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
      previewBlock(Modifier.fillMaxWidth())
      controlsBlock(Modifier.fillMaxWidth())
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun DimensionsPanel(state: StudioUiState, viewModel: StudioViewModel) {
  Text(
    text = "Photo Dimensions & Framing",
    style = MaterialTheme.typography.titleMedium,
    color = StudioTextPrimary,
  )

  // Presets row
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    DimensionPreset.entries.forEach { preset ->
      val selected = state.edits.preset == preset
      Surface(
        modifier =
          Modifier.clip(RoundedCornerShape(12.dp))
            .clickable { viewModel.selectDimensionPreset(preset) }
            .testTag("preset_${preset.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) StudioCyanContainer else StudioSurfaceElevated,
        border = BorderStroke(1.dp, if (selected) StudioCyan else StudioBorder),
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Text(
            text = preset.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) StudioOnCyanContainer else StudioTextPrimary,
          )
          Text(
            text = preset.subtitle,
            style = MonoTextStyle,
            color = if (selected) StudioCyanBright else StudioTextSecondary,
          )
        }
      }
    }
  }

  // Custom W x H inputs in mm
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedTextField(
      value = state.customWidthText,
      onValueChange = { viewModel.updateCustomWidth(it) },
      label = { Text("Width (mm)") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = studioTextFieldColors(),
      modifier = Modifier.weight(1f).testTag("custom_width_input"),
    )
    OutlinedTextField(
      value = state.customHeightText,
      onValueChange = { viewModel.updateCustomHeight(it) },
      label = { Text("Height (mm)") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      colors = studioTextFieldColors(),
      modifier = Modifier.weight(1f).testTag("custom_height_input"),
    )
  }

  StudioSlider(
    label = "Zoom",
    valueText = "${state.edits.zoomPercent.roundToInt()}%",
    value = state.edits.zoomPercent,
    valueRange = 50f..200f,
    onValueChange = { viewModel.updateZoom(it) },
    icon = Icons.Default.ZoomIn,
    testTag = "zoom_slider",
  )

  StudioSlider(
    label = "Horizontal Position",
    valueText = "${(state.edits.offsetX * 100f).roundToInt()}%",
    value = state.edits.offsetX,
    valueRange = -1f..1f,
    onValueChange = { viewModel.updateOffset(it, state.edits.offsetY) },
    icon = Icons.Default.SwapHoriz,
    testTag = "pan_x_slider",
  )

  StudioSlider(
    label = "Vertical Position",
    valueText = "${(state.edits.offsetY * 100f).roundToInt()}%",
    value = state.edits.offsetY,
    valueRange = -1f..1f,
    onValueChange = { viewModel.updateOffset(state.edits.offsetX, it) },
    icon = Icons.Default.SwapVert,
    testTag = "pan_y_slider",
  )
}

@Composable
private fun BackgroundPanel(
  state: StudioUiState,
  onRemoveBg: () -> Unit,
  onSelectSwatch: (BackgroundSwatch) -> Unit,
  onOpenCustomPicker: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "On-Device AI Backdrop",
        style = MaterialTheme.typography.titleMedium,
        color = StudioTextPrimary,
      )
      Text(
        text =
          if (state.foregroundCutoutBitmap != null) "Cutout ready — tap any color swatch"
          else "Runs 100% offline inside your device",
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (state.foregroundCutoutBitmap != null) StudioSuccess else StudioTextSecondary,
      )
    }
  }

  Button(
    onClick = onRemoveBg,
    enabled = !state.isRemovingBackground && state.sourceBitmap != null,
    colors =
      ButtonDefaults.buttonColors(
        containerColor = StudioCyan,
        contentColor = Color.White,
      ),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("remove_bg_button"),
  ) {
    Icon(
      imageVector = Icons.Default.AutoFixHigh,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text =
        if (state.foregroundCutoutBitmap != null) "Re-Run AI Background Removal"
        else "AI Remove Background",
      style = MaterialTheme.typography.labelLarge,
    )
  }

  Text(
    text = "Studio Backdrop Swatches",
    style = MaterialTheme.typography.labelLarge,
    color = StudioTextSecondary,
  )

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BackgroundSwatch.entries.forEach { swatch ->
      val selected = state.edits.bgSwatch == swatch
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
          Modifier.clip(RoundedCornerShape(10.dp))
            .clickable {
              if (swatch == BackgroundSwatch.CUSTOM) {
                onOpenCustomPicker()
              } else {
                onSelectSwatch(swatch)
              }
            }
            .padding(4.dp)
            .testTag("swatch_${swatch.name.lowercase()}"),
      ) {
        val circleMod =
          Modifier.size(44.dp)
            .clip(CircleShape)
            .border(
              width = if (selected) 2.5.dp else 1.dp,
              color = if (selected) StudioCyanBright else StudioBorder,
              shape = CircleShape,
            )

        when (swatch) {
          BackgroundSwatch.ORIGINAL -> {
            Box(
              modifier = circleMod.background(StudioSurfaceElevated),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.LayersClear,
                contentDescription = "Original background",
                tint = StudioTextSecondary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          BackgroundSwatch.CUSTOM -> {
            Box(
              modifier =
                circleMod.background(
                  Brush.sweepGradient(
                    listOf(
                      Color.Red,
                      Color.Yellow,
                      Color.Green,
                      Color.Cyan,
                      Color.Blue,
                      Color.Magenta,
                      Color.Red,
                    )
                  )
                ),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier.size(22.dp)
                    .clip(CircleShape)
                    .background(state.edits.customBgColor)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.ColorLens,
                  contentDescription = "Custom color picker",
                  tint = Color.White,
                  modifier = Modifier.size(12.dp),
                )
              }
            }
          }
          else -> {
            val fillColor = Color(swatch.colorInt ?: android.graphics.Color.WHITE)
            Box(
              modifier = circleMod.background(fillColor),
              contentAlignment = Alignment.Center,
            ) {
              if (selected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint =
                    if (swatch == BackgroundSwatch.PURE_WHITE ||
                      swatch == BackgroundSwatch.LIGHT_GRAY
                    ) {
                      Color(0xFF0F172A)
                    } else {
                      Color.White
                    },
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }

        Text(
          text = swatch.label,
          style = MaterialTheme.typography.labelSmall,
          color = if (selected) StudioCyanBright else StudioTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun RetouchPanel(state: StudioUiState, viewModel: StudioViewModel) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Studio Photo Enhancement",
      style = MaterialTheme.typography.titleMedium,
      color = StudioTextPrimary,
    )
    TextButton(
      onClick = { viewModel.resetRetouch() },
      modifier = Modifier.testTag("reset_retouch_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = null,
        tint = StudioTextSecondary,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text("Reset", color = StudioTextSecondary)
    }
  }

  OutlinedButton(
    onClick = { viewModel.applyAutoStudioRetouch() },
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, StudioCyan),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyanBright),
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("auto_retouch_button"),
  ) {
    Icon(
      imageVector = Icons.Default.AutoAwesome,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("1-Click Auto Studio Retouch", style = MaterialTheme.typography.labelLarge)
  }

  StudioSlider(
    label = "Brightness",
    valueText =
      if (state.edits.brightness >= 0) "+${state.edits.brightness.roundToInt()}"
      else "${state.edits.brightness.roundToInt()}",
    value = state.edits.brightness,
    valueRange = -50f..50f,
    onValueChange = { viewModel.updateBrightness(it) },
    icon = Icons.Default.Brightness6,
    testTag = "brightness_slider",
  )

  StudioSlider(
    label = "Contrast",
    valueText = "${(state.edits.contrast * 100f).roundToInt()}%",
    value = state.edits.contrast,
    valueRange = 0.5f..1.8f,
    onValueChange = { viewModel.updateContrast(it) },
    icon = Icons.Default.Contrast,
    testTag = "contrast_slider",
  )

  StudioSlider(
    label = "Saturation",
    valueText = "${(state.edits.saturation * 100f).roundToInt()}%",
    value = state.edits.saturation,
    valueRange = 0f..2f,
    onValueChange = { viewModel.updateSaturation(it) },
    icon = Icons.Default.ColorLens,
    testTag = "saturation_slider",
  )
}

@Composable
private fun NameStripPanel(state: StudioUiState, viewModel: StudioViewModel) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Name & Date Strip",
        style = MaterialTheme.typography.titleMedium,
        color = StudioTextPrimary,
      )
      Text(
        text = "White bottom label for exam & govt forms",
        style = MaterialTheme.typography.bodyMedium,
        color = StudioTextSecondary,
      )
    }
    Switch(
      checked = state.edits.showNameStrip,
      onCheckedChange = { viewModel.setShowNameStrip(it) },
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color.White,
          checkedTrackColor = StudioCyan,
          uncheckedTrackColor = StudioSurfaceElevated,
        ),
      modifier = Modifier.testTag("name_strip_switch"),
    )
  }

  OutlinedTextField(
    value = state.edits.personName,
    onValueChange = {
      if (!state.edits.showNameStrip && it.isNotBlank()) viewModel.setShowNameStrip(true)
      viewModel.updatePersonName(it)
    },
    label = { Text("Candidate / Customer Name") },
    placeholder = { Text("e.g. RAHUL SHARMA") },
    singleLine = true,
    colors = studioTextFieldColors(),
    modifier = Modifier.fillMaxWidth().testTag("person_name_input"),
  )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = state.edits.photoDate,
      onValueChange = {
        if (!state.edits.showNameStrip && it.isNotBlank()) viewModel.setShowNameStrip(true)
        viewModel.updatePhotoDate(it)
      },
      label = { Text("Date of Photo (DOP)") },
      placeholder = { Text("DD-MM-YYYY") },
      singleLine = true,
      colors = studioTextFieldColors(),
      modifier = Modifier.weight(1f).testTag("photo_date_input"),
    )
    OutlinedButton(
      onClick = {
        viewModel.setShowNameStrip(true)
        viewModel.fillTodayDate()
      },
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, StudioBorder),
      modifier = Modifier.height(52.dp).testTag("today_date_button"),
    ) {
      Icon(
        imageVector = Icons.Default.CalendarToday,
        contentDescription = "Insert today date",
        tint = StudioCyanBright,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text("Today", color = StudioTextPrimary)
    }
  }
}

@Composable
private fun studioTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StudioCyan,
    unfocusedBorderColor = StudioBorder,
    focusedLabelColor = StudioCyanBright,
    unfocusedLabelColor = StudioTextSecondary,
    focusedTextColor = StudioTextPrimary,
    unfocusedTextColor = StudioTextPrimary,
    cursorColor = StudioCyanBright,
    focusedContainerColor = StudioSurfaceElevated,
    unfocusedContainerColor = StudioSurfaceElevated,
  )
