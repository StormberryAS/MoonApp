package no.stormberry.moonapp.widget

/**
 * Which arrangement the large widget uses (1.0.8). Pure, so LargeLayoutTest covers it.
 *
 * - TALL stacks the moon and times over the four phase dates and needs 240 x 200 dp.
 * - WIDE puts the moon, the times and the phases side by side and needs 370 x 110 dp.
 * - COMPACT is for a widget that is neither (a 4x2 on a 1080 x 1920 phone, 292 x 184, a 4x2 on a
 *   small phone, about 340 x 170, or a widget resized down). A smaller moon beside one column of
 *   short rows: the three times, then the four phase dates. It fits from 180 x 100 dp.
 *
 * The minimums were measured with WidgetRenderTest on a 420 dpi AVD at the default font on
 * 2026-10-30, with the zone note and both polar sentences: TALL drops a time row below 200 dp high
 * and clips a label below 240 dp wide; WIDE clips its last phase row below 110 dp high and two
 * phase names below 370 dp wide. COMPACT fitted every size tried from 180 x 100 to 379 x 400 dp.
 *
 * On Android 12 and later the launcher chooses: MoonWidgets hands it one RemoteViews per entry in
 * [sizes], and it takes the entry that fits the widget and is closest to its size, again on every
 * resize and rotation. Several TALL entries make sure a tall widget is never given the WIDE or
 * COMPACT arrangement just because it fits them too. Android 7 to 11 have no such mapping, so
 * [pick] chooses from the size options the launcher reports.
 *
 * The phone's font size: the sizes above are for the default font. TALL and WIDE have fixed text
 * sizes, so a larger font needs a larger widget (at font scale 2.0 a 260 x 213 dp TALL widget lost
 * its moon and two of its times on the AVD); COMPACT shrinks its text to fit. Every threshold is
 * multiplied by [scaleFor], so with a large font a widget gets COMPACT until it is big enough.
 * After a font size change the arrangement follows at the next refresh (at most three hours, or
 * at once when MoonApp is opened), because Android tells no manifest receiver about it.
 */
object LargeLayout {

    enum class Arrangement { TALL, WIDE, COMPACT }

    /** The width in dp from which the TALL arrangement fits. */
    const val TALL_MIN_WIDTH_DP = 240

    /** The height in dp from which the TALL arrangement fits. */
    const val TALL_MIN_HEIGHT_DP = 200

    /** The width in dp from which the WIDE arrangement fits. */
    const val WIDE_MIN_WIDTH_DP = 370

    /** The height in dp from which the WIDE arrangement fits. */
    const val WIDE_MIN_HEIGHT_DP = 110

    /** Android 12 and later, at the default font: the arrangement for each ideal size in dp. */
    val SIZES: List<Triple<Float, Float, Arrangement>> = listOf(
        Triple(TALL_MIN_WIDTH_DP.toFloat(), TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(300f, TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(380f, TALL_MIN_HEIGHT_DP.toFloat(), Arrangement.TALL),
        Triple(420f, 240f, Arrangement.TALL),
        Triple(WIDE_MIN_WIDTH_DP.toFloat(), WIDE_MIN_HEIGHT_DP.toFloat(), Arrangement.WIDE),
        Triple(180f, 100f, Arrangement.COMPACT),
    )

    /**
     * How much larger the thresholds are at the phone's [fontScale]. About half of a TALL
     * widget's minimum height is text, so the thresholds grow by half the font's growth: 1.15 at
     * font scale 1.3 (TALL from 276 x 230 dp) and 1.5 at 2.0 (TALL from 360 x 300 dp). Never
     * below 1, so a small font keeps the default thresholds.
     */
    fun scaleFor(fontScale: Float): Float = maxOf(1f, 0.5f + 0.5f * fontScale)

    /** [SIZES] for the phone's [fontScale]: every entry multiplied by [scaleFor]. */
    fun sizes(fontScale: Float = 1f): List<Triple<Float, Float, Arrangement>> {
        val k = scaleFor(fontScale)
        return SIZES.map { (w, h, a) -> Triple(w * k, h * k, a) }
    }

    /**
     * Android 7 to 11: the arrangement for the widget as it shows in portrait, where the launcher
     * draws it [minWidth] wide and [maxHeight] high. TALL when it is at least
     * [TALL_MIN_WIDTH_DP] x [TALL_MIN_HEIGHT_DP]; otherwise WIDE when it is at least
     * [WIDE_MIN_WIDTH_DP] x [WIDE_MIN_HEIGHT_DP], and COMPACT when it is neither. With no size reported yet (all zero, the first update on some
     * launchers) TALL, the portrait case. In landscape on these versions the widget keeps the
     * portrait choice; it can look cramped there, but what it shows is still right. Both
     * thresholds are multiplied by [scaleFor] of the phone's [fontScale].
     */
    fun pick(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int, fontScale: Float = 1f): Arrangement {
        if (maxHeight <= 0 && minHeight <= 0) return Arrangement.TALL
        val k = scaleFor(fontScale)
        val portraitHeight = maxOf(maxHeight, minHeight)
        val portraitWidth = if (minWidth > 0) minWidth else maxWidth
        if (portraitHeight >= TALL_MIN_HEIGHT_DP * k && portraitWidth >= TALL_MIN_WIDTH_DP * k) {
            return Arrangement.TALL
        }
        return if (portraitWidth >= WIDE_MIN_WIDTH_DP * k && portraitHeight >= WIDE_MIN_HEIGHT_DP * k) {
            Arrangement.WIDE
        } else {
            Arrangement.COMPACT
        }
    }

    /**
     * What Android 12 and later choose from [sizes] for a widget [width] x [height] dp: the
     * fitting entry closest to the size, or the smallest entry when none fits. The platform's own
     * rule (RemoteViews.getRemoteViewsToApply), restated so LargeLayoutTest can show that the
     * entries above give each widget size the arrangement that fits it.
     */
    fun chosenOnApi31(width: Float, height: Float, fontScale: Float = 1f): Arrangement {
        val all = sizes(fontScale)
        val fitting = all.filter { (w, h) -> w <= width && h <= height }
        val best = fitting.minByOrNull { (w, h) -> (width - w) * (width - w) + (height - h) * (height - h) }
            ?: all.minBy { (w, h) -> w * h }
        return best.third
    }
}
