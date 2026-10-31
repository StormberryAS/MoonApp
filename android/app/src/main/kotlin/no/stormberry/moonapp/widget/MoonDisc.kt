package no.stormberry.moonapp.widget

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The geometry of the widget's moon drawing (1.0.8). Pure, so MoonDiscTest checks it on the JVM;
 * [MoonDiscBitmap] draws it.
 *
 * For an illuminated fraction k (0 new, 1 full) the terminator seen from Earth is half an
 * ellipse whose horizontal semi-axis is r * |1 - 2k|. With u measured from the disc's centre
 * toward the lit limb and y vertically, a point is lit when
 *
 *     u^2 + y^2 <= r^2   and   u >= (1 - 2k) * sqrt(r^2 - y^2)
 *
 * A crescent's terminator bulges toward the lit limb, a gibbous moon's into the dark half. The lit
 * area is then exactly k * pi * r^2, so the picture agrees with the printed percentage. (The
 * website's drawPhaseDisc scales its ellipse linearly with the phase instead; its lit area differs
 * by up to about 2 points at the octants.)
 */
object MoonDisc {

    /** Below this the disc is drawn dark: no hairline crescent at New Moon. */
    const val DARK_BELOW = 0.005

    /** Above this the disc is drawn fully lit: no hairline terminator at Full Moon. */
    const val FULL_ABOVE = 0.995

    /**
     * Whether the lit limb is on the right. Seen from the northern hemisphere a waxing moon
     * (phase below 0.5) is lit on the right; from the southern hemisphere the same moon is upside
     * down and lit on the left. Latitude 0 counts as northern. Near the tropics the crescent
     * actually lies closer to horizontal; the widget draws the north and south mirror only.
     */
    fun litOnRight(phase: Double, latDeg: Double): Boolean = (phase < 0.5) xor (latDeg < 0)

    /** The terminator's horizontal semi-axis as a share of r, signed: positive toward the lit limb. */
    fun terminatorScale(fraction: Double): Double = 1.0 - 2.0 * fraction.coerceIn(0.0, 1.0)

    /** The terminator's u at height [y] on a disc of radius [r]. */
    fun terminatorAt(fraction: Double, y: Double, r: Double): Double =
        terminatorScale(fraction) * sqrt((r * r - y * y).coerceAtLeast(0.0))

    /** Whether the point ([u] toward the lit limb, [y]) of a disc of radius [r] is lit. */
    fun isLit(fraction: Double, u: Double, y: Double, r: Double): Boolean =
        u * u + y * y <= r * r && u >= terminatorAt(fraction, y, r)

    /** Points per half outline in [litOutline]: smooth at the largest bitmap (288 px). */
    const val OUTLINE_STEPS = 96

    /**
     * The outline of the lit region that [MoonDiscBitmap] fills, as x, y pairs around a centre at
     * 0, 0 with the lit limb toward +x and y downward (screen axes). First the lit limb from the
     * top (0, -r) through (r, 0) to the bottom (0, r), then back up along the terminator,
     * x = [terminatorScale] * r * cos(t), so a crescent's terminator bulges toward the lit limb
     * and a gibbous moon's into the far half. The bitmap draws exactly these points, so
     * MoonDiscTest checks the shape on screen, not only [isLit]. 2 * ([steps] + 1) points.
     */
    fun litOutline(fraction: Double, r: Double, steps: Int = OUTLINE_STEPS): DoubleArray {
        val s = terminatorScale(fraction)
        val out = DoubleArray(4 * (steps + 1))
        var k = 0
        for (i in 0..steps) {
            val t = -PI / 2 + PI * i / steps
            out[k++] = r * cos(t)
            out[k++] = r * sin(t)
        }
        for (i in 0..steps) {
            val t = PI / 2 - PI * i / steps
            out[k++] = s * r * cos(t)
            out[k++] = r * sin(t)
        }
        return out
    }
}
