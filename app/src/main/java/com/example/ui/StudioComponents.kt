package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PhotoEdits
import com.example.model.PrintLayoutCalculator
import com.example.processing.StudioImageRenderer
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanBright
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StudioPhotoPreviewCanvas(
  sourceBitmap: Bitmap?,
  foregroundCutout: Bitmap?,
  edits: PhotoEdits,
  isBusy: Boolean,
  busyLabel: String,
  onPanZoomGesture: (zoomMultiplier: Float, panXNorm: Float, panYNorm: Float) -> Unit,
  onToggleGuide: () -> Unit,
  onResetFraming: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val activeBitmap =
    remember(sourceBitmap, foregroundCutout, edits.effectiveBgColorInt) {
      if (edits.effectiveBgColorInt != null && foregroundCutout != null) {
        foregroundCutout
      } else {
        sourceBitmap
      }
    }

  val composeColorMatrix =
    remember(edits.brightness, edits.contrast, edits.saturation) {
      val androidMatrix =
        StudioImageRenderer.createRetouchColorMatrix(
          brightness = edits.brightness,
          contrast = edits.contrast,
          saturation = edits.saturation,
        )
      ColorMatrix(androidMatrix.array)
    }

  val tilePxW = PrintLayoutCalculator.mmToPx300(edits.widthMm)
  val tilePxH = PrintLayoutCalculator.mmToPx300(edits.heightMm)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFF0D0F12),
    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Top micro-telemetry strip
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(StudioSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            "${formatMmLabel(edits.widthMm)}×${formatMmLabel(edits.heightMm)} mm · ${tilePxW}×${tilePxH} px @ 300 DPI",
          style = MonoTextStyle,
          color = StudioCyanBright,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          IconButton(
            onClick = onToggleGuide,
            modifier = Modifier.size(36.dp).testTag("toggle_guide_button"),
          ) {
            Icon(
              imageVector =
                if (edits.showAlignmentGuide) Icons.Default.GridOn else Icons.Default.GridOff,
              contentDescription = "Toggle face alignment guide",
              tint = if (edits.showAlignmentGuide) StudioCyan else StudioTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onResetFraming,
            modifier = Modifier.size(36.dp).testTag("reset_framing_button"),
          ) {
            Icon(
              imageVector = Icons.Default.CenterFocusStrong,
              contentDescription = "Reset crop framing",
              tint = StudioTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Main photo stage
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxWidth()
            .height(280.dp)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
      ) {
        // Subtle studio drafting grid behind the photo frame
        Canvas(modifier = Modifier.fillMaxSize()) {
          val step = 20.dp.toPx()
          var x = 0f
          while (x <= size.width) {
            drawLine(
              color = Color(0xFF181C23),
              start = Offset(x, 0f),
              end = Offset(x, size.height),
              strokeWidth = 1f,
            )
            x += step
          }
          var y = 0f
          while (y <= size.height) {
            drawLine(
              color = Color(0xFF181C23),
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1f,
            )
            y += step
          }
        }

        val targetAspect = edits.aspectRatio.coerceIn(0.35f, 2.8f)
        val stageAspect = maxWidth / maxHeight
        val frameModifier =
          if (targetAspect > stageAspect) {
            Modifier.fillMaxWidth(0.88f).aspectRatio(targetAspect)
          } else {
            Modifier.fillMaxHeight(0.96f).aspectRatio(targetAspect)
          }

        val hasStrip =
          edits.showNameStrip &&
            (edits.personName.isNotBlank() || edits.photoDate.isNotBlank())
        val bgColor = edits.effectiveBgColorInt?.let { Color(it) } ?: Color.White

        BoxWithConstraints(
          modifier =
            frameModifier
              .clip(RoundedCornerShape(4.dp))
              .background(bgColor)
              .border(1.5.dp, StudioCyan.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
              .clipToBounds()
              .pointerInput(edits.widthMm, edits.heightMm) {
                detectTransformGestures { _, pan, zoom, _ ->
                  val frameW = size.width.toFloat().coerceAtLeast(1f)
                  val frameH = size.height.toFloat().coerceAtLeast(1f)
                  val normDx = (pan.x / (frameW * 0.5f))
                  val normDy = (pan.y / (frameH * 0.5f))
                  onPanZoomGesture(zoom, normDx, normDy)
                }
              }
              .testTag("studio_preview_canvas")
        ) {
          val frameW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
          val frameH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
          val stripFraction = if (hasStrip) 0.145f else 0f
          val photoAreaH = frameH * (1f - stripFraction)

          if (activeBitmap != null) {
            val imageBitmap = remember(activeBitmap) { activeBitmap.asImageBitmap() }
            val scaleFactor = edits.zoomPercent / 100f
            val baseCoverScale =
              max(
                frameW / activeBitmap.width.toFloat(),
                photoAreaH / activeBitmap.height.toFloat(),
              )
            val drawW = activeBitmap.width * baseCoverScale
            val drawH = activeBitmap.height * baseCoverScale

            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .fillMaxHeight(1f - stripFraction)
                  .clipToBounds(),
              contentAlignment = Alignment.Center,
            ) {
              Image(
                bitmap = imageBitmap,
                contentDescription = "Studio photo live preview",
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(composeColorMatrix),
                modifier =
                  Modifier.fillMaxSize().graphicsLayer {
                    // Match StudioImageRenderer cover + zoom + pan semantics
                    val scaleXRatio = (drawW / frameW) * scaleFactor
                    val scaleYRatio = (drawH / photoAreaH) * scaleFactor
                    val uniformScale = max(scaleXRatio, scaleYRatio)
                    scaleX = uniformScale
                    scaleY = uniformScale
                    translationX = edits.offsetX * (frameW * 0.5f)
                    translationY = edits.offsetY * (photoAreaH * 0.5f)
                  },
              )
            }
          }

          // Optional Name & Date strip preview
          if (hasStrip) {
            Column(
              modifier =
                Modifier.align(Alignment.BottomCenter)
                  .fillMaxWidth()
                  .fillMaxHeight(stripFraction)
                  .background(Color.White)
                  .border(0.5.dp, Color(0xFFCBD5E1)),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              if (edits.personName.isNotBlank()) {
                Text(
                  text = edits.personName.trim().uppercase(),
                  color = Color(0xFF0F172A),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  textAlign = TextAlign.Center,
                )
              }
              if (edits.photoDate.isNotBlank()) {
                Text(
                  text = "DOP: ${edits.photoDate.trim()}",
                  color = Color(0xFF0F172A),
                  fontSize = 8.sp,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }

          // Passport head-and-shoulders alignment overlay
          if (edits.showAlignmentGuide) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val w = size.width
              val h = size.height * (1f - stripFraction)
              val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

              // Center vertical axis
              drawLine(
                color = StudioCyanBright.copy(alpha = 0.55f),
                start = Offset(w / 2f, 0f),
                end = Offset(w / 2f, h),
                strokeWidth = 1.5f,
                pathEffect = dashEffect,
              )

              // Eye-level horizontal line (~42% from top)
              drawLine(
                color = StudioCyanBright.copy(alpha = 0.45f),
                start = Offset(0f, h * 0.42f),
                end = Offset(w, h * 0.42f),
                strokeWidth = 1.2f,
                pathEffect = dashEffect,
              )

              // Head oval guide
              val ovalW = w * 0.46f
              val ovalH = h * 0.54f
              drawOval(
                color = StudioCyanBright.copy(alpha = 0.50f),
                topLeft = Offset((w - ovalW) / 2f, h * 0.14f),
                size = Size(ovalW, ovalH),
                style = Stroke(width = 1.8f, pathEffect = dashEffect),
              )
            }
          }
        }

        // Busy overlay (loading or AI background removal)
        androidx.compose.animation.AnimatedVisibility(
          visible = isBusy,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(StudioBg.copy(alpha = 0.80f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(
                color = StudioCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
              )
              Text(
                text = busyLabel,
                style = MaterialTheme.typography.labelLarge,
                color = StudioTextPrimary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun StudioSlider(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
  icon: ImageVector? = null,
  testTag: String = "",
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (icon != null) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = StudioTextSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = StudioTextPrimary,
        )
      }
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = StudioSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
      ) {
        Text(
          text = valueText,
          style = MonoTextStyle,
          color = StudioCyanBright,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      colors =
        SliderDefaults.colors(
          thumbColor = StudioCyan,
          activeTrackColor = StudioCyan,
          inactiveTrackColor = StudioSurfaceElevated,
        ),
      modifier =
        if (testTag.isNotBlank()) Modifier.fillMaxWidth().testTag(testTag)
        else Modifier.fillMaxWidth(),
    )
  }
}

@Composable
fun CustomColorPickerDialog(
  initialColor: Color,
  onDismiss: () -> Unit,
  onColorConfirmed: (Color) -> Unit,
) {
  var red by remember(initialColor) { mutableFloatStateOf(initialColor.red * 255f) }
  var green by remember(initialColor) { mutableFloatStateOf(initialColor.green * 255f) }
  var blue by remember(initialColor) { mutableFloatStateOf(initialColor.blue * 255f) }

  val currentColor = Color(red.roundToInt(), green.roundToInt(), blue.roundToInt())
  val hexString =
    String.format("#%06X", 0xFFFFFF and currentColor.toArgb())

  val quickStudioPalette =
    listOf(
      Color(0xFFFFFFFF),
      Color(0xFF1D70B8),
      Color(0xFF0EA5E9),
      Color(0xFFDC2626),
      Color(0xFF16A34A),
      Color(0xFF475569),
      Color(0xFFFEF3C7),
      Color(0xFFE0E7FF),
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioSurface,
    titleContentColor = StudioTextPrimary,
    title = { Text("Custom Backdrop Color", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(currentColor)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
          )
          Column {
            Text(
              text = "Selected Color",
              style = MaterialTheme.typography.labelMedium,
              color = StudioTextSecondary,
            )
            Text(
              text = hexString,
              style = MonoTextStyle.copy(fontSize = 15.sp),
              color = StudioCyanBright,
            )
          }
        }

        // Quick palette pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          quickStudioPalette.forEach { preset ->
            Box(
              modifier =
                Modifier.size(28.dp)
                  .clip(CircleShape)
                  .background(preset)
                  .border(1.dp, StudioBorder, CircleShape)
                  .clickable {
                    red = preset.red * 255f
                    green = preset.green * 255f
                    blue = preset.blue * 255f
                  }
            )
          }
        }

        StudioSlider(
          label = "Red",
          valueText = red.roundToInt().toString(),
          value = red,
          valueRange = 0f..255f,
          onValueChange = { red = it },
        )
        StudioSlider(
          label = "Green",
          valueText = green.roundToInt().toString(),
          value = green,
          valueRange = 0f..255f,
          onValueChange = { green = it },
        )
        StudioSlider(
          label = "Blue",
          valueText = blue.roundToInt().toString(),
          value = blue,
          valueRange = 0f..255f,
          onValueChange = { blue = it },
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onColorConfirmed(currentColor) },
        modifier = Modifier.testTag("confirm_custom_color_button"),
      ) {
        Text("Apply Color")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = StudioTextSecondary)
      }
    },
  )
}

fun formatMmLabel(value: Float): String {
  return if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)
}
