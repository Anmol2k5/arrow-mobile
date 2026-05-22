package com.clicky.windows_agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64 as KotlinBase64
import kotlin.io.encoding.ExperimentalEncodingApi

data class AgentStatus(
    val status: String,
    val hostname: String,
    val ip: String,
    val port: Int,
    val executorAvailable: Boolean,
    val pendingTasks: Int,
    val timestamp: String
)

data class ExecutionResult(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val screenshotB64: String? = null,
    val x: Int? = null,
    val y: Int? = null
)

data class ScheduledTask(
    val taskId: String,
    val name: String,
    val description: String,
    val triggerTime: String,
    val recurring: Boolean,
    val intervalHours: Int = 24
)

data class ScheduleResult(
    val success: Boolean,
    val taskId: String? = null,
    val name: String? = null,
    val triggerTime: String? = null,
    val recurring: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class SkillInfo(
    val name: String,
    val trigger: String,
    val description: String
)

class WindowsAgentClient(
    private var baseUrl: String,
    private var authToken: String = ""
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun updateBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun updateToken(token: String) {
        authToken = token
    }

    private fun buildRequest(path: String, body: String? = null): Request {
        val url = "$baseUrl$path"
        val builder = Request.Builder().url(url)
        if (authToken.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $authToken")
        }
        if (body != null) {
            builder.post(body.toRequestBody(jsonMediaType))
        } else {
            builder.get()
        }
        return builder.build()
    }

    @kotlin.jvm.JvmOverloads
    suspend fun getStatus(): Result<AgentStatus> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/status")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            Result.success(AgentStatus(
                status = json.getString("status"),
                hostname = json.optString("hostname", ""),
                ip = json.optString("ip", ""),
                port = json.optInt("port", 18765),
                executorAvailable = json.optBoolean("executor_available", false),
                pendingTasks = json.optInt("pending_tasks", 0),
                timestamp = json.optString("timestamp", "")
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun execute(command: String): Result<ExecutionResult> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """{"command": ${org.json.JSONObject.quote(command)}}"""
            val request = buildRequest("/execute", jsonBody)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            Result.success(ExecutionResult(
                success = json.optBoolean("success", false),
                message = json.optString("message", null),
                error = json.optString("error", null),
                screenshotB64 = json.optString("screenshot_b64", null),
                x = if (json.has("x")) json.getInt("x") else null,
                y = if (json.has("y")) json.getInt("y") else null
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun captureScreenshot(quality: Int = 80): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/screenshot?quality=$quality")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            val b64 = json.optString("screenshot_b64", null)
                ?: return@withContext Result.failure(Exception("No screenshot in response"))
            val bytes = KotlinBase64.decode(b64)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext Result.failure(Exception("Failed to decode screenshot"))
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMemory(limit: Int = 50): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/memory?limit=$limit")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(json.optString("error", "Unknown error")))
            }
            Result.success(json.optString("memory", ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scheduleTask(
        name: String,
        command: String,
        triggerAt: String? = null,
        recurring: Boolean = false,
        intervalHours: Int = 24,
        description: String = ""
    ): Result<ScheduleResult> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = org.json.JSONObject().apply {
                put("name", name)
                put("command", command)
                if (triggerAt != null) put("trigger_at", triggerAt)
                put("recurring", recurring)
                put("interval_hours", intervalHours)
                put("description", description)
            }
            val request = buildRequest("/schedule", jsonBody.toString())
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            if (json.optBoolean("success", false)) {
                Result.success(ScheduleResult(
                    success = true,
                    taskId = json.optString("task_id", null),
                    name = json.optString("name", name),
                    triggerTime = json.optString("trigger_time", null),
                    recurring = json.optBoolean("recurring", recurring),
                    message = json.optString("message", null)
                ))
            } else {
                Result.success(ScheduleResult(
                    success = false,
                    error = json.optString("error", "Unknown error")
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listScheduledTasks(): Result<List<ScheduledTask>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/scheduled")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            val tasksArray = json.optJSONArray("tasks") ?: return@withContext Result.success(emptyList())
            val tasks = mutableListOf<ScheduledTask>()
            for (i in 0 until tasksArray.length()) {
                val t = tasksArray.getJSONObject(i)
                tasks.add(ScheduledTask(
                    taskId = t.optString("task_id", ""),
                    name = t.optString("name", ""),
                    description = t.optString("description", ""),
                    triggerTime = t.optString("trigger_time", ""),
                    recurring = t.optBoolean("recurring", false),
                    intervalHours = t.optInt("interval_hours", 24)
                ))
            }
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteScheduledTask(taskId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/scheduled/$taskId")
            val requestDelete = Request.Builder()
                .url("$baseUrl/scheduled/$taskId")
                .delete()
                .apply { if (authToken.isNotBlank()) addHeader("Authorization", "Bearer $authToken") }
                .build()
            val response = client.newCall(requestDelete).execute()
            val respBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }
            val json = org.json.JSONObject(respBody)
            Result.success(json.optBoolean("success", false))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSkills(): Result<List<SkillInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/skills")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            val skillsArray = json.optJSONArray("skills") ?: return@withContext Result.success(emptyList())
            val skills = mutableListOf<SkillInfo>()
            for (i in 0 until skillsArray.length()) {
                val s = skillsArray.getJSONObject(i)
                skills.add(SkillInfo(
                    name = s.optString("name", ""),
                    trigger = s.optString("trigger", ""),
                    description = s.optString("description", "")
                ))
            }
            Result.success(skills)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class DiscoveryInfo(
        val service: String,
        val hostname: String,
        val ip: String,
        val port: Int,
        val portOpen: Boolean
    )

    suspend fun discover(): Result<DiscoveryInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/mDNS")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }
            val json = org.json.JSONObject(body)
            Result.success(DiscoveryInfo(
                service = json.optString("service", "clicky-agent"),
                hostname = json.optString("hostname", ""),
                ip = json.optString("ip", ""),
                port = json.optInt("port", 18765),
                portOpen = json.optBoolean("port_open", false)
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DEFAULT_PORT = 18765
    }
}