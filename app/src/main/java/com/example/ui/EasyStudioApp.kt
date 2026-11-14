package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.StudioWorkspaceTab
import com.example.processing.ExportAndPrintManager
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioCyanBright
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel
import java.io.File

@Composable
fun EasyStudioApp(viewModel: StudioViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

  // Zero-permission Android Photo Picker
  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.loadPhotoFromUri(uri)
      }
    }

  // Zero-permission system Camera intent via FileProvider
  val cameraLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.TakePicture()
    ) { success ->
      val captured = pendingCameraUri
      if (success && captured != null) {
        viewModel.loadPhotoFromUri(captured)
      }
    }

  LaunchedEffect(state.snackbarMessage) {
    val msg = state.snackbarMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
      viewModel.clearSnackbar()
    }
  }

  // Handle system back press when viewing Print Sheet tab
  BackHandler(enabled = state.activeWorkspace == StudioWorkspaceTab.PRINT_SHEET) {
    viewModel.setWorkspaceTab(StudioWorkspaceTab.EDITOR)
  }

  Scaffold(
    containerColor = StudioBg,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = StudioSurfaceElevated,
          contentColor = StudioTextPrimary,
          actionColor = StudioCyanBright,
        )
      }
    },
    topBar = {
      StudioTopBar(
        state = state,
        onImportGallery = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onTakePhoto = {
          val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
          val file = File(cameraDir, "studio_capture_${System.currentTimeMillis()}.jpg")
          val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
          pendingCameraUri = uri
          cameraLauncher.launch(uri)
        },
        onLoadSample = { viewModel.loadSamplePortrait() },
        onSelectWorkspace = { viewModel.setWorkspaceTab(it) },
      )
    },
    bottomBar = {
      StudioBottomActionBar(
        state = state,
        onAddToQueue = { viewModel.addCurrentPhotoToQueue(navigateToSheet = false) },
        onOpenPrintSheet = {
          if (state.printQueue.isEmpty()) {
            viewModel.addCurrentPhotoToQueue(navigateToSheet = true)
          } else {
            viewModel.setWorkspaceTab(StudioWorkspaceTab.PRINT_SHEET)
          }
        },
        onExportPdf = { viewModel.exportPdf() },
        onExportJpg = { viewModel.exportJpg() },
        onPrint = {
          if (state.sheetPages.isNotEmpty()) {
            ExportAndPrintManager.launchSystemPrint(context, state.sheetPages)
          }
        },
        onShareLastExport = {
          state.lastExportResult?.let { ExportAndPrintManager.launchShareIntent(context, it) }
        },
      )
    },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isWideLayout = maxWidth >= 760.dp

      when (state.activeWorkspace) {
        StudioWorkspaceTab.EDITOR ->
          EditorScreen(
            state = state,
            viewModel = viewModel,
            isWideLayout = isWideLayout,
          )
        StudioWorkspaceTab.PRINT_SHEET ->
          PrintSheetScreen(
            state = state,
            viewModel = viewModel,
            isWideLayout = isWideLayout,
          )
      }
    }
  }
}

