package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.size.ScaleDrawable
import eu.kanade.tachiyomi.R

fun Drawable.getBitmapOrNull(): Bitmap? = when (this) {
    is BitmapDrawable -> bitmap
    is ScaleDrawable -> child.toBitmap()
    else -> runCatching { toBitmap() }.getOrNull()
}

fun Context.getAppIconBitmap(): Bitmap? {
    return ContextCompat.getDrawable(this, R.mipmap.ic_launcher)?.getBitmapOrNull()
}
