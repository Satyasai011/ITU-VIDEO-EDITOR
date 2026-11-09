package com.itu.videoeditor.ui

import com.itu.videoeditor.data.*
import kotlinx.coroutines.CancellationException

/** Read-only bounded navigation over I2.2B's opaque forward-only cursors. */
class HistoryPager(private val read: suspend (String, String?, Int) -> RevisionHistoryResult) {
    data class Page(val index: Int, val entries: List<RevisionHistoryEntry>, val requestCursor: String?, val nextCursor: String?)
    sealed interface Outcome {
        data class Ready(val pages: List<Page>, val hasNewer: Boolean, val hasOlder: Boolean) : Outcome
        data class Paused(val completed: Int, val target: Int) : Outcome
        data class Failure(val message: String, val missingProject: Boolean = false) : Outcome
    }
    private var project = ""
    private val pages = mutableListOf<Page>()
    private val cursors = linkedMapOf<Int, String?>()
    private var pending: Replay? = null
    private data class Replay(val target: Int, var page: Int, var cursor: String?)
    val loaded: List<Page> get() = pages.toList()
    val pendingReplay: Boolean get() = pending != null

    fun reset(projectId: String) {
        project = projectId
        pages.clear()
        cursors.clear()
        cursors[1] = null
        pending = null
    }
    fun cancelReplay() { pending = null }
    private fun rememberCursor(index: Int, cursor: String?) {
        cursors.remove(index)
        cursors[index] = cursor
        while (cursors.size > 32) {
            val protected = pages.map { it.index }.toSet() + 1
            val victim = cursors.keys.firstOrNull { it !in protected } ?: break
            cursors.remove(victim)
        }
    }
    private fun snapshot() = Outcome.Ready(pages.toList(), pages.firstOrNull()?.index?.let { it > 1 } ?: false,
        pages.lastOrNull()?.nextCursor != null)
    private fun validate(index: Int, cursor: String?, result: RevisionHistoryResult): Page? {
        if (result !is RevisionHistoryResult.Page || result.projectId != project) return null
        if (result.entries.size > 20 || result.entries.map { it.id }.distinct().size != result.entries.size) return null
        if (result.entries.zipWithNext().any { (a,b) -> a.revisionNumber <= b.revisionNumber }) return null
        return Page(index, result.entries, cursor, result.nextCursor)
    }
    private suspend fun fetch(index: Int, cursor: String?): Pair<Page?, Outcome.Failure?> {
        val result = read(project, cursor, 20)
        if (result is RevisionHistoryResult.ProjectNotFound) return null to Outcome.Failure("Project unavailable", true)
        if (result is RevisionHistoryResult.InvalidRequest) return null to Outcome.Failure("History navigation expired. Reload history.")
        if (result is RevisionHistoryResult.ReadFailure) return null to Outcome.Failure("Unable to load history. Try again.")
        return validate(index, cursor, result) to null
    }
    suspend fun first(projectId: String): Outcome {
        val oldProject = project
        val (page, error) = run {
            val result = read(projectId, null, 20)
            when (result) {
                is RevisionHistoryResult.Page -> {
                    if (result.projectId != projectId || result.entries.size > 20 ||
                        result.entries.zipWithNext().any { (a,b) -> a.revisionNumber <= b.revisionNumber })
                        null to Outcome.Failure("Invalid history response")
                    else Page(1, result.entries, null, result.nextCursor) to null
                }
                is RevisionHistoryResult.ProjectNotFound -> null to Outcome.Failure("Project unavailable", true)
                else -> null to Outcome.Failure("Unable to load history. Try again.")
            }
        }
        if (error != null) return error
        if (page == null) return Outcome.Failure("Unable to load history")
        reset(projectId)
        pages.add(page)
        if (page.nextCursor != null) rememberCursor(2, page.nextCursor)
        return snapshot()
    }
    suspend fun older(): Outcome {
        val last = pages.lastOrNull() ?: return Outcome.Failure("History not loaded")
        val cursor = last.nextCursor ?: return snapshot()
        val (page,error) = fetch(last.index + 1, cursor)
        if (error != null) return error
        if (page == null || page.entries.isEmpty() || pages.any { existing ->
            existing.entries.any { a -> page.entries.any { b -> a.id == b.id } }
        }) return Outcome.Failure("Invalid history page. Reload history.")
        pages.add(page)
        if (pages.size > 10) pages.removeAt(0)
        rememberCursor(page.index, cursor)
        if (page.nextCursor != null) rememberCursor(page.index + 1, page.nextCursor)
        return snapshot()
    }
    suspend fun newer(): Outcome {
        val first = pages.firstOrNull() ?: return Outcome.Failure("History not loaded")
        if (first.index == 1) return snapshot()
        val target = first.index - 1
        val cursor = cursors[target]
        if (cursors.containsKey(target)) return completeNewer(target, cursor)
        val anchor = cursors.keys.filter { it < target }.maxOrNull() ?: 1
        pending = Replay(target, anchor, cursors[anchor])
        return continueReplay()
    }
    private suspend fun completeNewer(index: Int, cursor: String?): Outcome {
        val (page,error) = fetch(index,cursor)
        if (error != null) return error
        if (page == null || page.nextCursor != pages.firstOrNull()?.requestCursor ||
            page.entries.any { e -> pages.any { p -> p.entries.any { it.id == e.id } } })
            return Outcome.Failure("History boundary changed. Reload history.")
        pages.add(0,page)
        if (pages.size > 10) pages.removeAt(pages.lastIndex)
        rememberCursor(index,cursor)
        return snapshot()
    }
    suspend fun continueReplay(): Outcome {
        val replay = pending ?: return Outcome.Failure("No navigation to continue")
        var reads = 0
        while (replay.page < replay.target && reads < 10) {
            val (page,error) = fetch(replay.page,replay.cursor)
            if (error != null) return error
            if (page == null || page.nextCursor == null) return Outcome.Failure("Navigation expired. Reload history.")
            replay.page++
            replay.cursor = page.nextCursor
            reads++
        }
        if (replay.page < replay.target) return Outcome.Paused(replay.page,replay.target)
        val result = completeNewer(replay.target,replay.cursor)
        if (result is Outcome.Ready) pending = null
        return result
    }
}
