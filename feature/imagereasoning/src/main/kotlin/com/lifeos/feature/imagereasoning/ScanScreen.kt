package com.lifeos.feature.imagereasoning

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeos.core.designsystem.component.EmptyState
import com.lifeos.core.designsystem.component.SectionHeader
import com.lifeos.feature.imagereasoning.data.BoardParser
import com.lifeos.feature.imagereasoning.data.ScanKind
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanRoute(viewModel: ScanViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScanScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanScreen(uiState: ScanUiState, onEvent: (ScanUiEvent) -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            onEvent(ScanUiEvent.DismissError)
        }
    }
    LaunchedEffect(uiState.done) {
        if (uiState.done) {
            snackbarHostState.showSnackbar("Saved")
            onEvent(ScanUiEvent.BackToCapture)
        }
    }

    when (uiState.mode) {
        ScanScreenMode.CAPTURE -> CaptureScreen(uiState, onEvent, snackbarHostState)
        ScanScreenMode.REVIEW -> ReviewScreen(uiState, onEvent, snackbarHostState)
        ScanScreenMode.HISTORY -> HistoryScreen(uiState, onEvent, snackbarHostState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureScreen(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan") },
                actions = {
                    IconButton(onClick = { onEvent(ScanUiEvent.ShowHistory) }) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasPermission && !uiState.analyzing) {
                FloatingActionButton(onClick = {
                    val file = File.createTempFile("scan-", ".jpg", context.cacheDir)
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onEvent(ScanUiEvent.PhotoTaken(file.toUri(), hint = null))
                            }

                            override fun onError(exception: ImageCaptureException) {
                                // surfaced via the repository error path on next action
                            }
                        },
                    )
                }) {
                    Icon(Icons.Filled.Camera, contentDescription = "Capture")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Camera access is needed to scan receipts and boards")
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Grant camera access")
                        }
                    }
                }
                else -> {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).also { previewView ->
                                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture,
                                    )
                                }, ContextCompat.getMainExecutor(viewContext))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (uiState.analyzing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val analysis = uiState.analysis ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review scan") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ScanUiEvent.BackToCapture) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (analysis.kind) {
                ScanKind.RECEIPT -> {
                    SectionHeader(title = "Receipt")
                    OutlinedTextField(
                        value = uiState.merchant,
                        onValueChange = { onEvent(ScanUiEvent.MerchantChanged(it)) },
                        label = { Text("Merchant") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.total,
                            onValueChange = { onEvent(ScanUiEvent.TotalChanged(it)) },
                            label = { Text("Total (€)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.warrantyMonths,
                            onValueChange = { onEvent(ScanUiEvent.WarrantyChanged(it)) },
                            label = { Text("Warranty (months)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = { onEvent(ScanUiEvent.ConfirmReceipt) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save receipt")
                    }
                }
                ScanKind.WHITEBOARD -> {
                    SectionHeader(title = "Board")
                    OutlinedTextField(
                        value = uiState.boardTitle,
                        onValueChange = { onEvent(ScanUiEvent.BoardTitleChanged(it)) },
                        label = { Text("Board name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    analysis.board?.columns?.forEach { column ->
                        Card {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(column.title, style = MaterialTheme.typography.titleSmall)
                                column.cards.forEach { card ->
                                    Text("• $card", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Button(
                        onClick = { onEvent(ScanUiEvent.ConfirmBoard) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Save board to Notes")
                    }
                }
                ScanKind.OTHER -> {
                    SectionHeader(title = "Recognized text")
                    if (analysis.barcodes.isNotEmpty()) {
                        Text("Barcodes: ${analysis.barcodes.joinToString()}")
                    }
                    Card {
                        Text(
                            analysis.ocrText.ifBlank { "No text recognized" },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { onEvent(ScanUiEvent.BackToCapture) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Discard")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    uiState: ScanUiState,
    onEvent: (ScanUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan history") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ScanUiEvent.BackToCapture) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.history.isEmpty()) {
            Column(modifier = Modifier.padding(innerPadding)) {
                EmptyState(title = "No scans yet", description = "Receipts, whiteboards, barcodes.")
            }
        } else {
            LazyColumn(contentPadding = innerPadding) {
                items(uiState.history, key = { it.id }) { doc ->
                    ListItem(
                        overlineContent = {
                            Text(
                                doc.kind + " · " + DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT,
                                    DateFormat.SHORT,
                                ).format(Date(doc.createdAt)),
                            )
                        },
                        headlineContent = {
                            Text(
                                doc.ocrText.lineSequence().firstOrNull().orEmpty().ifBlank { "(no text)" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onEvent(ScanUiEvent.DeleteDoc(doc.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                }
            }
        }
    }
}
