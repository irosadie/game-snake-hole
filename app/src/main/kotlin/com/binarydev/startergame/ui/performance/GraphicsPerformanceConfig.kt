package com.binarydev.startergame.ui.performance

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Hardware RAM/CPU snapshot detected safely via Android [ActivityManager].
 * Used to automatically enable lightweight graphics on low-RAM ("hape kentang") devices.
 */
data class DevicePerformanceInfo(
    val isLowRamDevice: Boolean = false,
    val totalRamMb: Int = 4096,
    val cpuCores: Int = 4
) {
    /** Recommends Mode Ringan for Android Go/low-RAM devices, <= 3.5 GB RAM, or <= 4 cores. */
    val recommendedLowRamMode: Boolean
        get() = isLowRamDevice || (totalRamMb in 1..3584) || cpuCores <= 4

    companion object {
        fun detect(context: Context): DevicePerformanceInfo {
            val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(4)
            return runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt()
                val lowRamFlag = am?.isLowRamDevice == true
                DevicePerformanceInfo(
                    isLowRamDevice = lowRamFlag,
                    totalRamMb = if (totalMb > 0) totalMb else 4096,
                    cpuCores = cores
                )
            }.getOrDefault(DevicePerformanceInfo(isLowRamDevice = false, totalRamMb = 4096, cpuCores = cores))
        }
    }
}

/**
 * Runtime graphics & frame-budget configuration consumed by Canvas boards and screens.
 */
data class GraphicsPerformanceConfig(
    val lowRamMode: Boolean = false,
    val enableIdleAnimations: Boolean = !lowRamMode,
    val enableShadersAndGradients: Boolean = !lowRamMode,
    val enableSpecularHighlights: Boolean = !lowRamMode,
    val maxConfettiParticles: Int = if (lowRamMode) 6 else 14,
    // Keep active movement silky at 60fps (zero-alloc batched Canvas is <2ms/frame) while idle stays 0fps in Lite mode
    val targetFrameIntervalNanos: Long = 16_666_666L,
    val idleFrameIntervalNanos: Long = 66_666_666L
) {
    companion object {
        fun forLowRamMode(lowRamMode: Boolean): GraphicsPerformanceConfig =
            GraphicsPerformanceConfig(lowRamMode = lowRamMode)
    }
}

val LocalGraphicsPerformanceConfig = compositionLocalOf {
    GraphicsPerformanceConfig(lowRamMode = false)
}

/**
 * Decodes a bitmap drawable downsampled to [maxSizeDp] using RGB_565 (half the RAM of ARGB_8888)
 * and power-of-two [BitmapFactory.Options.inSampleSize], preventing multi-megabyte full-resolution
 * allocations when displaying large launcher/banner assets inside small thumbnails.
 */
@Composable
fun rememberSampledDrawableBitmap(
    @DrawableRes resId: Int,
    maxSizeDp: Dp,
    preferRgb565: Boolean = true
): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { maxSizeDp.roundToPx() }.coerceIn(48, 512)

    return remember(resId, targetPx, preferRgb565) {
        runCatching {
            val resources = context.resources
            val boundsOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(resources, resId, boundsOpts)
            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) return@runCatching null

            var sampleSize = 1
            while ((srcW / (sampleSize * 2)) >= targetPx && (srcH / (sampleSize * 2)) >= targetPx) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inScaled = false
                inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(resources, resId, decodeOpts)?.asImageBitmap()
        }.getOrNull()
    }
}

/**
 * Drop-in RAM-safe Image composable for bitmap drawables, falling back cleanly to [painterResource]
 * if decoding returns null (e.g. in vector/Robolectric environments).
 */
@Composable
fun SampledDrawableImage(
    @DrawableRes resId: Int,
    maxSizeDp: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    preferRgb565: Boolean = true
) {
    val sampledBitmap = rememberSampledDrawableBitmap(
        resId = resId,
        maxSizeDp = maxSizeDp,
        preferRgb565 = preferRgb565
    )
    if (sampledBitmap != null) {
        Image(
            bitmap = sampledBitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}
