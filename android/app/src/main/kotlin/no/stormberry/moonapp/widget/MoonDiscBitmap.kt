package no.stormberry.moonapp.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.roundToInt

/**
 * Draws the widget's moon on a Canvas (1.0.8): the dark disc, the lit part bounded by the exact
 * terminator of [MoonDisc], and a thin rim so the outline shows at New Moon. The colours are the
 * website's phase disc (ts/app.ts drawPhaseDisc) and the app's palette.
 */
object MoonDiscBitmap {

    /** The largest bitmap, in pixels: keeps every RemoteViews payload far below the binder budget. */
    private const val MAX_PX = 288

    /** The dark side: an earthshine tone, so a new moon still reads as a disc on the dark panel. */
    private val DARK = 0xFF1E2647.toInt()

    /** Moon.BorderActive. */
    private val RIM = 0x4DC8D8F0

    private val LIT_STOPS = intArrayOf(
        0xFFF4F6FF.toInt(),
        0xFFC8D8F0.toInt(),
        0xFF7B9FD4.toInt(),
    )
    private val LIT_POSITIONS = floatArrayOf(0f, 0.55f, 1f)

    /** A [sizeDp] square bitmap of the moon with lit [fraction], lit on the right when [litOnRight]. */
    fun draw(sizeDp: Int, density: Float, fraction: Double, litOnRight: Boolean): Bitmap {
        val px = (sizeDp * density).roundToInt().coerceIn(16, MAX_PX)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = px / 2f
        val cy = px / 2f
        val rim = (1f * density).coerceAtLeast(1f)
        val r = px / 2f - 2f * density.coerceAtLeast(1f)

        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, r, dark)

        if (fraction >= MoonDisc.DARK_BELOW) {
            val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(cx - 0.2f * r, cy - 0.25f * r, r, LIT_STOPS, LIT_POSITIONS, Shader.TileMode.CLAMP)
            }
            if (fraction > MoonDisc.FULL_ABOVE) {
                canvas.drawCircle(cx, cy, r, lit)
            } else {
                canvas.save()
                if (!litOnRight) canvas.scale(-1f, 1f, cx, cy)
                canvas.drawPath(litPath(cx, cy, r, fraction), lit)
                canvas.restore()
            }
        }

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RIM
            style = Paint.Style.STROKE
            strokeWidth = rim
        }
        canvas.drawCircle(cx, cy, r, outline)
        return bmp
    }

    /**
     * The lit region, lit limb on the right: [MoonDisc.litOutline] moved to the centre, point for
     * point. Straight segments rather than arcs, so the drawn shape is exactly the one MoonDiscTest
     * checks; 96 segments per half are below a pixel apart at the largest bitmap.
     */
    internal fun litPath(cx: Float, cy: Float, r: Float, fraction: Double): Path {
        val pts = MoonDisc.litOutline(fraction, r.toDouble())
        val path = Path()
        path.moveTo(cx + pts[0].toFloat(), cy + pts[1].toFloat())
        var i = 2
        while (i < pts.size) {
            path.lineTo(cx + pts[i].toFloat(), cy + pts[i + 1].toFloat())
            i += 2
        }
        path.close()
        return path
    }
}
