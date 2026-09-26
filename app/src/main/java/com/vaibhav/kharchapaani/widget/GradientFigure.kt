package com.vaibhav.kharchapaani.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import com.vaibhav.kharchapaani.R
import kotlin.math.ceil

/**
 * The month's figure as Home draws it: Space Grotesk, tabular, in the brand gradient (amber, gold,
 * lime; ui/theme/Glass.kt BrandGradient). A widget's TextView can't paint gradient text, so it's
 * rendered to a bitmap here, in the app's own process where the bundled font is available.
 */
internal object GradientFigure {

    private val BRAND = intArrayOf(0xFFFF7A3D.toInt(), 0xFFFFB547.toInt(), 0xFFC6F24C.toInt())

    fun render(context: Context, text: String, sizeSp: Float): Bitmap {
        val metrics = context.resources.displayMetrics
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
            typeface = numericTypeface(context)
            fontFeatureSettings = "tnum"
            letterSpacing = -0.03f
            // A method, not a property: the setter returns whether the axis applied.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setFontVariationSettings("'wght' 500")
        }
        val fontMetrics = paint.fontMetrics
        // A couple of pixels either side: measureText leaves out glyph overhang.
        val inset = 2f * metrics.density
        val width = (ceil(paint.measureText(text) + 2 * inset)).toInt().coerceAtLeast(1)
        val height = ceil(fontMetrics.descent - fontMetrics.ascent).toInt().coerceAtLeast(1)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, BRAND, null, Shader.TileMode.CLAMP)

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(text, inset, -fontMetrics.ascent, paint)
        }
    }

    private fun numericTypeface(context: Context): Typeface =
        try {
            ResourcesCompat.getFont(context, R.font.space_grotesk_variable) ?: Typeface.DEFAULT_BOLD
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }
}
