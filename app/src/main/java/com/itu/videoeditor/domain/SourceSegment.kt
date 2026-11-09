package com.itu.videoeditor.domain

/**
 * Immutable source-relative clip ranges. This model does not alter the original media.
 * Persistence and timeline integration are intentionally separate implementation stages.
 */
data class SourceSegment(val startUs: Long, val endUs: Long) {
    init {
        require(startUs >= 0L) { "Negative source start" }
        require(endUs > startUs) { "Empty or reversed segment" }
    }
    val durationUs: Long get() = endUs - startUs
}

data class SplitResult(val left: SourceSegment, val right: SourceSegment)

/** Split strictly inside a segment; never create a zero-duration clip. */
fun splitSourceSegment(segment: SourceSegment, atUs: Long): SplitResult {
    require(atUs > segment.startUs && atUs < segment.endUs) {
        "Split position must be strictly inside the source segment"
    }
    return SplitResult(
        SourceSegment(segment.startUs, atUs),
        SourceSegment(atUs, segment.endUs)
    )
}

/** Preserve segment order and the original source-relative boundaries. */
fun splitSegmentAt(segments: List<SourceSegment>, index: Int, atUs: Long): List<SourceSegment> {
    require(index in segments.indices) { "Invalid segment index" }
    val split = splitSourceSegment(segments[index], atUs)
    return buildList(segments.size + 1) {
        addAll(segments.subList(0, index))
        add(split.left)
        add(split.right)
        addAll(segments.subList(index + 1, segments.size))
    }
}
