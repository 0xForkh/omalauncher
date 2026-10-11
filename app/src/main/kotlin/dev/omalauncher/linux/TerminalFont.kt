package dev.omalauncher.linux

import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The size foot draws text at. foot.ini includes [PATH] rather than fixing a
 * size, so every foot (opened from the launcher or from a shell) follows the
 * screen's density and Android's font size, as the launcher's own text does.
 */
class TerminalFont(
    private val proot: Proot,
) {
    /** The point size new terminals open at. */
    var size = DEFAULT_SIZE
        private set

    /** Whether the file holds [size]; a write can fail before Linux is unpacked. */
    private var written = false

    /**
     * Android's display metrics and font size; [scale] is the integer
     * wl_output scale foot multiplies point sizes by. Terminals opened from
     * now on get the new size.
     */
    fun update(
        density: Float,
        fontScale: Float,
        scale: Int,
    ) {
        val next = pointSize(density, fontScale, scale)
        if (next == size && written) return
        size = next
        written = true
        runCatching {
            val file = File(proot.home, PATH.removePrefix("~/"))
            file.parentFile?.mkdirs()
            file.writeText("[main]\nfont=$FAMILY:size=${format(size)}\n")
        }.onFailure {
            written = false
            Log.w(TAG, "can't write the terminal font", it)
        }
    }

    companion object {
        /** foot.ini's include; the launcher owns this file and rewrites it. */
        const val PATH = "~/.local/state/omalauncher/foot-font.ini"

        /** The family foot.ini's font always was; an override (`-o main.font=`) must name it too. */
        const val FAMILY = "JetBrains Mono"

        /**
         * The em size terminal text gets, in dp at Android's default font size: the
         * launcher's 12sp minimum. A phone in portrait gets ~54 columns.
         */
        private const val EM_DP = 12f

        /** foot's pixels per point at scale 1 (96 dpi, without dpi-aware). */
        private const val PX_PER_PT = 96f / 72f

        /** JetBrains Mono's advance, in ems. */
        private const val ADVANCE = 0.6f

        /** foot.ini's pad, in logical pixels on each side. */
        private const val PAD = 8

        private const val DEFAULT_SIZE = 8.0
        private const val MIN_SIZE = 4.0
        private const val MAX_SIZE = 40.0
        private const val TAG = "terminal-font"

        /**
         * The point size that draws an em of [EM_DP] dp, times [fontScale], in
         * a foot at integer [scale] on a screen of [density]: foot multiplies
         * points by the scale, which rounds the density down (2.625 -> 2).
         * In half points, as foot.ini sizes go.
         */
        fun pointSize(
            density: Float,
            fontScale: Float,
            scale: Int,
        ): Double = (halves(EM_DP * fontScale * density / (scale * PX_PER_PT))).coerceIn(MIN_SIZE, MAX_SIZE)

        /** The largest point size, at most [size], at which [columns] fit across [widthPx] at [scale]. */
        fun fitting(
            columns: Int,
            widthPx: Int,
            scale: Int,
            size: Double,
        ): Double {
            val cellPerPt = ADVANCE * PX_PER_PT * scale
            // One column spare, for foot rounding each cell to whole pixels.
            val fit = (widthPx - 2 * PAD * scale) / (cellPerPt * (columns + 1)).toDouble()
            return minOf(size, floor(fit * 2.0) / 2).coerceAtLeast(MIN_SIZE)
        }

        fun format(size: Double): String = String.format(Locale.ROOT, "%.1f", size)

        private fun halves(pt: Float) = (pt * 2).roundToInt() / 2.0
    }
}
