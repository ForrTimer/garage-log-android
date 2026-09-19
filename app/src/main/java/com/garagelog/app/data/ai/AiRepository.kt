package com.garagelog.app.data.ai

import com.garagelog.app.data.dao.AiChatDao
import com.garagelog.app.data.dao.AiDiagnosisDao
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.LogEntryEntity
import com.garagelog.app.data.entity.MaintenanceScheduleEntity
import com.garagelog.app.data.entity.VehicleEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Owns every Claude-backed operation: assembles the vehicle context, streams the answer, and
 * persists the result. Callers pass a delta callback so the UI can render tokens as they land,
 * but they never see the transport — swapping [ClaudeClient] for a proxy-backed one changes
 * nothing here or above.
 */
class AiRepository(
    private val client: ClaudeClient,
    private val diagnosisDao: AiDiagnosisDao,
    private val chatDao: AiChatDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sourceListSerializer = ListSerializer(ClaudeSource.serializer())

    fun observeDiagnoses(): Flow<Map<String, AiDiagnosisEntity>> =
        diagnosisDao.observeAll().map { list -> list.associateBy { it.issueId } }

    fun observeChat(): Flow<List<AiChatMessageEntity>> = chatDao.observeAll()

    suspend fun diagnose(
        issue: IssueEntity,
        vehicle: VehicleEntity,
        schedules: List<MaintenanceScheduleEntity>,
        logs: List<LogEntryEntity>,
        issues: List<IssueEntity>,
        onDelta: (String) -> Unit,
        onSearching: () -> Unit,
    ): AiDiagnosisEntity {
        val prompt = buildString {
            appendLine(GarageContext.vehicleProfile(vehicle, schedules, logs, issues))
            appendLine()
            appendLine(GarageContext.issueDetail(issue))
        }
        val result = collect(
            ClaudeRequest(
                system = AiPrompts.DIAGNOSIS_SYSTEM,
                messages = listOf(ClaudeMessage("user", prompt)),
            ),
            onDelta,
            onSearching,
        )
        val diagnosis = AiDiagnosisEntity(
            issueId = issue.id,
            vehicleId = issue.vehicleId,
            content = result.text,
            sourcesJson = encodeSources(result.sources),
            model = DirectClaudeClient.MODEL,
            createdAt = System.currentTimeMillis(),
            milesAtRun = vehicle.miles,
        )
        diagnosisDao.upsert(diagnosis)
        return diagnosis
    }

    /**
     * [history] is the conversation *before* this question. The vehicle profile is prepended to
     * the conversation's first user message rather than put in the system prompt, so the system
     * prompt stays byte-identical across every request and keeps its cache hit, while the profile
     * still sits ahead of everything the model reads.
     */
    suspend fun sendChatMessage(
        vehicle: VehicleEntity,
        schedules: List<MaintenanceScheduleEntity>,
        logs: List<LogEntryEntity>,
        issues: List<IssueEntity>,
        history: List<AiChatMessageEntity>,
        question: String,
        onDelta: (String) -> Unit,
        onSearching: () -> Unit,
    ): AiChatMessageEntity {
        // Persisted before the call so the question shows in the thread immediately; if the call
        // then fails, the question stays put and the owner can retry without retyping it.
        chatDao.upsert(
            AiChatMessageEntity(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                role = "user",
                content = question,
                sourcesJson = "",
                createdAt = System.currentTimeMillis(),
            ),
        )

        val profile = GarageContext.vehicleProfile(vehicle, schedules, logs, issues)
        val messages = if (history.isEmpty()) {
            listOf(ClaudeMessage("user", "$profile\n\n---\n\n$question"))
        } else {
            buildList {
                add(ClaudeMessage("user", "$profile\n\n---\n\n${history.first().content}"))
                history.drop(1).forEach { add(ClaudeMessage(it.role, it.content)) }
                add(ClaudeMessage("user", question))
            }
        }

        val result = collect(
            ClaudeRequest(system = AiPrompts.CHAT_SYSTEM, messages = messages),
            onDelta,
            onSearching,
        )
        val reply = AiChatMessageEntity(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicle.id,
            role = "assistant",
            content = result.text,
            sourcesJson = encodeSources(result.sources),
            createdAt = System.currentTimeMillis(),
        )
        chatDao.upsert(reply)
        return reply
    }

    suspend fun clearChat(vehicleId: String) = chatDao.deleteForVehicle(vehicleId)

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

    private fun encodeSources(sources: List<ClaudeSource>): String =
        if (sources.isEmpty()) "" else json.encodeToString(sourceListSerializer, sources)

    private data class StreamResult(val text: String, val sources: List<ClaudeSource>)

    private suspend fun collect(
        request: ClaudeRequest,
        onDelta: (String) -> Unit,
        onSearching: () -> Unit,
    ): StreamResult {
        val text = StringBuilder()
        // Several searches can run in one answer and the same page can come back twice; keyed by
        // URL so the source list the owner sees is deduplicated but keeps first-seen order.
        val sources = LinkedHashMap<String, ClaudeSource>()
        client.stream(request).collect { event ->
            when (event) {
                is ClaudeEvent.TextDelta -> {
                    text.append(event.text)
                    onDelta(event.text)
                }
                is ClaudeEvent.Searching -> onSearching()
                is ClaudeEvent.SourcesFound -> event.sources.forEach { sources.putIfAbsent(it.url, it) }
            }
        }
        return StreamResult(text.toString().trim(), sources.values.toList())
    }
}
