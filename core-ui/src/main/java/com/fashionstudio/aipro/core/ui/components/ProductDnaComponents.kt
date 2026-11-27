package com.fashionstudio.aipro.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fashionstudio.aipro.core.imaging.DnaLockStatus
import com.fashionstudio.aipro.core.ui.theme.StudioColors

/**
 * Always-visible Product DNA Lock status pill displayed in the Studio Editor and Verification screens.
 */
@Composable
fun ProductDnaLockBadge(
    status: DnaLockStatus,
    modifier: Modifier = Modifier
) {
    val (label, tint, icon) = when (status) {
        DnaLockStatus.PROTECTED -> Triple("DNA Protected", StudioColors.AccentGold, Icons.Default.Lock)
        DnaLockStatus.VERIFIED -> Triple("DNA Verified", StudioColors.SuccessGreen, Icons.Default.VerifiedUser)
        DnaLockStatus.REVIEW_REQUIRED -> Triple("Review Required", StudioColors.WarningAmber, Icons.Default.Visibility)
        DnaLockStatus.FIDELITY_FAILED -> Triple("Fidelity Failed", StudioColors.ErrorRed, Icons.Default.GppBad)
        DnaLockStatus.VIRTUAL_RECONSTRUCTION -> Triple("Virtual Reconstruction", StudioColors.InfoBlue, Icons.Default.AutoAwesome)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tint.copy(alpha = 0.16f))
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Interactive Before/After split slider with synchronized 1x–4x (100%–400%) pinch zoom & pan
 * for verifying seams, logos, buttons, and color fidelity against the immutable original.
 */
@Composable
fun BeforeAfterVerificationViewport(
    originalBitmap: Bitmap?,
    resultBitmap: Bitmap?,
    diffHeatmapBitmap: Bitmap?,
    showHeatmap: Boolean,
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                    onZoomChange(nextScale)
                    panOffset = if (nextScale > 1f) {
                        Offset(
                            (panOffset.x + pan.x).coerceIn(-600f, 600f),
                            (panOffset.y + pan.y).coerceIn(-600f, 600f)
                        )
                    } else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (zoomScale <= 1.05f && size.width > 0) {
                        change.consume()
                        splitFraction = (splitFraction + dragAmount / size.width).coerceIn(0.05f, 0.95f)
                    }
                }
            }
    ) {
        val activeRight = if (showHeatmap && diffHeatmapBitmap != null) diffHeatmapBitmap else resultBitmap

        if (originalBitmap != null && activeRight != null) {
            val origImg = remember(originalBitmap) { originalBitmap.asImageBitmap() }
            val rightImg = remember(activeRight) { activeRight.asImageBitmap() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
            ) {
                // Right side (Result or Heatmap)
                Image(
                    bitmap = rightImg,
                    contentDescription = "Generated or Heatmap Image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // Left side clipped to splitFraction (Immutable Original)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val splitX = size.width * splitFraction
                    clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                        drawImage(
                            image = origImg,
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }
                    drawLine(
                        color = StudioColors.AccentGold,
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, size.height),
                        strokeWidth = 3f
                    )
                }
            }

            // Corner labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(8.dp)) {
                    Text("ORIGINAL", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (showHeatmap) "DIFF MAP" else "STUDIO RESULT (${(zoomScale * 100).toInt()}%)",
                        color = StudioColors.AccentGold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No comparison image loaded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
