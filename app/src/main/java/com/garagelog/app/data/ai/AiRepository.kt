package com.garagelog.app.data.ai

import com.garagelog.app.data.dao.AiChatDao
import com.garagelog.app.data.dao.AiDiagnosisDao
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.data.repository.IssueRepository
import com.garagelog.app.data.repository.LogRepository
import com.garagelog.app.data.repository.ScheduleRepository
import com.garagelog.app.data.repository.VehicleRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Owns every assistant-backed operation end to end: given only an id, it gathers the vehicle's
 * context itself, streams the answer, reports progress to [AiRunHolder], and persists the result.
 *
 * Self-sufficient on purpose — the caller is a background worker that may outlive the screen that
 * started it, so it can't depend on UI state being around to hand it a vehicle and its history.
 */
class AiRepository(
    private val client: ClaudeClient,
    private val diagnosisDao: AiDiagnosisDao,
    private val chatDao: AiChatDao,
    private val vehicleRepository: VehicleRepository,
    private val logRepository: LogRepository,
    private val issueRepository: IssueRepository,
    private val scheduleRepository: ScheduleRepository,
    private val runHolder: AiRunHolder,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourceListSerializer = ListSerializer(ClaudeSource.serializer())

    fun observeDiagnoses(): Flow<Map<String, AiDiagnosisEntity>> =
        diagnosisDao.observeAll().map { list -> list.associateBy { it.issueId } }

    fun observeChat(): Flow<List<AiChatMessageEntity>> = chatDao.observeAll()

    /** Throws [ClaudeException] on failure, after recording it against [issueId] for the UI. */
    suspend fun runDiagnosis(issueId: String) {
        val issue = issueRepository.getAll().find { it.id == issueId }
            ?: error("That issue no longer exists.")
        val vehicle = vehicleRepository.getAll().find { it.id == issue.vehicleId }
            ?: error("That issue's vehicle no longer exists.")

        val prompt = buildString {
            appendLine(profileFor(vehicle.id))
            appendLine()
            appendLine(GarageContext.issueDetail(issue))
        }

        val result = collect(
            key = issueId,
            request = ClaudeRequest(
                system = AiPrompts.DIAGNOSIS_SYSTEM,
                messages = listOf(ClaudeMessage("user", prompt)),
            ),
        )
        diagnosisDao.upsert(
            AiDiagnosisEntity(
                issueId = issue.id,
                vehicleId = issue.vehicleId,
                content = result.text,
                sourcesJson = encodeSources(result.sources),
                model = DirectClaudeClient.DIAGNOSIS_MODEL,
                createdAt = System.currentTimeMillis(),
                milesAtRun = vehicle.miles,
            ),
        )
    }

    /**
     * [issueId] non-null makes this a follow-up on that issue's diagnosis: the thread is seeded
     * with the issue and the diagnosis already given, so the first question can just be "why?"
     * without the owner restating any of it.
     */
    suspend fun runChat(vehicleId: String, issueId: String?, question: String) {
        val vehicle = vehicleRepository.getAll().find { it.id == vehicleId }
            ?: error("That vehicle no longer exists.")
        val key = AiChatMessageEntity.threadKey(vehicleId, issueId)
        val history = chatDao.getAll().filter { it.vehicleId == vehicleId && it.issueId == issueId }
            .sortedBy { it.createdAt }

        val opening = buildString {
            appendLine(profileFor(vehicleId))
            if (issueId != null) {
                val issue = issueRepository.getAll().find { it.id == issueId }
                if (issue != null) {
                    appendLine()
                    appendLine(GarageContext.issueDetail(issue))
                }
                diagnosisDao.getForIssue(issueId)?.let { diagnosis ->
                    appendLine()
                    appendLine("## The diagnosis you already gave for this issue")
                    appendLine(diagnosis.content)
                }
                appendLine()
                appendLine("The owner is following up on that diagnosis. Don't repeat it back to them.")
            }
        }

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

        val messages = if (history.isEmpty()) {
            listOf(ClaudeMessage("user", "$opening\n\n---\n\n$question"))
        } else {
            buildList {
                add(ClaudeMessage("user", "$opening\n\n---\n\n${history.first().content}"))
                history.drop(1).forEach { add(ClaudeMessage(it.role, it.content)) }
                add(ClaudeMessage("user", question))
            }
        }

        val result = collect(
            key,
            ClaudeRequest(
                system = AiPrompts.CHAT_SYSTEM,
                messages = messages,
                model = DirectClaudeClient.CHAT_MODEL,
                // A short answer needs far less room; the cap also discourages rambling.
                maxTokens = 2000,
            ),
        )
        chatDao.upsert(
            AiChatMessageEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicleId,
                issueId = issueId,
                role = "assistant",
                content = result.text,
                sourcesJson = encodeSources(result.sources),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearThread(vehicleId: String, issueId: String?) =
        chatDao.deleteForThread(vehicleId, issueId)

    suspend fun deleteDiagnosisForIssue(issueId: String) = diagnosisDao.deleteForIssue(issueId)

    suspend fun deleteForVehicle(vehicleId: String) {
        diagnosisDao.deleteForVehicle(vehicleId)
        chatDao.deleteForVehicle(vehicleId)
    }

    suspend fun deleteAll() {
        diagnosisDao.deleteAll()
        chatDao.deleteAll()
    }

    fun decodeSources(raw: String): List<ClaudeSource> =
        if (raw.isBlank()) emptyList()
        else runCatching { json.decodeFromString(sourceListSerializer, raw) }.getOrDefault(emptyList())

    private suspend fun profileFor(vehicleId: String): String {
        val vehicle = vehicleRepository.getAll().find { it.id == vehicleId } ?: error("Unknown vehicle")
        return GarageContext.vehicleProfile(
            vehicle = vehicle,
            schedules = scheduleRepository.getAll().filter { it.vehicleId == vehicleId },
            logs = logRepository.getAll().filter { it.vehicleId == vehicleId },
            issues = issueRepository.getAll().filter { it.vehicleId == vehicleId },
        )
    }

    private fun encodeSources(sources: List<ClaudeSource>): String =
        if (sources.isEmpty()) "" else json.encodeToString(sourceListSerializer, sources)

    /**
     * Researches and proposes a maintenance schedule for [vehicle] — which may not be saved yet,
     * when this runs from the Add vehicle form. Nothing is persisted: the owner reviews the list
     * and the caller saves what they keep. Runs in the caller's scope rather than a worker for the
     * same reason: without the form open there is nothing to show the result in.
     *
     * [alreadyTracked] is passed in rather than read from the database for the same unsaved-vehicle
     * reason; Bob is told to skip those.
     */
    suspend fun suggestSchedule(vehicle: VehicleEntity, alreadyTracked: List<String>): List<ScheduleSuggestion> {
        val prompt = buildString {
            appendLine(GarageContext.vehicleProfile(vehicle, schedules = emptyList(), logs = emptyList(), issues = emptyList()))
            appendLine()
            appendLine("## Already tracked (skip these)")
            if (alreadyTracked.isEmpty()) appendLine("Nothing yet.") else alreadyTracked.forEach { appendLine("- $it") }
        }
        val result = collect(
            key = scheduleKey(vehicle.id),
            request = ClaudeRequest(
                system = AiPrompts.SCHEDULE_SYSTEM,
                messages = listOf(ClaudeMessage("user", prompt)),
                model = DirectClaudeClient.CHAT_MODEL,
                customTools = listOf(AiPrompts.PROPOSE_SCHEDULE_TOOL),
            ),
        )
        val input = result.toolInputs[AiPrompts.PROPOSE_SCHEDULE_TOOL.name]
            ?: throw ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME didn't return a schedule. Try again.")
        return runCatching { json.decodeFromString(ProposedSchedule.serializer(), input).services }
            .getOrElse { throw ClaudeException(ClaudeErrorKind.Other, "$ASSISTANT_NAME's schedule came back unreadable. Try again.") }
            .filter { it.name.isNotBlank() }
    }

    private data class StreamResult(
        val text: String,
        val sources: List<ClaudeSource>,
        /** Complete JSON input of each custom tool Claude called, by tool name. */
        val toolInputs: Map<String, String> = emptyMap(),
    )

    private companion object {
        /** Waits before each automatic retry of an overloaded request; three tries, ~30s total. */
        val OVERLOAD_BACKOFF_MS = longArrayOf(4_000, 10_000, 20_000)
    }

    private suspend fun collect(key: String, request: ClaudeRequest): StreamResult {
        runHolder.start(key)
        val text = StringBuilder()
        // Several searches can run in one answer and the same page can come back twice; keyed by
        // URL so the source list stays deduplicated but keeps first-seen order.
        val sources = LinkedHashMap<String, ClaudeSource>()
        // Content-block index → tool name, for our own tool calls only; see ClaudeEvent.ToolInputDelta.
        val toolNames = HashMap<Int, String>()
        val toolInputs = HashMap<Int, StringBuilder>()
        try {
            var attempt = 0
            while (true) {
                try {
                    client.stream(request).collect { event ->
                        when (event) {
                            is ClaudeEvent.TextDelta -> {
                                text.append(event.text)
                                runHolder.appendDelta(key, event.text)
                            }
                            is ClaudeEvent.Searching -> runHolder.markSearching(key)
                            is ClaudeEvent.SourcesFound -> event.sources.forEach { sources.putIfAbsent(it.url, it) }
                            is ClaudeEvent.ToolCallStarted -> {
                                toolNames[event.index] = event.name
                                runHolder.markWriting(key)
                            }
                            is ClaudeEvent.ToolInputDelta ->
                                if (event.index in toolNames) toolInputs.getOrPut(event.index) { StringBuilder() }.append(event.partialJson)
                        }
                    }
                    break
                } catch (e: ClaudeException) {
                    // "Overloaded" is Anthropic shedding load, not anything wrong with the request,
                    // and it usually clears in seconds — so ride it out rather than making the owner
                    // tap retry. Only before any answer has arrived, though: once output is flowing,
                    // a retry would be paying twice for the same answer.
                    if (e.kind != ClaudeErrorKind.Overloaded || text.isNotEmpty() || toolInputs.isNotEmpty() ||
                        attempt >= OVERLOAD_BACKOFF_MS.size
                    ) throw e
                    runHolder.markRetrying(key)
                    delay(OVERLOAD_BACKOFF_MS[attempt++])
                    sources.clear()
                    toolNames.clear()
                    runHolder.start(key)
                }
            }
        } catch (e: CancellationException) {
            // Being stopped is not a failure and must never be shown as one. This is what made a
            // backgrounded request report "couldn't reach Bob": the OS stops the work, the socket
            // dies, and an indiscriminate catch turned that into a connection error on screen.
            runHolder.finish(key)
            throw e
        } catch (e: Throwable) {
            runHolder.fail(key, (e as? ClaudeException)?.userMessage ?: e.message ?: "Something went wrong.")
            throw e
        }
        runHolder.finish(key)
        return StreamResult(
            text = text.toString().trim(),
            sources = sources.values.toList(),
            toolInputs = toolInputs.mapKeys { (index, _) -> toolNames.getValue(index) }.mapValues { it.value.toString() },
        )
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
