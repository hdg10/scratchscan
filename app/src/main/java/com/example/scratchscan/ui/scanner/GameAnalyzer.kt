package com.example.scratchscan.ui.scanner

import android.graphics.Point
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class GameAnalyzer(
    private val onObjectDetected: (Rect, List<Point>?) -> Unit,
    private val onGameIdentified: (Int?, String?, Rect, List<Point>?) -> Unit,
    private val onDiagnosticUpdate: (String) -> Unit,
    private val onDimensionsUpdate: (Int, Int) -> Unit,
) : ImageAnalysis.Analyzer {

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .build()
    )

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().build()
    )

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
        val width = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val height = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        onDimensionsUpdate(width, height)

        val image = InputImage.fromMediaImage(mediaImage, rotation)

        val objectTask = objectDetector.process(image)
        val barcodeTask = barcodeScanner.process(image)
        val textTask = textRecognizer.process(image)

        com.google.android.gms.tasks.Tasks.whenAllComplete(objectTask, barcodeTask, textTask)
            .addOnCompleteListener {
                try {
                    val visionText = if (textTask.isSuccessful) textTask.result as? Text else null
                    val brandBlock = visionText?.textBlocks?.find { 
                        it.text.contains("Maryland", ignoreCase = true) || 
                        it.text.contains("Lottery", ignoreCase = true) 
                    }

                    if (brandBlock != null) {
                        onObjectDetected(brandBlock.boundingBox ?: Rect(), brandBlock.cornerPoints?.toList())
                    } else {
                        val objects = if (objectTask.isSuccessful) objectTask.result else null
                        objects?.firstOrNull()?.let { 
                            onObjectDetected(it.boundingBox, null)
                        }
                    }

                    val barcodes = if (barcodeTask.isSuccessful) barcodeTask.result else null
                    val barcode = barcodes?.firstOrNull()
                    
                    var gameNumber: Int? = null
                    var gameName: String? = null
                    var box = Rect()
                    var corners: List<Point>? = null

                    if (barcode != null) {
                        gameNumber = extractGameNumberFromBarcode(barcode.rawValue)
                        box = barcode.boundingBox ?: Rect()
                        corners = barcode.cornerPoints?.toList()
                    } else if (visionText != null) {
                        gameName = extractPotentialGameName(visionText)
                        gameNumber = extractGameNumberFromText(visionText.text)
                        
                        box = brandBlock?.boundingBox ?: Rect()
                        corners = brandBlock?.cornerPoints?.toList()
                    }

                    if (gameNumber != null || gameName != null) {
                        onGameIdentified(gameNumber, gameName, box, corners)
                    }

                } catch (e: Exception) {
                    onDiagnosticUpdate("Analysis Error: ${e.localizedMessage}")
                } finally {
                    isProcessing = false
                    imageProxy.close()
                }
            }
    }

    private fun extractGameNumberFromBarcode(raw: String?): Int? {
        if (raw == null) return null
        val clean = raw.filter { it.isDigit() }
        return if (clean.length >= 3) clean.take(3).toIntOrNull() else null
    }

    private fun extractGameNumberFromText(text: String): Int? {
        val regex = Regex("(?i)(?:GAME|GME|#)\\s*(\\d{3})")
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractPotentialGameName(visionText: Text): String {
        return visionText.text
    }
}
