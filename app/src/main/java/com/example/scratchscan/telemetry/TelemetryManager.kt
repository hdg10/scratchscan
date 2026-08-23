package com.example.scratchscan.telemetry

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ScanTelemetry(
    val eventType: String,
    val gameNumber: Int? = null,
    val scanDurationMs: Long? = null,
    val barcodeType: Int? = null,
    val batteryLevel: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object TelemetryManager {
    private const val TAG = "ScratchScanTelemetry"
    private val json = Json { prettyPrint = true }

    fun logScanSuccess(gameNumber: Int, duration: Long, barcodeType: Int) {
        val event = ScanTelemetry(
            eventType = "SCAN_SUCCESS",
            gameNumber = gameNumber,
            scanDurationMs = duration,
            barcodeType = barcodeType
        )
        persistEvent(event)
    }

    fun logScanFailure(reason: String) {
        val event = ScanTelemetry(
            eventType = "SCAN_FAILURE",
            gameNumber = null // Could add 'reason' field if needed
        )
        persistEvent(event)
    }

    fun logPerformance(frameTimeMs: Long) {
        // High-frequency logging - might want to aggregate this
        if (frameTimeMs > 200) {
            Log.w(TAG, "Slow frame processing: ${frameTimeMs}ms")
        }
    }

    private fun persistEvent(event: ScanTelemetry) {
        val serialized = json.encodeToString(event)
        // In a real app, this would go to Firebase Analytics or a Room table
        Log.i(TAG, serialized)
    }
}
