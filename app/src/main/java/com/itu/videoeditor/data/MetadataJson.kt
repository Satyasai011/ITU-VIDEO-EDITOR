package com.itu.videoeditor.data

import com.itu.videoeditor.domain.*
import org.json.JSONArray
import org.json.JSONObject

object MetadataJson {
    fun encode(info: MediaInfo): String = JSONObject().apply {
        put("width", info.width); put("height", info.height); put("rotation", info.rotation)
        put("fps", info.frameRate); put("fpsEvidence", info.frameRateEvidence); put("variable", info.variableTimingObserved)
        put("codec", info.codec); put("videoBitrate", info.videoBitrate); put("containerBitrate", info.containerBitrate)
        put("durationUs", info.durationUs); put("sizeBytes", info.sizeBytes); put("error", info.error)
        info.audio?.let { tracks ->
            put("audio", JSONArray().apply {
                tracks.forEach { track ->
                    put(JSONObject().apply {
                        put("codec", track.codec); put("sampleRate", track.sampleRate); put("channels", track.channels)
                        put("bitrate", track.bitrate); put("language", track.language)
                    })
                }
            })
        }
    }.toString()

    fun decode(raw: String): MediaInfo = runCatching {
        val json = JSONObject(raw)
        val audio = json.optJSONArray("audio")?.let { tracks ->
            (0 until tracks.length()).map { index ->
                val track = tracks.getJSONObject(index)
                AudioInfo(track.text("codec"), track.int("sampleRate"), track.int("channels"), track.long("bitrate"), track.text("language"))
            }
        }
        MediaInfo(
            width = json.int("width"), height = json.int("height"), rotation = json.int("rotation"),
            frameRate = json.double("fps"), frameRateEvidence = json.text("fpsEvidence"),
            variableTimingObserved = if (json.has("variable") && !json.isNull("variable")) json.getBoolean("variable") else null,
            codec = json.text("codec"), videoBitrate = json.long("videoBitrate"), containerBitrate = json.long("containerBitrate"),
            durationUs = json.long("durationUs"), sizeBytes = json.long("sizeBytes"), audio = audio, error = json.text("error")
        )
    }.getOrElse { MediaInfo(error = "Stored metadata could not be read") }

    fun format(value: ProjectFormat) = JSONObject().apply {
        put("width", value.width); put("height", value.height); put("fps", value.frameRate)
    }.toString()
    fun readFormat(raw: String?): ProjectFormat? = raw?.let {
        runCatching { JSONObject(it).let { json -> ProjectFormat(json.int("width"), json.int("height"), json.double("fps")) } }.getOrNull()
    }
}
private fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONObject.double(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
private fun JSONObject.text(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
