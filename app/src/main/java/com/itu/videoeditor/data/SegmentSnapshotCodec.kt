package com.itu.videoeditor.data

import com.itu.videoeditor.domain.SourceSegment
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, source-relative timeline snapshot payload.
 * Designed for storage in existing immutable project_revisions.snapshotJson.
 * Does not change the Room schema or write revisions by itself.
 */
object SegmentSnapshotCodec {
    private const val VERSION = 1

    fun encode(projectId: String, sourceUri: String, segments: List<SourceSegment>): String {
        require(projectId.isNotBlank() && sourceUri.isNotBlank())
        require(segments.isNotEmpty()) { "Timeline requires at least one segment" }
        val clips = JSONArray()
        segments.forEach { segment ->
            clips.put(JSONObject().put("startUs", segment.startUs).put("endUs", segment.endUs))
        }
        return JSONObject()
            .put("timelineVersion", VERSION)
            .put("projectId", projectId)
            .put("sourceUri", sourceUri)
            .put("segments", clips)
            .toString()
    }

    fun decode(payload: String, expectedProjectId: String, expectedSourceUri: String, durationUs: Long): List<SourceSegment> {
        require(durationUs > 0L)
        val root = JSONObject(payload)
        require(root.getInt("timelineVersion") == VERSION) { "Unsupported timeline version" }
        require(root.getString("projectId") == expectedProjectId) { "Wrong project" }
        require(root.getString("sourceUri") == expectedSourceUri) { "Source changed" }
        val clips = root.getJSONArray("segments")
        require(clips.length() in 1..10000) { "Invalid clip count" }
        return (0 until clips.length()).map { index ->
            val clip = clips.getJSONObject(index)
            val segment = SourceSegment(clip.getLong("startUs"), clip.getLong("endUs"))
            require(segment.endUs <= durationUs) { "Segment exceeds source duration" }
            segment
        }
    }
}
