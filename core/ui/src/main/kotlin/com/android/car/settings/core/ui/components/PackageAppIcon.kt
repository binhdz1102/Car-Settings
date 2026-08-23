package com.android.car.settings.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PACKAGE_ICON_SIZE_PX = 48
private const val PACKAGE_ICON_CACHE_ENTRIES = 64

/**
 * Shared asynchronous application-icon renderer for settings lists.
 *
 * PackageManager calls can block while the package database is being loaded, so lookup and
 * rasterisation intentionally happen off the main thread. A removed/broken package is a normal
 * state for a system settings screen and falls back to the generic Apps glyph.
 */
@Composable
fun PackageAppIcon(
    packageName: String,
    modifier: Modifier = Modifier.size(48.dp),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf(PackageIconCache.get(packageName))
    }
    LaunchedEffect(context, packageName) {
        if (bitmap != null) return@LaunchedEffect
        val loaded =
            withContext(Dispatchers.IO) {
                loadPackageIcon(context, packageName)
            }
        if (loaded != null) {
            PackageIconCache.put(packageName, loaded)
            bitmap = loaded
        }
    }
    val resolved = bitmap
    if (resolved == null) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // App-icon artwork is frequently transparent or near-black; a surfaceVariant plate keeps
        // it visible on both dark and light row surfaces.
        Box(
            modifier =
                modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = resolved.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun loadPackageIcon(
    context: Context,
    packageName: String,
): Bitmap? =
    runCatching {
        context.packageManager
            .getApplicationIcon(packageName)
            .toBitmap(PACKAGE_ICON_SIZE_PX, PACKAGE_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    }.getOrNull()

private object PackageIconCache {
    private val cache = object : LruCache<String, Bitmap>(PACKAGE_ICON_CACHE_ENTRIES) {}

    @Synchronized
    fun get(packageName: String): Bitmap? = cache.get(packageName)

    @Synchronized
    fun put(
        packageName: String,
        bitmap: Bitmap,
    ) {
        cache.put(packageName, bitmap)
    }
}