@Composable
private fun StudioTopBar(
  state: StudioUiState,
  onImportGallery: () -> Unit,
  onTakePhoto: () -> Unit,
  onLoadSample: () -> Unit,
  onSelectWorkspace: (StudioWorkspaceTab) -> Unit,
) {
  Surface(color = StudioSurface, tonalElevation = 2.dp) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "EasyStudio Pro",
              style = MaterialTheme.typography.titleLarge,
              color = StudioTextPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = StudioCyan.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.5f)),
            ) {
              Text(
                text = "300 DPI",
                style = MonoTextStyle,
                color = StudioCyanBright,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }

        // Quick photo source buttons
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = onImportGallery,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, StudioCyan),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
              horizontal = 10.dp,
              vertical = 6.dp,
            ),
            modifier = Modifier.height(40.dp).testTag("import_gallery_button"),
          ) {
            Icon(
              imageVector = Icons.Default.PhotoLibrary,
              contentDescription = "Import from gallery",
              tint = StudioCyanBright,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "Photo",
              style = MaterialTheme.typography.labelLarge,
              color = StudioTextPrimary,
            )
          }

          IconButton(
            onClick = onTakePhoto,
            modifier =
              Modifier.size(40.dp)
                .background(StudioSurfaceElevated, RoundedCornerShape(10.dp))
                .testTag("camera_capture_button"),
          ) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = "Take studio photo with camera",
              tint = StudioCyanBright,
              modifier = Modifier.size(18.dp),
            )
          }

          IconButton(
            onClick = onLoadSample,
            modifier =
              Modifier.size(40.dp)
                .background(StudioSurfaceElevated, RoundedCornerShape(10.dp))
                .testTag("sample_portrait_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = "Load sample studio portrait",
              tint = StudioTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Workspace Tab Row
      TabRow(
        selectedTabIndex = state.activeWorkspace.ordinal,
        containerColor = StudioSurface,
        contentColor = StudioCyanBright,
        indicator = { tabPositions ->
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[state.activeWorkspace.ordinal]),
            height = 2.5.dp,
            color = StudioCyan,
          )
        },
        divider = { Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(StudioBorder)) },
      ) {
        StudioWorkspaceTab.entries.forEach { tab ->
          val selected = state.activeWorkspace == tab
          Tab(
            selected = selected,
            onClick = { onSelectWorkspace(tab) },
            text = {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector =
                    if (tab == StudioWorkspaceTab.EDITOR) Icons.Default.Tune
                    else Icons.Default.GridOn,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = tab.label,
                  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                if (tab == StudioWorkspaceTab.PRINT_SHEET && state.printQueue.isNotEmpty()) {
                  Badge(
                    containerColor = StudioCyan,
                    contentColor = Color.White,
                  ) {
                    Text("${state.totalQueuedCopies}")
                  }
                }
              }
            },
            selectedContentColor = StudioCyanBright,
            unselectedContentColor = StudioTextSecondary,
            modifier = Modifier.testTag("workspace_tab_${tab.name.lowercase()}"),
          )
        }
      }
    }
  }
}

@Composable
private fun StudioBottomActionBar(
  state: StudioUiState,
  onAddToQueue: () -> Unit,
  onOpenPrintSheet: () -> Unit,
  onExportPdf: () -> Unit,
  onExportJpg: () -> Unit,
  onPrint: () -> Unit,
  onShareLastExport: () -> Unit,
) {
  Surface(
    color = StudioSurface,
    tonalElevation = 4.dp,
    border = BorderStroke(1.dp, StudioBorder),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (state.activeWorkspace == StudioWorkspaceTab.EDITOR) {
        OutlinedButton(
          onClick = onAddToQueue,
          enabled = state.sourceBitmap != null,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, StudioCyan),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyanBright),
          modifier = Modifier.weight(1f).height(48.dp).testTag("bottom_add_queue_button"),
        ) {
          Icon(
            imageVector = Icons.Default.AddPhotoAlternate,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add to Queue", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Button(
          onClick = onOpenPrintSheet,
          enabled = state.sourceBitmap != null,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
          modifier = Modifier.weight(1f).height(48.dp).testTag("bottom_print_sheet_button"),
        ) {
          Text("Print Sheet", maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
      } else {
        val hasPages = state.sheetPages.isNotEmpty() && !state.isExporting

        Button(
          onClick = onExportPdf,
          enabled = hasPages,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_pdf_button"),
        ) {
          if (state.isExporting) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Icon(
              imageVector = Icons.Default.PictureAsPdf,
              contentDescription = null,
              modifier = Modifier.size(17.dp),
            )
          }
          Spacer(modifier = Modifier.width(5.dp))
          Text("PDF", maxLines = 1)
        }

        OutlinedButton(
          onClick = onExportJpg,
          enabled = hasPages,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, StudioCyan),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioCyanBright),
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_jpg_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text("JPG", maxLines = 1)
        }

        OutlinedButton(
          onClick = onPrint,
          enabled = hasPages,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, StudioBorder),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioTextPrimary),
          modifier = Modifier.weight(1f).height(48.dp).testTag("system_print_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Print,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text("Print", maxLines = 1)
        }

        if (state.lastExportResult != null) {
          IconButton(
            onClick = onShareLastExport,
            modifier =
              Modifier.size(48.dp)
                .background(StudioSurfaceElevated, RoundedCornerShape(12.dp))
                .testTag("share_export_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Share,
              contentDescription = "Share last exported file",
              tint = StudioCyanBright,
            )
          }
        }
      }
    }
  }
}
