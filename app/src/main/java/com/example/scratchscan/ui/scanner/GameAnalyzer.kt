package com.example.scratchscan.ui.scanner

import android.graphics.Point
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class GameAnalyzer(
    private val onObjectDetected: (Rect, List<Point>?) -> Unit,
    private val onGameIdentified: (String?, Rect, List<Point>?) -> Unit,
    private val onDiagnosticUpdate: (String) -> Unit,
    private val onDimensionsUpdate: (Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var isProcessing = false
    private var lastProcessingTime = 0L
    private val frameThrottleMs = 250L 

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (isProcessing || ((currentTime - lastProcessingTime) < frameThrottleMs)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        isProcessing = true
        lastProcessingTime = currentTime
        
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = if ((rotation == 90) || (rotation == 270)) imageProxy.height else imageProxy.width
        val height = if ((rotation == 90) || (rotation == 270)) imageProxy.width else imageProxy.height
        onDimensionsUpdate(width, height)

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        textRecognizer.process(image)
            .addOnCompleteListener { textTask ->
                try {
                    val visionText = if (textTask.isSuccessful) textTask.result else null
                    val rawCameraText = visionText?.text ?: ""
                    
                    // Anchor the UI box to the Lottery logo
                    val brandBlock = visionText?.textBlocks?.find { 
                        it.text.contains("Maryland", ignoreCase = true) || it.text.contains("Lottery", ignoreCase = true) 
                    }
                    val box = brandBlock?.boundingBox ?: Rect()
                    val corners = brandBlock?.cornerPoints?.toList()

                    if (brandBlock != null) {
                        onObjectDetected(box, corners)
                    }

                    if (rawCameraText.isNotBlank()) {
                        onGameIdentified(rawCameraText, box, corners)
                    }

                } catch (e: Exception) {
                    onDiagnosticUpdate("Analysis Error: ${e.localizedMessage}")
                } finally {
                    isProcessing = false
                    imageProxy.close()
                }
            }
    }
}
