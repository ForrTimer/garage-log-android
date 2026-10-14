package com.garagelog.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.garagelog.app.data.ai.AiRunState
import com.garagelog.app.data.ai.ClaudeSource
import com.garagelog.app.data.ai.ScheduleSuggestion
import com.garagelog.app.data.ai.scheduleKey
import com.garagelog.app.data.entity.AiChatMessageEntity
import com.garagelog.app.data.entity.AiDiagnosisEntity
import com.garagelog.app.data.entity.IssueEntity
import com.garagelog.app.data.entity.VehicleEntity
import com.garagelog.app.di.ServiceLocator
import com.garagelog.app.ui.locator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bob: issue diagnoses, per-vehicle chats, schedule suggestions, and the API key he runs on. */
class BobViewModel(private val locator: ServiceLocator) : ViewModel() {

    /**
     * Keyed by what the request is for, and owned by a process-wide holder rather than this
     * ViewModel, so an answer in flight survives the screen being closed or the app being left.
     */
    val runs: StateFlow<Map<String, AiRunState>> = locator.aiRunHolder.runs

    val hasApiKey: StateFlow<Boolean> = locator.aiKeyStore.hasKey

    val diagnoses: StateFlow<Map<String, AiDiagnosisEntity>> =
        locator.aiRepository.observeDiagnoses()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val chatMessages: StateFlow<List<AiChatMessageEntity>> =
        locator.aiRepository.observeChat()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Both of these hand off to [com.garagelog.app.data.ai.AiWorker] rather than running here,
     * so the request outlives this ViewModel and the answer is saved even if the app is closed. */
    fun runDiagnosis(issue: IssueEntity) {
        locator.aiRunHolder.clearError(issue.id)
        locator.enqueueDiagnosis(issue.id, issue.title)
    }

    fun sendChatMessage(vehicleId: String, issueId: String?, question: String, label: String) {
        if (question.isBlank()) return
        locator.aiRunHolder.clearError(AiChatMessageEntity.threadKey(vehicleId, issueId))
        locator.enqueueChat(vehicleId, issueId, question.trim(), label)
    }

    fun cancelDiagnosis(issueId: String) {
        locator.cancelAiWork("ai-diagnosis-$issueId")
        locator.aiRunHolder.finish(issueId)
    }

    fun cancelChat(vehicleId: String, issueId: String?) {
        locator.cancelAiWork("ai-chat-${issueId ?: vehicleId}")
        locator.aiRunHolder.finish(AiChatMessageEntity.threadKey(vehicleId, issueId))
    }

    fun clearChat(vehicleId: String, issueId: String?) = viewModelScope.launch {
        locator.aiRepository.clearThread(vehicleId, issueId)
    }

    /**
     * Asks Bob for a maintenance schedule for [vehicle] (possibly not saved yet). Progress shows
     * through [runs] under [scheduleKey]; the result comes back through [onResult] rather than
     * the database, since nothing is saved until the owner has reviewed it.
     */
    fun suggestSchedule(
        vehicle: VehicleEntity,
        alreadyTracked: List<String>,
        onResult: (List<ScheduleSuggestion>) -> Unit,
    ) = viewModelScope.launch {
        locator.aiRunHolder.clearError(scheduleKey(vehicle.id))
        try {
            onResult(locator.aiRepository.suggestSchedule(vehicle, alreadyTracked))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Already recorded on the run holder for the screen; nothing else to do.
        }
    }

    fun decodeSources(sourcesJson: String): List<ClaudeSource> = locator.aiRepository.decodeSources(sourcesJson)

    fun setApiKey(key: String) {
        locator.aiKeyStore.setApiKey(key)
        locator.userMessages.post("API key saved.")
    }

    fun clearApiKey() {
        locator.aiKeyStore.clear()
        locator.userMessages.post("API key removed.")
    }

    fun apiKeyHint(): String? = locator.aiKeyStore.keyHint()

    companion object {
        val Factory = viewModelFactory { initializer { BobViewModel(locator()) } }
    }
}
