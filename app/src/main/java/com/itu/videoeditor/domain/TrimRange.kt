package com.itu.videoeditor.domain

/** Source-relative microseconds. No constant-frame-rate assumption. */
data class TrimRange(val startUs: Long, val endUs: Long, val sourceDurationUs: Long) {
    init {
        require(sourceDurationUs > 0)
        require(startUs >= 0 && endUs <= sourceDurationUs && startUs < endUs)
    }
    val durationUs: Long get() = endUs - startUs
}
