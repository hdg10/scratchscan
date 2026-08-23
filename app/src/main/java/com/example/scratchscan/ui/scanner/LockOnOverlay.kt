package com.example.scratchscan.ui.scanner

import android.graphics.Point
import android.graphics.Rect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LockOnOverlay(
    state: ScannerState,
    imageDimensions: Pair<Int, Int>?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val beamOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val viewWidth = size.width
        val viewHeight = size.height
        
        // Draw Reticle in searching state
        if (state is ScannerState.Searching) {
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.5f),
                radius = 50.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawLine(
                color = Color.Cyan.copy(alpha = 0.3f),
                start = Offset(0f, viewHeight * beamOffset),
                end = Offset(viewWidth, viewHeight * beamOffset),
                strokeWidth = 4.dp.toPx()
            )
        }

        // Draw Tracking Outline if detected or locked
        val (rawBox, rawCorners) = when (state) {
            is ScannerState.Detected -> state.boundingBox to state.corners
            is ScannerState.Locked -> state.boundingBox to state.corners
            else -> null to null
        }

        if (imageDimensions != null && imageDimensions.first > 0 && imageDimensions.second > 0) {
            val imgWidth = imageDimensions.first
            val imgHeight = imageDimensions.second
            
            val scaleX = viewWidth / imgWidth
            val scaleY = viewHeight / imgHeight
            val color = if (state is ScannerState.Locked) Color.Green else Color.Yellow
            val edgeStrokeWidth = 3.dp.toPx()
            val cornerStrokeWidth = 8.dp.toPx()
            val cornerLen = 28.dp.toPx()
            val cornerRadius = 12.dp.toPx()

            if (rawCorners != null && rawCorners.size >= 4) {
                val mappedPoints = rawCorners.map { 
                    Offset(it.x * scaleX, it.y * scaleY) 
                }
                
                val path = Path().apply {
                    moveTo(mappedPoints[0].x, mappedPoints[0].y)
                    for (i in 1 until mappedPoints.size) {
                        lineTo(mappedPoints[i].x, mappedPoints[i].y)
                    }
                    close()
                }
                
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.3f),
                    style = Stroke(
                        width = edgeStrokeWidth,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.cornerPathEffect(cornerRadius)
                    )
                )

                mappedPoints.forEachIndexed { i, point ->
                    val nextPoint = mappedPoints[(i + 1) % mappedPoints.size]
                    val prevPoint = mappedPoints[(i + mappedPoints.size - 1) % mappedPoints.size]
                    
                    val dirNext = (nextPoint - point).let { 
                        val len = Math.sqrt((it.x * it.x + it.y * it.y).toDouble()).toFloat()
                        if (len > 0) Offset(it.x / len, it.y / len) else Offset.Zero
                    }
                    val dirPrev = (prevPoint - point).let { 
                        val len = Math.sqrt((it.x * it.x + it.y * it.y).toDouble()).toFloat()
                        if (len > 0) Offset(it.x / len, it.y / len) else Offset.Zero
                    }
                    
                    drawLine(color, point, point + dirNext * cornerLen, cornerStrokeWidth, StrokeCap.Round)
                    drawLine(color, point, point + dirPrev * cornerLen, cornerStrokeWidth, StrokeCap.Round)
                }

            } else if (rawBox != null) {
                val mappedRect = Rect(
                    (rawBox.left * scaleX).toInt(),
                    (rawBox.top * scaleY).toInt(),
                    (rawBox.right * scaleX).toInt(),
                    (rawBox.bottom * scaleY).toInt()
                )

                drawRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(mappedRect.left.toFloat(), mappedRect.top.toFloat()),
                    size = androidx.compose.ui.geometry.Size(mappedRect.width().toFloat(), mappedRect.height().toFloat()),
                    style = Stroke(width = edgeStrokeWidth, join = StrokeJoin.Round)
                )

                val rect = mappedRect
                val s = cornerStrokeWidth
                drawLine(color, Offset(rect.left.toFloat(), rect.top.toFloat()), Offset(rect.left.toFloat() + cornerLen, rect.top.toFloat()), s, StrokeCap.Round)
                drawLine(color, Offset(rect.left.toFloat(), rect.top.toFloat()), Offset(rect.left.toFloat(), rect.top.toFloat() + cornerLen), s, StrokeCap.Round)
                drawLine(color, Offset(rect.right.toFloat(), rect.top.toFloat()), Offset(rect.right.toFloat() - cornerLen, rect.top.toFloat()), s, StrokeCap.Round)
                drawLine(color, Offset(rect.right.toFloat(), rect.top.toFloat()), Offset(rect.right.toFloat(), rect.top.toFloat() + cornerLen), s, StrokeCap.Round)
                drawLine(color, Offset(rect.left.toFloat(), rect.bottom.toFloat()), Offset(rect.left.toFloat() + cornerLen, rect.bottom.toFloat()), s, StrokeCap.Round)
                drawLine(color, Offset(rect.left.toFloat(), rect.bottom.toFloat()), Offset(rect.left.toFloat(), rect.bottom.toFloat() - cornerLen), s, StrokeCap.Round)
                drawLine(color, Offset(rect.right.toFloat(), rect.bottom.toFloat()), Offset(rect.right.toFloat() - cornerLen, rect.bottom.toFloat()), s, StrokeCap.Round)
                drawLine(color, Offset(rect.right.toFloat(), rect.bottom.toFloat()), Offset(rect.right.toFloat(), rect.bottom.toFloat() - cornerLen), s, StrokeCap.Round)
            }
        }
    }
}
