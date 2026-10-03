package eu.kanade.tachiyomi.ui.reader.viewer

/** Screen-pixel bounds of the matched text, not the whole OCR paragraph. */
data class PopupSourceRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal data class PopupPlacement(
    val x: Float, val y: Float, val width: Float, val height: Float, val after: Boolean,
)

/**
 * Adapted from Yomitan popup.js: constrained binary placement, source-rectangle
 * candidates plus their union, and rejection of candidates covering source text.
 * Reference: yomidevs/yomitan, 67db60ddc2cbd7b5172d777c117e3201d7ddff0f.
 * Copyright (C) 2023-2026 Yomitan Authors; GPL-3.0-or-later.
 * Android adaptation: finite inputs, nonnegative extents, and gap-aware overlap
 * checks. With no free area, return a zero-sized axis instead of covering text.
 */
internal object PopupPlacementPolicy {
    fun bestHorizontal(
        sources: List<PopupSourceRect>, preferredWidth: Float, preferredHeight: Float,
        viewportWidth: Float, viewportHeight: Float, padding: Float, gap: Float,
        preferBelow: Boolean,
    ): PopupPlacement = best(sources, preferredWidth, preferredHeight, viewportWidth,
        viewportHeight, padding, gap, preferBelow, false)

    fun bestVertical(
        sources: List<PopupSourceRect>, preferredWidth: Float, preferredHeight: Float,
        viewportWidth: Float, viewportHeight: Float, padding: Float, gap: Float,
        preferRight: Boolean,
    ): PopupPlacement = best(sources, preferredWidth, preferredHeight, viewportWidth,
        viewportHeight, padding, gap, preferRight, true)

    private fun best(
        sources: List<PopupSourceRect>, w: Float, h: Float, vw: Float, vh: Float,
        padding: Float, gap: Float, preferAfter: Boolean, vertical: Boolean,
    ): PopupPlacement {
        val valid = sources.filter { listOf(it.left, it.top, it.right, it.bottom).all(Float::isFinite) }
            .map { PopupSourceRect(minOf(it.left, it.right), minOf(it.top, it.bottom),
                maxOf(it.left, it.right), maxOf(it.top, it.bottom)) }
            .ifEmpty { listOf(PopupSourceRect(0f, 0f, 0f, 0f)) }
        val union = PopupSourceRect(valid.minOf { it.left }, valid.minOf { it.top },
            valid.maxOf { it.right }, valid.maxOf { it.bottom })
        val candidates = if (valid.size > 1) valid + union else valid
        var best: PopupPlacement? = null
        for (source in candidates) {
            val result = if (vertical) vertical(source, w, h, vw, vh, padding, gap, preferAfter)
                else horizontal(source, w, h, vw, vh, padding, gap, preferAfter)
            if (valid.any { overlaps(result, it, positive(gap)) }) continue
            val size = if (vertical) result.width else result.height
            val bestSize = best?.let { if (vertical) it.width else it.height } ?: -1f
            if (size > bestSize) {
                best = result
                if (size >= positive(if (vertical) w else h)) break
            }
        }
        return best ?: if (vertical) vertical(union, w, h, vw, vh, padding, gap, preferAfter)
            else horizontal(union, w, h, vw, vh, padding, gap, preferAfter)
    }

    fun horizontal(
        source: PopupSourceRect, preferredWidth: Float, preferredHeight: Float,
        viewportWidth: Float, viewportHeight: Float, padding: Float, gap: Float,
        preferBelow: Boolean,
    ): PopupPlacement {
        val (minX, maxX) = limits(viewportWidth, padding)
        val (minY, maxY) = limits(viewportHeight, padding)
        val width = positive(preferredWidth).coerceAtMost(maxX - minX)
        // Like Yomitan, align to the start of the source and constrain on the
        // orthogonal axis; never constrain through the source on the main axis.
        val x = finite(source.left, minX).coerceIn(minX, (maxX - width).coerceAtLeast(minX))
        val (y, height, after) = binary(finite(source.top, minY) - positive(gap),
            finite(source.bottom, minY) + positive(gap), preferredHeight, minY, maxY, preferBelow)
        return PopupPlacement(x, y, width, height, after)
    }

    fun vertical(
        source: PopupSourceRect, preferredWidth: Float, preferredHeight: Float,
        viewportWidth: Float, viewportHeight: Float, padding: Float, gap: Float,
        preferRight: Boolean,
    ): PopupPlacement {
        val (minX, maxX) = limits(viewportWidth, padding)
        val (minY, maxY) = limits(viewportHeight, padding)
        val height = positive(preferredHeight).coerceAtMost(maxY - minY)
        val y = finite(source.top, minY).coerceIn(minY, (maxY - height).coerceAtLeast(minY))
        val (x, width, after) = binary(finite(source.left, minX) - positive(gap),
            finite(source.right, minX) + positive(gap), preferredWidth, minX, maxX, preferRight)
        return PopupPlacement(x, y, width, height, after)
    }

    private fun binary(before: Float, after: Float, size: Float, min: Float, max: Float,
        preferAfter: Boolean): Triple<Float, Float, Boolean> {
        val requested = positive(size).coerceAtMost(max - min)
        val overflowBefore = min - (before - requested)
        val overflowAfter = after + requested - max
        val placeAfter = if (overflowBefore > 0f || overflowAfter > 0f)
            overflowAfter < overflowBefore else preferAfter
        val edge = (if (placeAfter) after else before).coerceIn(min, max)
        val available = if (placeAfter) max - edge else edge - min
        val fitted = minOf(requested, available).coerceAtLeast(0f)
        return Triple(if (placeAfter) edge else edge - fitted, fitted, placeAfter)
    }

    private fun limits(size: Float, padding: Float): Pair<Float, Float> {
        val end = positive(size)
        val inset = positive(padding).coerceAtMost(end / 2f)
        return inset to end - inset
    }

    private fun finite(value: Float, fallback: Float) = if (value.isFinite()) value else fallback
    private fun positive(value: Float) = finite(value, 0f).coerceAtLeast(0f)
    private fun overlaps(p: PopupPlacement, r: PopupSourceRect, gap: Float): Boolean =
        p.width > 0f && p.height > 0f &&
            p.x < r.right + gap - 0.01f && p.x + p.width > r.left - gap + 0.01f &&
            p.y < r.bottom + gap - 0.01f && p.y + p.height > r.top - gap + 0.01f
}
