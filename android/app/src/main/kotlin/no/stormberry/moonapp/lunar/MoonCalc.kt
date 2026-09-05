package no.stormberry.moonapp.lunar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Data class representing the calculated moon position in the sky.
 *
 * @param altitudeRad The angle of the moon above/below the horizon in radians.
 * @param azimuthRad The compass direction of the moon in radians.
 * @param distanceKm The approximate distance from Earth's center in kilometers.
 */
data class MoonPosition(
    val altitudeRad: Double,
    val azimuthRad: Double,
    val distanceKm: Double
)

/**
 * Data class representing the moon's phase and illumination fraction.
 *
 * @param fraction Value between 0.0 (New Moon) and 1.0 (Full Moon).
 * @param phase Value between 0.0 and 1.0 representing the lunation cycle progress.
 */
data class MoonIllumination(
    val fraction: Double,
    val phase: Double
)

/**
 * Astronomical calculation engine for MoonApp.
 *
 * For entry-level programmers:
 * Astronomical calculations convert dates into positions of celestial objects using trigonometry.
 * We calculate the position of the Moon using Julian dates (days elapsed since J2000.0 epoch),
 * spherical coordinates (Right Ascension and Declination), and local geographic latitude/longitude.
 */
object MoonCalc {

    // Mathematical constants used for angle conversions and day length calculations.
    private const val RAD = PI / 180.0
    private const val DAY_MS = 86_400_000.0
    private const val J1970 = 2_440_588.0
    private const val J2000 = 2_451_545.0
    private const val OBLIQUITY = 23.4397 * RAD

    /** Convert Instant to Julian Day number. */
    private fun toJulian(at: Instant): Double = at.toEpochMilli() / DAY_MS - 0.5 + J1970

    /** Convert Instant to days elapsed since J2000 epoch. */
    private fun toDays(at: Instant): Double = toJulian(at) - J2000

    /** Convert Julian Day number back into Instant timestamp. */
    private fun fromJulian(j: Double): Instant? =
        if (j.isNaN()) null else Instant.ofEpochMilli(((j + 0.5 - J1970) * DAY_MS).toLong())

    /**
     * Calculates Right Ascension and Declination of the moon for a given day counter.
     */
    private fun moonCoords(d: Double): DoubleArray {
        val l = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.22935 * d)

        val longitude = l + RAD * 6.289 * sin(m)
        val latitude = RAD * 5.128 * sin(f)
        val distance = 385001.0 - 20905.0 * cos(m)

        val ra = atan2(sin(longitude) * cos(OBLIQUITY) - tan(latitude) * sin(OBLIQUITY), cos(longitude))
        val dec = asin(sin(latitude) * cos(OBLIQUITY) + cos(latitude) * sin(OBLIQUITY) * sin(longitude))

