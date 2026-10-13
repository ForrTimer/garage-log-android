package com.garagelog.app.data.ai

import com.garagelog.app.data.dao.AiChatDao
import com.garagelog.app.data.dao.AiDiagnosisDao
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.VehicleRepository
import com.garagelog.app.util.todayIso
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Owns every assistant-backed operation end to end: given only an id, it brings Bob's copy of the
 * records up to date, runs the turn, reports progress to [AiRunHolder], and persists the result.
 *
 * Self-sufficient on purpose — the caller is a background worker that may outlive the screen that
 * started it, so it can't depend on UI state being around to hand it a vehicle and its history.
 *
 * Bob runs as a Managed Agent ([BobAgent]): the vehicle's records live in a memory store he looks
 * things up in ([GarageMirror]), so each message carries the question and which vehicle, not the
 * whole history. [DirectClaudeClient] is no longer used here; it stays as the fallback path.
 */
class AiRepository(
    private val bob: BobAgent,
    private val mirror: GarageMirror,
    private val diagnosisDao: AiDiagnosisDao,
    private val chatDao: AiChatDao,
    private val vehicleRepository: VehicleRepository,
    private val issueRepository: IssueRepository,
    private val runHolder: AiRunHolder,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourceListSerializer = ListSerializer(ClaudeSource.serializer())

    fun observeDiagnoses(): Flow<Map<String, AiDiagnosisEntity>> =
        diagnosisDao.observeAll().map { list -> list.associateBy { it.issueId } }

    fun observeChat(): Flow<List<AiChatMessageEntity>> = chatDao.observeAll()

    /** Throws [ClaudeException] on failure, after recording it against [issueId] for the UI. */
    suspend fun runDiagnosis(issueId: String) = reporting(issueId) {
        val issue = issueRepository.getAll().find { it.id == issueId }
            ?: error("That issue no longer exists.")
        val vehicle = vehicleRepository.getAll().find { it.id == issue.vehicleId }
            ?: error("That issue's vehicle no longer exists.")
        mirror.sync()

        val message = buildString {
            appendLine("Kind: Diagnosis")
            appendLine(vehicleLine(vehicle))
            appendLine("Today: ${todayIso()}")
            appendLine()
            append(GarageContext.issueDetail(issue))
        }
        // A re-run is a fresh look, not a continuation of the last one.
        val diagnosisThread = diagnosisThreadKey(issueId)
        bob.forget(diagnosisThread)
        val turn = bob.runTurn(
            runKey = issueId,
            threadKey = diagnosisThread,
            title = "Diagnosis: ${issue.title}",
            opening = { message },
            followUp = message,
        )
        if (turn.text.isEmpty()) throw budgetSpent()
        diagnosisDao.upsert(
            AiDiagnosisEntity(
                issueId = issue.id,
                vehicleId = issue.vehicleId,
                content = turn.text + if (turn.budgetReached) BUDGET_NOTE else "",
                sourcesJson = encodeSources(turn.sources),
                model = MODEL_LABEL,
                createdAt = System.currentTimeMillis(),
                milesAtRun = vehicle.miles,
            ),
        )
        // "Ask a follow-up" carries on in the same session, so Bob already has his diagnosis in
        // context instead of being handed it back as text.
        bob.sessionFor(diagnosisThread)?.let { bob.adoptSession(AiChatMessageEntity.threadKey(vehicle.id, issueId), it) }
    }

    /**
     * [issueId] non-null makes this a follow-up on that issue's diagnosis. A thread keeps its agent
     * session, so a follow-up sends only the question; a new session (the first question, or after
     * the last one hit its spend cap) is told the vehicle and given a short recap of the thread.
     */
    suspend fun runChat(vehicleId: String, issueId: String?, question: String) {
        val key = AiChatMessageEntity.threadKey(vehicleId, issueId)
        reporting(key) {
            val vehicle = vehicleRepository.getAll().find { it.id == vehicleId }
                ?: error("That vehicle no longer exists.")
            val history = chatDao.getAll().filter { it.vehicleId == vehicleId && it.issueId == issueId }
                .sortedBy { it.createdAt }
            val issue = issueId?.let { id -> issueRepository.getAll().find { it.id == id } }
            // Only needed when the diagnosis session is gone and the thread starts over.
            val diagnosis = issueId?.let { diagnosisDao.getForIssue(it) }
            val vehicleLine = vehicleLine(vehicle)
            mirror.sync()

            chatDao.upsert(
                AiChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    issueId = issueId,
                    role = "user",
                    content = question,
                    sourcesJson = "",
                    createdAt = System.currentTimeMillis(),
                ),
            )

            val opening = {
                buildString {
                    appendLine("Kind: Chat")
                    appendLine(vehicleLine)
                    appendLine("Today: ${todayIso()}")
                    if (issue != null) {
                        appendLine()
                        appendLine("The owner is following up on this issue:")
                        appendLine(GarageContext.issueDetail(issue))
                    }
                    if (diagnosis != null) {
                        appendLine()
                        appendLine("## The diagnosis you gave earlier (don't repeat it back)")
                        appendLine(diagnosis.content)
                    }
                    if (history.isNotEmpty()) {
                        appendLine()
                        appendLine("Earlier in this conversation (for context; don't answer these again):")
                        history.takeLast(RECAP_MESSAGES).forEach { message ->
                            val who = if (message.role == "user") "Owner" else ASSISTANT_NAME
                            appendLine("$who: ${message.content.take(RECAP_CHARS)}")
                        }
                    }
                    appendLine()
                    appendLine("---")
                    appendLine()
                    append(question)
                }
            }
            val turn = bob.runTurn(
                runKey = key,
                threadKey = key,
                title = issue?.let { "Follow-up: ${it.title}" } ?: "Chat: ${vehicle.name}",
                opening = opening,
                followUp = question,
                // An issue's follow-up stays on the diagnosis model; a plain chat runs cheap.
                model = if (issue == null) BobAgent.CHAT_MODEL else null,
            )
            if (turn.text.isEmpty()) throw budgetSpent()
            chatDao.upsert(
                AiChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    issueId = issueId,
                    role = "assistant",
                    content = turn.text + if (turn.budgetReached) BUDGET_NOTE else "",
                    sourcesJson = encodeSources(turn.sources),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Clearing a thread also drops its session, so the next question starts clean. */
    suspend fun clearThread(vehicleId: String, issueId: String?) {
        chatDao.deleteForThread(vehicleId, issueId)
        bob.forget(AiChatMessageEntity.threadKey(vehicleId, issueId))
    }

    suspend fun deleteDiagnosisForIssue(issueId: String) {
        diagnosisDao.deleteForIssue(issueId)
        bob.forget(diagnosisThreadKey(issueId))
    }

    suspend fun deleteForVehicle(vehicleId: String) {
        diagnosisDao.deleteForVehicle(vehicleId)
        chatDao.deleteForVehicle(vehicleId)
        bob.forget(AiChatMessageEntity.threadKey(vehicleId, null))
    }

    suspend fun deleteAll() {
        diagnosisDao.deleteAll()
        chatDao.deleteAll()
        bob.forgetAll()
    }

    fun decodeSources(raw: String): List<ClaudeSource> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString(sourceListSerializer, raw) }.getOrDefault(emptyList())

    private fun encodeSources(sources: List<ClaudeSource>): String =
        if (sources.isEmpty()) "" else json.encodeToString(sourceListSerializer, sources)

    private suspend fun vehicleLine(vehicle: VehicleEntity): String =
        "Vehicle: ${GarageContext.identity(vehicle)} — records in ${mirror.folderFor(vehicle)}"

    /**
     * Researches and proposes a maintenance schedule for [vehicle] — which may not be saved yet,
     * when this runs from the Add vehicle form, so its details go in the message rather than
     * coming from the records store. Nothing is persisted: the owner reviews the list and the
     * caller saves what they keep. Runs in the caller's scope rather than a worker for the same
     * reason: without the form open there is nothing to show the result in.
     */
    suspend fun suggestSchedule(vehicle: VehicleEntity, alreadyTracked: List<String>): List<ScheduleSuggestion> {
        val key = scheduleKey(vehicle.id)
        return reporting(key) {
            val message = buildString {
                appendLine("Kind: Schedule")
                appendLine("Today: ${todayIso()}")
                appendLine()
                appendLine(GarageContext.vehicleProfile(vehicle, schedules = emptyList(), logs = emptyList(), issues = emptyList()))
                appendLine()
                appendLine("## Already tracked (skip these)")
                if (alreadyTracked.isEmpty()) appendLine("Nothing yet.") else alreadyTracked.forEach { appendLine("- $it") }
            }
            val turn = bob.runTurn(
                runKey = key,
                threadKey = null,
                title = "Schedule: ${vehicle.name.ifBlank { GarageContext.identity(vehicle) }}",
                opening = { message },
                followUp = message,
                onCustomTool = { _, _ -> "Received. The owner will review these before anything is saved." },
            )
            val input = turn.toolInputs[AiPrompts.PROPOSE_SCHEDULE_TOOL.name]
                ?: throw if (turn.budgetReached) budgetSpent()
                // Bob declines rather than inventing intervals (a made-up vehicle, one with no published
                // schedule); his reason is more use to the owner than a generic failure.
                else if (turn.text.isNotBlank()) ClaudeException(ClaudeErrorKind.Other, turn.text, fromAssistant = true)
                else ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME didn't return a schedule. Try again.")
            runCatching { json.decodeFromString(ProposedSchedule.serializer(), input).services }
                .getOrElse { throw ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME's schedule came back unreadable. Try again.") }
                .filter { it.name.isNotBlank() }
        }
    }

    /**
     * Records a failure against [key] so whichever screen is watching can show it. Being stopped
     * is not a failure and must never be shown as one — that's what made a backgrounded request
     * once report "couldn't reach Bob".
     */
    private suspend fun <T> reporting(key: String, block: suspend () -> T): T {
        runHolder.start(key)
        return try {
            block().also { runHolder.finish(key) }
        } catch (e: CancellationException) {
            runHolder.finish(key)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$key failed", e)
            runHolder.fail(
                key,
                (e as? ClaudeException)?.userMessage ?: e.message ?: "Something went wrong.",
                declined = (e as? ClaudeException)?.fromAssistant == true,
            )
            throw e
        }
    }

    private fun budgetSpent() = ClaudeException(
        ClaudeErrorKind.Other,
        "This conversation reached its \$1 limit before $ASSISTANT_NAME finished. Ask again to start a fresh one.",
    )

    private companion object {
        const val TAG = "AiRepository"
        const val MODEL_LABEL = "Bob agent (claude-sonnet-5)"
        const val RECAP_MESSAGES = 6
        const val RECAP_CHARS = 800
        const val BUDGET_NOTE =
            "\n\n_This conversation reached its \$1 limit, so the next question starts a fresh one. " +
                "Anything worth remembering is in $ASSISTANT_NAME's notes._"

        fun diagnosisThreadKey(issueId: String) = "diagnosis:$issueId"
    }
}

/** One service Bob proposes; the owner edits and ticks these before anything is saved. */
@Serializable
data class ScheduleSuggestion(
    val name: String,
    @SerialName("interval_miles") val intervalMiles: Int? = null,
    @SerialName("interval_months") val intervalMonths: Int? = null,
    val note: String = "",
)

@Serializable
private data class ProposedSchedule(val services: List<ScheduleSuggestion> = emptyList())

/** [AiRunHolder] key for a schedule suggestion; distinct from issue ids and chat thread keys. */
fun scheduleKey(vehicleId: String) = "schedule/$vehicleId"
