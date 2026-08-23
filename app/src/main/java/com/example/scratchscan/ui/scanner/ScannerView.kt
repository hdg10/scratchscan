package com.example.scratchscan.ui.scanner

import android.app.Application
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.util.concurrent.Executors

@Composable
fun ScannerView(
    onNavigateToStats: (Int) -> Unit,
    viewModel: ScannerViewModel = viewModel(
        factory = ScannerViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scannerState by viewModel.uiState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val imageDimensions by viewModel.imageDimensions.collectAsState()
    
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashEnabled by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    previewView?.let { view ->
                        val factory = view.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { _ -> }
        )

        // Camera setup in LaunchedEffect to avoid re-binding
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, GameAnalyzer(
                            onObjectDetected = { rect, corners -> viewModel.onObjectDetected(rect, corners) },
                            onGameIdentified = { num, name, rect, corners -> viewModel.onGameIdentified(num, name, rect, corners) },
                            onDiagnosticUpdate = { msg -> viewModel.updateDiagnostics(msg) },
                            onDimensionsUpdate = { w, h -> viewModel.setImageDimensions(w, h) }
                        ))
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("ScannerView", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        LockOnOverlay(
            state = scannerState,
            imageDimensions = imageDimensions
        )

        // HUD & Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Confirmation Card
            AnimatedVisibility(
                visible = scannerState is ScannerState.Confirming,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                (scannerState as? ScannerState.Confirming)?.let { state ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = state.game.artworkUrl,
                                    contentDescription = state.game.name,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color.White, MaterialTheme.shapes.small)
                                        .padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(text = "Confirm Ticket", style = MaterialTheme.typography.labelMedium)
                                    Text(text = state.game.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                                    Text(text = "Game #${state.game.gameNumber} • $${state.game.price}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.confirmGame(state.game) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Text("Confirm & View Stats", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // Diagnostics HUD
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .padding(8.dp)
            ) {
                Text(
                    text = diagnostics,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (scannerState is ScannerState.Locked) {
                val locked = scannerState as ScannerState.Locked
                Button(
                    onClick = { onNavigateToStats(locked.game.gameNumber) },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null)
                    Text("View Detailed Statistics", modifier = Modifier.padding(start = 8.dp))
                }
                
                Button(
                    onClick = { viewModel.resetScanner() },
                    modifier = Modifier.padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Scan New Ticket", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        IconButton(
            onClick = {
                isFlashEnabled = !isFlashEnabled
                camera?.cameraControl?.enableTorch(isFlashEnabled)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.extraLarge)
        ) {
            Icon(
                imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Toggle Flash",
                tint = if (isFlashEnabled) Color.Yellow else Color.White
            )
        }
    }
}