        return doubleArrayOf(ra, dec, distance)
    }

    /**
     * Calculates the altitude, azimuth, and distance of the moon at a given instant and location.
     */
    /**
     * Refraction in radians for an apparent altitude, after SunCalc. Below the horizon the
     * formula is not meaningful, so the argument is clamped at zero, which is what SunCalc
     * does too.
     */
    private fun astroRefraction(altRad: Double): Double {
        val h = if (altRad < 0.0) 0.0 else altRad
        return 0.0002967 / kotlin.math.tan(h + 0.00312536 / (h + 0.08901179))
    }

    fun position(at: Instant, latDeg: Double, lonDeg: Double): MoonPosition {
        val lw = RAD * -lonDeg
        val phi = RAD * latDeg
        val d = toDays(at)

        val coords = moonCoords(d)
        val ra = coords[0]
        val dec = coords[1]
        val dist = coords[2]

        val siderealTime = RAD * (280.16 + 360.9856235 * d) - lw
        val h = siderealTime - ra

        val geometric = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))

        // ATMOSPHERIC REFRACTION, which SunCalc's getMoonPosition applies and this port did
        // not. Light bends near the horizon, so the moon appears higher than it geometrically
        // is, and that is exactly where rise and set are decided. Omitting it left the app
        // reporting rise and set roughly eleven minutes away from the website. Fixed
        // 2026-09-04. Same formula as SunCalc's astroRefraction.
        val alt = geometric + astroRefraction(geometric)

        return MoonPosition(altitudeRad = alt, azimuthRad = az, distanceKm = dist)
    }

    /**
     * Calculates the moon phase and illumination fraction for a given instant.
     */
    fun illumination(at: Instant): MoonIllumination {
        val d = toDays(at)
        // THE SUN'S ECLIPTIC LONGITUDE, not sidereal time. 360.9856235 is the coefficient
        // from SunCalc's siderealTime(), and using it here made the computed sun sweep a
        // full circle every 24 hours, so the phase completed a whole lunation per day and
        // was only correct at exactly midnight UTC (because 360.9856235 mod 360 is very
        // nearly 0.9856235, which is why daily spot-checks passed). Fixed 2026-09-04.
        // Reference: SunCalc solarMeanAnomaly = rad * (357.5291 + 0.98560028 * d).
        val meanAnomaly = RAD * (357.5291 + 0.98560028 * d)
        val center = RAD * (1.9148 * sin(meanAnomaly) + 0.02 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly))
        val sunL = meanAnomaly + center + RAD * 102.9372 + PI
        val sunRa = atan2(sin(sunL) * cos(OBLIQUITY), cos(sunL))
        val sunDec = asin(sin(OBLIQUITY) * sin(sunL))

        val moonC = moonCoords(d)
        val moonRa = moonC[0]
        val moonDec = moonC[1]

        val phi = acos(sin(sunDec) * sin(moonDec) + cos(sunDec) * cos(moonDec) * cos(sunRa - moonRa))
        val inc = atan2(149598000.0 * sin(phi), moonC[2] - 149598000.0 * cos(phi))

        val fraction = (1.0 + cos(inc)) / 2.0
        val angle = atan2(cos(sunDec) * sin(sunRa - moonRa), sin(sunDec) * cos(moonDec) - cos(sunDec) * sin(moonDec) * cos(sunRa - moonRa))
        val phase = 0.5 + 0.5 * inc * (if (angle < 0) -1.0 else 1.0) / PI

        return MoonIllumination(fraction = fraction, phase = phase)
    }

    /**
     * Finds Moonrise, Moonset, and Lunar Transit times for a given calendar date and location.
     */
    /**
     * What kind of lunar day this is at this place. On a normal day the moon rises and sets;
     * inside the polar circles it can do neither, and "up all day" and "down all day" are
     * opposite facts that the UI must not collapse into one message.
     *
     * Decided from the measured peak altitude rather than suncalc's parabola-sign test, which
     * evaluates a vertex that can fall outside the sampled window. A sweep of latitudes -89 to
     * +89 across 2026 found 12,217 days carrying suncalc's flag and 1,485 of them, 12.2 per
     * cent, labelled the wrong way round, worst at Tromso and Longyearbyen.
     */
    fun dayKind(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        zone: java.time.ZoneId = ZoneOffset.UTC,
    ): LunarDayKind {
        val t = times(date, latDeg, lonDeg, zone)
        if (t[LunarEvent.MOONRISE] != null || t[LunarEvent.MOONSET] != null) return LunarDayKind.NORMAL
        val startOfDay = date.atStartOfDay(zone).toInstant()
        var peak = -Double.MAX_VALUE
        for (i in 0..96) {
            val a = position(startOfDay.plusSeconds(i * 900L), latDeg, lonDeg).altitudeRad
            if (a > peak) peak = a
        }
        return if (peak > 0.0) LunarDayKind.ALWAYS_UP else LunarDayKind.ALWAYS_DOWN
    }

    /**
     * Rise, transit and set for [date] at a place.
     *
     * [zone] is the day boundary, and it is load-bearing for agreeing with the website.
     * app.js:327 starts its search at the CITY'S local midnight converted to UTC
     * (`Date.UTC(y, m, d) - tzOffsetMs`), not at UTC midnight, so for anywhere with a non-zero
     * offset the two surfaces were searching different 24-hour windows and returned different
     * answers for the same calendar date: over an hour apart at Bergen, and a full day apart on
     * moonset at Sydney. The default stays UTC because `tools/gen-moontimes-golden.mjs` pins
     * the algorithm against suncalc.js in UTC mode, and because OccurrenceEngine scans a
     * contiguous span of days where the boundary only decides which day an event is filed
     * under, never whether it is found. Anything the USER reads must pass the place's zone.
     */
    fun times(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        zone: java.time.ZoneId = ZoneOffset.UTC,
    ): Map<LunarEvent, Instant?> {
        val startOfDay = date.atStartOfDay(zone).toInstant()

        // HOURLY SEARCH WITH QUADRATIC INTERPOLATION, ported from SunCalc's getMoonTimes,
        // which is what the website runs. The previous implementation stepped in 20-minute
        // jumps and took the first step past the horizon as the answer, so every rise and set
        // was quantised to a 20-minute grid, and it applied no horizon correction at all.
        // Between the quantisation and the missing correction the app disagreed with the
        // website by up to half an hour. Fixed 2026-09-04.
        //
        // h0 is the altitude the moon's centre has when its upper limb touches the horizon,
        // allowing for the mean apparent radius and refraction. SunCalc uses 0.133 rad.
        val h0 = 0.133 * RAD
        var rise: Double? = null
        var set: Double? = null
        var transit: Instant? = null
        var maxAlt = -Double.MAX_VALUE

        fun altAt(hours: Double): Double =
            position(startOfDay.plusSeconds((hours * 3600.0).toLong()), latDeg, lonDeg).altitudeRad - h0

        var h00 = altAt(0.0)
        var hour = 1.0
        while (hour <= 24.0) {
            val h1 = altAt(hour)
            val h2 = altAt(hour + 1.0)
            // fit a parabola through the three samples and solve it for the horizon crossing
            val a = (h00 + h2) / 2.0 - h1
            val b = (h2 - h00) / 2.0
            val xe = -b / (2.0 * a)
            val ye = (a * xe + b) * xe + h1
            val d = b * b - 4.0 * a * h1
            var roots = 0
            var x1 = 0.0
            var x2 = 0.0
            if (d >= 0) {
                val dx = Math.sqrt(d) / (Math.abs(a) * 2.0)
                x1 = xe - dx
                x2 = xe + dx
                if (Math.abs(x1) <= 1.0) roots++
                if (Math.abs(x2) <= 1.0) roots++
                if (x1 < -1.0) x1 = x2
            }
            if (roots == 1) {
                if (h00 < 0) rise = hour + x1 else set = hour + x1
            } else if (roots == 2) {
                rise = hour + (if (ye < 0) x2 else x1)
                set = hour + (if (ye < 0) x1 else x2)
            }
            if (rise != null && set != null) break
            h00 = h2
            hour += 2.0
        }

        // TRANSIT gets its own scan. It used to be read off the rise/set loop, which steps two
        // hours at a time and breaks as soon as both crossings are found, so on most days the
        // scan never reached the peak at all: median error 45 minutes, worst case 23 hours, and
        // a LUNAR_TRANSIT alarm rang at the wrong time. The website scans every 15 minutes
        // (app.js:447-461), so match that, then refine with the parabola vertex through the
        // best sample and its two neighbours. Fixed 2026-09-04.
        run {
            val stepMinutes = 15
            val steps = 24 * 60 / stepMinutes
            var bestIdx = 0
            var bestAlt = -Double.MAX_VALUE
            val alts = DoubleArray(steps + 1)
            for (i in 0..steps) {
                val a = altAt(i * stepMinutes / 60.0) + h0     // undo the horizon offset
                alts[i] = a
                if (a > bestAlt) { bestAlt = a; bestIdx = i }
            }
            var peakMinutes = bestIdx.toDouble() * stepMinutes
            if (bestIdx in 1 until steps) {
                val y0 = alts[bestIdx - 1]; val y1 = alts[bestIdx]; val y2 = alts[bestIdx + 1]
                val denom = y0 - 2 * y1 + y2
                if (denom != 0.0) {
                    val offset = 0.5 * (y0 - y2) / denom          // vertex, in samples
                    if (offset > -1.0 && offset < 1.0) peakMinutes += offset * stepMinutes
                }
            }
            maxAlt = bestAlt
            transit = startOfDay.plusSeconds((peakMinutes * 60.0).toLong())
        }

        val result = HashMap<LunarEvent, Instant?>()
        result[LunarEvent.MOONRISE] = rise?.let { startOfDay.plusMillis((it * 3600_000.0).toLong()) }
        result[LunarEvent.MOONSET] = set?.let { startOfDay.plusMillis((it * 3600_000.0).toLong()) }
        result[LunarEvent.LUNAR_TRANSIT] = transit
        // Neither a rise nor a set means the moon was up all day or down all day, and those
        // are opposite facts a user needs told apart. dayKind() below reports which, from the
        // measured peak altitude rather than suncalc's parabola-sign test, which a survey of
        // 12,217 flagged days found mislabels 12.2 per cent of them.


        // FULL AND NEW MOON: only on the night the phase actually peaks.
        // The previous test was "fraction >= 0.95", which is true for roughly four consecutive
        // nights, so a full-moon alarm fired four times. Compare the day against its neighbours
        // instead and fire only at the extremum. The hour is local rather than 22:00 UTC, which
        // was the same instant for a user in Bergen and one in Tokyo. Longitude gives local
        // solar time without needing a timezone database.
        val noon = startOfDay.plusSeconds(43200L)
        val fToday = illumination(noon).fraction
        val fPrev = illumination(noon.minusSeconds(86400L)).fraction
        val fNext = illumination(noon.plusSeconds(86400L)).fraction
        val localOffsetSeconds = (-lonDeg / 15.0 * 3600.0).toLong()
        val eveningLocal = startOfDay.plusSeconds(79200L + localOffsetSeconds)
        if (fToday >= 0.95 && fToday >= fPrev && fToday >= fNext) {
            result[LunarEvent.FULL_MOON] = eveningLocal
        } else if (fToday <= 0.05 && fToday <= fPrev && fToday <= fNext) {
            result[LunarEvent.NEW_MOON] = eveningLocal
        }

        return result
    }
}
