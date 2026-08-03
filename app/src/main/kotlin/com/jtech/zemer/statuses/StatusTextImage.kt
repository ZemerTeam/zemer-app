package com.jtech.zemer.statuses

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation

/**
 * Renders a text status to a square bitmap for saving to the gallery, reproducing how the story viewer
 * shows it (centered body over a solid background). Deliberately COLOR-AGNOSTIC: the caller passes the
 * background and text colors in (theme-derived, or the status's own `text_bg_color`), so nothing here is
 * hardcoded. Uses Android text layout, the same primitive `ComposeToImage` uses for the lyric card.
 */
object StatusTextImage {

    fun render(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        sizePx: Int = 1080,
    ): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val padding = sizePx * 0.1f
        val maxWidth = (sizePx - padding * 2).toInt()
        val paint = TextPaint().apply {
            color = textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Shrink the type until the body fits the available height (mirrors the lyric-card auto-fit).
        var textSize = sizePx * 0.075f
        var layout = buildLayout(text, paint, maxWidth, textSize)
        val availableHeight = sizePx - padding * 2
        while (layout.height > availableHeight && textSize > sizePx * 0.03f) {
            textSize -= sizePx * 0.005f
            layout = buildLayout(text, paint, maxWidth, textSize)
        }

        val top = (sizePx - layout.height) / 2f
        canvas.withTranslation(padding, top) { layout.draw(this) }
        return bitmap
    }

    private fun buildLayout(text: String, paint: TextPaint, maxWidth: Int, size: Float): StaticLayout {
        paint.textSize = size
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(size * 0.2f, 1.2f)
            .build()
    }
}
