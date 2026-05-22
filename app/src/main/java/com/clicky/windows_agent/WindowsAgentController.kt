package com.clicky.windows_agent

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentUiState(
    val isConnected: Boolean = false,
    val isChecking: Boolean = false,
    val statusMessage: String = "Not connected",
    val hostname: String = "",
    val ip: String = "",
    val port: Int = 18765,
    val executorAvailable: Boolean = false,
    val lastCommandResult: String = "",
    val lastScreenshot: android.graphics.Bitmap? = null,
    val scheduledTasks: List<ScheduledTask> = emptyList(),
    val availableSkills: List<SkillInfo> = emptyList(),
    val isExecuting: Boolean = false,
    val error: String? = null
)

class WindowsAgentController(context: Context) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("clicky_agent", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private var client: WindowsAgentClient? = null
    private var savedBaseUrl: String = ""
    private var savedToken: String = ""

    init {
        savedBaseUrl = prefs.getString("base_url", "") ?: ""
        savedToken = prefs.getString("auth_token", "") ?: ""
        if (savedBaseUrl.isNotBlank()) {
            client = WindowsAgentClient(savedBaseUrl, savedToken)
        }
    }

    fun connect(baseUrl: String, token: String = "") {
        savedBaseUrl = baseUrl.trimEnd('/')
        savedToken = token
        prefs.edit()
            .putString("base_url", savedBaseUrl)
            .putString("auth_token", savedToken)
            .apply()

        client = WindowsAgentClient(savedBaseUrl, savedToken)
        checkStatus()
    }

    fun disconnect() {
        client = null
        _state.value = AgentUiState()
        prefs.edit().remove("base_url").remove("auth_token").apply()
    }

    fun checkStatus() {
        val agentClient = client ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isChecking = true, error = null)
            agentClient.getStatus().fold(
                onSuccess = { status ->
                    _state.value = _state.value.copy(
                        isConnected = true,
                        isChecking = false,
                        statusMessage = "Connected to ${status.hostname}",
                        hostname = status.hostname,
                        ip = status.ip,
                        port = status.port,
                        executorAvailable = status.executorAvailable,
                        error = null
                    )
                    loadScheduledTasks()
                    loadSkills()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isConnected = false,
                        isChecking = false,
                        statusMessage = "Connection failed",
                        error = e.message
                    )
                }
            )
        }
    }

    fun executeCommand(command: String) {
        val agentClient = client ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, error = null)
            agentClient.execute(command).fold(
                onSuccess = { result ->
                    val message = if (result.success) {
                        result.message ?: "Command executed"
                    } else {
                        result.error ?: "Command failed"
                    }
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        lastCommandResult = message,
                        error = if (result.success) null else message
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        lastCommandResult = "",
                        error = e.message
                    )
                }
            )
        }
    }

    fun captureScreenshot() {
        val agentClient = client ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, error = null)
            agentClient.captureScreenshot().fold(
                onSuccess = { bitmap ->
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        lastScreenshot = bitmap,
                        error = null
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun scheduleTask(
        name: String,
        command: String,
        triggerAt: String? = null,
        recurring: Boolean = false,
        intervalHours: Int = 24
    ) {
        val agentClient = client ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, error = null)
            agentClient.scheduleTask(name, command, triggerAt, recurring, intervalHours).fold(
                onSuccess = { result ->
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        error = if (result.success) null else result.error
                    )
                    if (result.success) {
                        loadScheduledTasks()
                    }
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isExecuting = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun deleteScheduledTask(taskId: String) {
        val agentClient = client ?: return
        viewModelScope.launch {
            agentClient.deleteScheduledTask(taskId).fold(
                onSuccess = {
                    loadScheduledTasks()
                },
                onFailure = { }
            )
        }
    }

    fun loadScheduledTasks() {
        val agentClient = client ?: return
        viewModelScope.launch {
            agentClient.listScheduledTasks().fold(
                onSuccess = { tasks ->
                    _state.value = _state.value.copy(scheduledTasks = tasks)
                },
                onFailure = { }
            )
        }
    }

    fun loadSkills() {
        val agentClient = client ?: return
        viewModelScope.launch {
            agentClient.listSkills().fold(
                onSuccess = { skills ->
                    _state.value = _state.value.copy(availableSkills = skills)
                },
                onFailure = { }
            )
        }
    }

    fun clearLastScreenshot() {
        _state.value = _state.value.copy(lastScreenshot = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun hasSavedConnection(): Boolean {
        return savedBaseUrl.isNotBlank()
    }

    fun getSavedBaseUrl(): String = savedBaseUrl

    fun getSavedToken(): String = savedToken
}