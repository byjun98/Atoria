package com.ssafy.culture.ui.component

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.ssafy.culture.R

@Composable
fun CultureAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    allowHardware: Boolean = true,
    placeholder: Painter? = painterResource(id = R.drawable.login_hero_scene),
    error: Painter? = placeholder,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, model, allowHardware) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .allowHardware(allowHardware)
            .apply {
                if (!allowHardware) {
                    bitmapConfig(Bitmap.Config.ARGB_8888)
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = error,
    )
}
