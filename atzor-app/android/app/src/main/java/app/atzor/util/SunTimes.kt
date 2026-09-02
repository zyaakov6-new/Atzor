package app.atzor.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline sunset arithmetic (classic NOAA/Ed-Williams algorithm) fixed to
 * Jerusalem, so Shabbat times need no location permission and no network.
 * Accuracy is within a couple of minutes, well inside the halachic buffers.
 */
object SunTimes {

    private const val LAT = 31.778
    private const val LON = 35.235
    private const val ZENITH = 90.833

    /** Candle lighting: 18 minutes before Friday sunset (Jerusalem custom is 40; 18 is the common default). */
    private const val CANDLE_LIGHTING_MS = 18L * 60_000L

    /** Havdalah: 40 minutes after Saturday sunset (a common stringency-neutral default). */
    private const val HAVDALAH_MS = 40L * 60_000L

    private fun sinDeg(d: Double) = sin(Math.toRadians(d))
    private fun cosDeg(d: Double) = cos(Math.toRadians(d))
    private fun tanDeg(d: Double) = tan(Math.toRadians(d))
    private fun asinDeg(x: Double) = Math.toDegrees(asin(x))
    private fun acosDeg(x: Double) = Math.toDegrees(acos(x))
    private fun atanDeg(x: Double) = Math.toDegrees(atan(x))
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0

    /** Sunset for the given local date, as epoch millis; null only in polar edge cases. */
    fun sunsetMillis(date: LocalDate): Long? {
        val n = date.dayOfYear
        val lngHour = LON / 15.0
        val t = n + ((18.0 - lngHour) / 24.0)

        val m = (0.9856 * t) - 3.289
        var l = norm360(m + (1.916 * sinDeg(m)) + (0.020 * sinDeg(2 * m)) + 282.634)

        var ra = norm360(atanDeg(0.91764 * tanDeg(l)))
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuadrant - raQuadrant)) / 15.0

        val sinDec = 0.39782 * sinDeg(l)
        val cosDec = cosDeg(asinDeg(sinDec))

        val cosH = (cosDeg(ZENITH) - (sinDec * sinDeg(LAT))) / (cosDec * cosDeg(LAT))
        if (cosH < -1.0 || cosH > 1.0) return null

        val h = acosDeg(cosH) / 15.0
        val tSet = h + ra - (0.06571 * t) - 6.622
        val ut = (((tSet - lngHour) % 24.0) + 24.0) % 24.0

        return date.atStartOfDay(ZoneOffset.UTC)
            .plusSeconds((ut * 3600.0).toLong())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * The Shabbat window relevant to `now`: this week's if it is Friday/Saturday,
     * otherwise the upcoming one (useful for display). Pair(candleLighting, havdalah).
     */
    fun shabbatWindow(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val friday = when (date.dayOfWeek) {
            DayOfWeek.FRIDAY -> date
            DayOfWeek.SATURDAY -> date.minusDays(1)
            else -> date.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        }
        val fridaySunset = sunsetMillis(friday) ?: return null
        val saturdaySunset = sunsetMillis(friday.plusDays(1)) ?: return null
        return (fridaySunset - CANDLE_LIGHTING_MS) to (saturdaySunset + HAVDALAH_MS)
    }

    /** End of the active Shabbat window, or null when not inside one. */
    fun activeShabbatEnd(now: Long): Long? {
        val (start, end) = shabbatWindow(now) ?: return null
        return if (now in start until end) end else null
    }
}
