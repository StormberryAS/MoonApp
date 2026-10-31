package no.stormberry.moonapp.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The widget's moon drawing (1.0.8): the lit side for each hemisphere, a lit area equal to the
 * printed percentage, which the website's linear terminator does not give, and the outline the
 * bitmap actually fills (MoonDisc.litOutline), checked point for point against isLit.
 */
class MoonDiscTest {

    @Test
    fun `the lit side is right when waxing in the north, left when waning, mirrored in the south`() {
        assertTrue("waxing, Oslo", MoonDisc.litOnRight(0.30, 59.91))
        assertFalse("waning, Oslo", MoonDisc.litOnRight(0.70, 59.91))
        assertFalse("waxing, Sydney", MoonDisc.litOnRight(0.30, -33.87))
        assertTrue("waning, Sydney", MoonDisc.litOnRight(0.70, -33.87))
        assertTrue("latitude 0 counts as north", MoonDisc.litOnRight(0.30, 0.0))
        assertFalse("latitude 0 counts as north", MoonDisc.litOnRight(0.70, 0.0))
    }

    /** The lit share of the disc, sampled on an n x n grid. */
    private fun litShare(k: Double, n: Int = 400): Double {
        val r = 1.0
        var inside = 0
        var lit = 0
        for (i in 0 until n) for (j in 0 until n) {
            val u = -r + (i + 0.5) * 2 * r / n
            val y = -r + (j + 0.5) * 2 * r / n
            if (u * u + y * y > r * r) continue
            inside++
            if (MoonDisc.isLit(k, u, y, r)) lit++
        }
        return lit.toDouble() / inside
    }

    @Test
    fun `the drawn lit area equals the lit fraction within 0_3 points`() {
        for (k in listOf(0.0, 0.01, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, 1.0)) {
            val share = litShare(k)
            assertTrue("k = $k: drawn ${"%.4f".format(share)}", abs(share - k) <= 0.003)
        }
    }

    @Test
    fun `a crescent never lights the far half and a gibbous moon always lights the near half`() {
        val r = 1.0
        for (k in listOf(0.02, 0.2, 0.45)) {
            for (y in listOf(-0.9, -0.5, 0.0, 0.5, 0.9)) {
                for (u in listOf(-0.99, -0.5, -0.01)) {
                    assertFalse("crescent k = $k lights ($u, $y)", MoonDisc.isLit(k, u, y, r))
                }
            }
        }
        for (k in listOf(0.55, 0.8, 0.98)) {
            for (y in listOf(-0.9, -0.5, 0.0, 0.5, 0.9)) {
                for (u in listOf(0.0, 0.2, 0.4)) {
                    if (u * u + y * y > r * r) continue
                    assertTrue("gibbous k = $k leaves ($u, $y) dark", MoonDisc.isLit(k, u, y, r))
                }
            }
        }
    }

    @Test
    fun `the terminator moves continuously through the quarter`() {
        val r = 1.0
        var prev = MoonDisc.terminatorAt(0.49, 0.0, r)
        var k = 0.49
        while (k <= 0.51) {
            val now = MoonDisc.terminatorAt(k, 0.0, r)
            assertTrue("jump at k = $k", abs(now - prev) < 0.003)
            assertTrue("the terminator moves away from the lit limb as k grows", now <= prev + 1e-12)
            prev = now
            k += 0.001
        }
        assertEquals(0.0, MoonDisc.terminatorAt(0.5, 0.3, r), 1e-12)
        assertEquals("new moon: the terminator is the lit limb itself", 1.0, MoonDisc.terminatorAt(0.0, 0.0, r), 1e-12)
        assertEquals("full moon: the terminator is the far limb", -1.0, MoonDisc.terminatorAt(1.0, 0.0, r), 1e-12)
    }

    /** Shoelace area of an x, y outline. */
    private fun area(pts: DoubleArray): Double {
        var a = 0.0
        val n = pts.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += pts[2 * i] * pts[2 * j + 1] - pts[2 * j] * pts[2 * i + 1]
        }
        return abs(a) / 2
    }

    /** Even-odd ray cast: whether (x, y) is inside the outline. */
    private fun inside(pts: DoubleArray, x: Double, y: Double): Boolean {
        val n = pts.size / 2
        var c = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = pts[2 * i]
            val yi = pts[2 * i + 1]
            val xj = pts[2 * j]
            val yj = pts[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    @Test
    fun `the drawn outline encloses the lit fraction of the disc`() {
        val r = 100.0
        for (k in listOf(0.01, 0.1, 0.25, 0.4, 0.5, 0.6, 0.75, 0.9, 0.99)) {
            val share = area(MoonDisc.litOutline(k, r)) / (PI * r * r)
            assertTrue("k = $k: outline encloses ${"%.4f".format(share)}", abs(share - k) <= 0.002)
        }
    }

    @Test
    fun `the drawn outline starts at the top and runs down the lit limb on the right`() {
        val r = 100.0
        val pts = MoonDisc.litOutline(0.2, r)
        val half = MoonDisc.OUTLINE_STEPS / 2
        assertEquals("top", 0.0, pts[0], 1e-9)
        assertEquals("top", -r, pts[1], 1e-9)
        assertEquals("the lit limb is at +x", r, pts[2 * half], 1e-9)
        assertEquals(0.0, pts[2 * half + 1], 1e-9)
        // The terminator at mid-height: toward the lit limb for a crescent, away for a gibbous moon.
        val mid = 2 * (MoonDisc.OUTLINE_STEPS + 1 + half)
        assertEquals("crescent", 0.6 * r, MoonDisc.litOutline(0.2, r)[mid], 1e-9)
        assertEquals("gibbous", -0.6 * r, MoonDisc.litOutline(0.8, r)[mid], 1e-9)
    }

    @Test
    fun `every pixel the outline fills is lit and every lit pixel is filled`() {
        val r = 100.0
        for (k in listOf(0.03, 0.2, 0.45, 0.5, 0.55, 0.78, 0.97)) {
            val pts = MoonDisc.litOutline(k, r)
            var y = -r + 0.5
            while (y < r) {
                var x = -r + 0.5
                while (x < r) {
                    // Away from the edges, where a 96-step polygon and the curve may differ by
                    // less than a pixel.
                    val nearLimb = abs(hypot(x, y) - r) < 1.0
                    val nearTerminator = abs(x - MoonDisc.terminatorAt(k, y, r)) < 1.0
                    if (!nearLimb && !nearTerminator) {
                        assertEquals("k = $k at ($x, $y)", MoonDisc.isLit(k, x, y, r), inside(pts, x, y))
                    }
                    x += 2.0
                }
                y += 2.0
            }
        }
    }
}
