package com.mlib.dashboard

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun setSystemWallpaperToColor(context: Context, color: Color): Boolean = withContext(Dispatchers.IO) {
	try {
		val dm = context.resources.displayMetrics
		val w = dm.widthPixels.coerceAtLeast(1)
		val h = dm.heightPixels.coerceAtLeast(1)
		val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(bmp)
		val paint = Paint().apply { this.color = color.toArgb() }
		canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
		val setLockScreenWallpaperAlso = true

		val wm = WallpaperManager.getInstance(context)
		@Suppress("DEPRECATION")

		if (setLockScreenWallpaperAlso) wm.setBitmap(bmp, null, true)
		else wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)

		bmp.recycle()
		true
	} catch (e: Throwable) {
		false
	}
}
