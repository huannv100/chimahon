package eu.kanade.tachiyomi.ui.reader.viewer

internal data class PopupSourceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PopupPlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val after: Boolean,
)

internal object PopupPlacementPolicy {

    fun bestHorizontal(
        sources: List<PopupSourceRect>,
        preferredWidth: Float,
        preferredHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: Float,
        gap: Float,
        preferBelow: Boolean,
    ): PopupPlacement {
        val candidates = candidateSources(sources)
        var best: PopupPlacement? = null
        for ((index, source) in candidates.withIndex()) {
            val result = horizontal(
                source, preferredWidth, preferredHeight,
                viewportWidth, viewportHeight, padding, gap, preferBelow,
            )
            val isBoundingCandidate = index >= sources.size
            if (!isBoundingCandidate && overlapsAny(result, sources, ignoreIndex = index)) continue
            if (best == null || result.height > best.height) {
                best = result
                if (result.height >= preferredHeight) break
            }
        }
        return best ?: horizontal(
            boundingRect(sources), preferredWidth, preferredHeight,
            viewportWidth, viewportHeight, padding, gap, preferBelow,
        )
    }

    fun bestVertical(
        sources: List<PopupSourceRect>,
        preferredWidth: Float,
        preferredHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: Float,
        gap: Float,
        preferRight: Boolean,
    ): PopupPlacement {
        val candidates = candidateSources(sources)
        var best: PopupPlacement? = null
        for ((index, source) in candidates.withIndex()) {
            val result = vertical(
                source, preferredWidth, preferredHeight,
                viewportWidth, viewportHeight, padding, gap, preferRight,
            )
            val isBoundingCandidate = index >= sources.size
            if (!isBoundingCandidate && overlapsAny(result, sources, ignoreIndex = index)) continue
            if (best == null || result.width > best.width) {
                best = result
                if (result.width >= preferredWidth) break
            }
        }
        return best ?: vertical(
            boundingRect(sources), preferredWidth, preferredHeight,
            viewportWidth, viewportHeight, padding, gap, preferRight,
        )
    }


    fun horizontal(
        source: PopupSourceRect,
        preferredWidth: Float,
        preferredHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: Float,
        gap: Float,
        preferBelow: Boolean,
    ): PopupPlacement {
        val minX = padding
        val maxX = (viewportWidth - padding).coerceAtLeast(minX)
        val minY = padding
        val maxY = (viewportHeight - padding).coerceAtLeast(minY)

        val width = preferredWidth.coerceAtMost((maxX - minX).coerceAtLeast(1f))
        val centerX = (source.left + source.right) / 2f
        val x = (centerX - width / 2f)
            .coerceIn(minX, (maxX - width).coerceAtLeast(minX))

        val before = source.top - gap
        val after = source.bottom + gap
        val (y, height, placedAfter) = constrainedBinary(
            positionBefore = before,
            positionAfter = after,
            preferredSize = preferredHeight,
            minLimit = minY,
            maxLimit = maxY,
            preferAfter = preferBelow,
        )

        return PopupPlacement(x, y, width, height, placedAfter)
    }

    fun vertical(
        source: PopupSourceRect,
        preferredWidth: Float,
        preferredHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        padding: Float,
        gap: Float,
        preferRight: Boolean,
    ): PopupPlacement {
        val minX = padding
        val maxX = (viewportWidth - padding).coerceAtLeast(minX)
        val minY = padding
        val maxY = (viewportHeight - padding).coerceAtLeast(minY)

        val height = preferredHeight.coerceAtMost((maxY - minY).coerceAtLeast(1f))
        val centerY = (source.top + source.bottom) / 2f
        val y = (centerY - height / 2f)
            .coerceIn(minY, (maxY - height).coerceAtLeast(minY))

        val before = source.left - gap
        val after = source.right + gap
        val (x, width, placedAfter) = constrainedBinary(
            positionBefore = before,
            positionAfter = after,
            preferredSize = preferredWidth,
            minLimit = minX,
            maxLimit = maxX,
            preferAfter = preferRight,
        )

        return PopupPlacement(x, y, width, height, placedAfter)
    }

    private fun candidateSources(sources: List<PopupSourceRect>): List<PopupSourceRect> {
        if (sources.isEmpty()) return listOf(PopupSourceRect(0f, 0f, 1f, 1f))
        return if (sources.size > 1) sources + boundingRect(sources) else sources
    }

    private fun boundingRect(sources: List<PopupSourceRect>): PopupSourceRect {
        if (sources.isEmpty()) return PopupSourceRect(0f, 0f, 1f, 1f)
        return PopupSourceRect(
            left = sources.minOf { it.left },
            top = sources.minOf { it.top },
            right = sources.maxOf { it.right },
            bottom = sources.maxOf { it.bottom },
        )
    }

    private fun overlapsAny(
        placement: PopupPlacement,
        sources: List<PopupSourceRect>,
        ignoreIndex: Int,
    ): Boolean = sources.indices.any { i ->
        if (i == ignoreIndex) return@any false
        val source = sources[i]
        placement.x < source.right &&
            placement.x + placement.width > source.left &&
            placement.y < source.bottom &&
            placement.y + placement.height > source.top
    }

    /**
     * Yomitan-style binary placement.
     *
     * Compare overflow on both sides of the source rectangle. If the preferred
     * side cannot fit, choose the side with less overflow. Crucially, shrink the
     * popup to the available space instead of clamping it across the source text.
     */
    private fun constrainedBinary(
        positionBefore: Float,
        positionAfter: Float,
        preferredSize: Float,
        minLimit: Float,
        maxLimit: Float,
        preferAfter: Boolean,
    ): Triple<Float, Float, Boolean> {
        val maxSize = (maxLimit - minLimit).coerceAtLeast(1f)
        val requested = preferredSize.coerceAtMost(maxSize)

        val overflowBefore = minLimit - (positionBefore - requested)
        val overflowAfter = (positionAfter + requested) - maxLimit

        var after = preferAfter
        if (overflowAfter > 0f || overflowBefore > 0f) {
            after = overflowAfter < overflowBefore
        }

        return if (after) {
            val size = (requested - maxOf(0f, overflowAfter)).coerceAtLeast(1f)
            val position = maxOf(minLimit, positionAfter)
            Triple(position, size, true)
        } else {
            val size = (requested - maxOf(0f, overflowBefore)).coerceAtLeast(1f)
            val position = minOf(maxLimit, positionBefore) - size
            Triple(position, size, false)
        }
    }
}
