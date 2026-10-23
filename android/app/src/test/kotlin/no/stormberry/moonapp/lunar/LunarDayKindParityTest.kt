package no.stormberry.moonapp.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Whether the moon rises and sets, stays up or stays down, as the website labels it (1.0.4, F-3).
 *
 * lunar-daykind-parity.csv is written from the compiled page by tools/gen-lunar-parity.ts: every
 * 2026 day on a two-degree latitude grid whose peak lies between the horizon and the 0.133 degree
 * rise threshold (the days the fix changes) with the days either side, and five polar places
 * every seventh day and on every day their zone changes its offset, with its neighbours (F-5).
 * Until 1.0.4 both surfaces called such a day ALWAYS_UP.
 *
 * Regenerate the corpus with: node tools/gen-lunar-parity.ts
 */
class LunarDayKindParityTest {

    @Test
    fun `the app labels every day as the website does`() {
        val rows = parityRows("lunar-daykind-parity.csv")
        assertEquals("rows in lunar-daykind-parity.csv", 355, rows.size)
        val failures = rows.mapNotNull { f ->
            val got = MoonCalc.dayKind(LocalDate.parse(f[4]), f[1].toDouble(), f[2].toDouble(), ZoneId.of(f[3]))
            if (got.name == f[5]) null else "${f[0]} ${f[4]}: app $got, website ${f[5]}"
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
        // The band days themselves must be in the corpus, or the test proves nothing about F-3.
        assertTrue(rows.count { it[0].startsWith("Grid") && it[5] == "ALWAYS_DOWN" } > 0)
    }
}
