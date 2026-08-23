package com.android.car.settings.core.ui

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads vehicle artwork off the main thread and at a bounded display size.
 *
 * The resources are deliberately kept at native 16:9 resolution for popup clarity, but decoding
 * all of them synchronously through painterResource made the first vehicle frame wait on several
 * multi-megabyte WebP decodes. A fixed 2x sample is sufficient for the largest on-screen preview
 * (and still retains more than 768px), while the placeholder lets the rotary host register before
 * artwork finishes. The composable is cancellation-safe when the user changes category quickly.
 */
@Composable
fun VehicleIllustrationImage(
    @DrawableRes illustrationRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val painter by
        produceState<BitmapPainter?>(initialValue = null, key1 = illustrationRes) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val bitmap =
                            context.resources.openRawResource(illustrationRes).use { stream ->
                                BitmapFactory.decodeStream(
                                    stream,
                                    null,
                                    BitmapFactory.Options().apply {
                                        // 1536x864 -> 768x432, which is above the minimum visible
                                        // preview size and avoids a synchronous full-resolution decode.
                                        inSampleSize = 2
                                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                                    },
                                )
                            }
                        bitmap?.asImageBitmap()?.let(::BitmapPainter)
                    }.getOrNull()
                }
        }

    if (painter != null) {
        Image(
            painter = painter!!,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier =
                modifier
                    .widthIn(min = 96.dp)
                    .heightIn(min = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The semantic description stays on the eventual Image; this placeholder is purely
            // visual and must not become a duplicate accessibility node while loading.
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent,
            ) {}
        }
    }
}
