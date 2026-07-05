package com.lifeos.core.ai.mcp

import com.lifeos.core.common.result.LifeError
import com.lifeos.core.common.result.LifeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal MCP client over the streamable-HTTP transport (§8.5): JSON-RPC 2.0
 * POSTed to a single endpoint (the NAS-side wrapper around the stdio mail
 * MCP). Handles both plain-JSON and SSE-framed responses. LAN/VPN only —
 * the endpoint is user-configured, never a third-party cloud.
 */
@Singleton
class McpClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val nextId = AtomicLong(1)

    /** Handshake; returns the server's advertised name. */
    suspend fun initialize(endpoint: String): LifeResult<String> {
        val result = rpc(endpoint, "initialize") {
            put("protocolVersion", "2024-11-05")
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "lifeos")
                put("version", "0.1")
            }
        }
        return when (result) {
            is LifeResult.Success -> LifeResult.Success(
                result.value.jsonObject["serverInfo"]
                    ?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "unknown server",
            )
            is LifeResult.Failure -> result
        }
    }

    /** Lists tool names exposed by the server. */
    suspend fun listTools(endpoint: String): LifeResult<List<String>> {
        val result = rpc(endpoint, "tools/list") {}
        return when (result) {
            is LifeResult.Success -> LifeResult.Success(
                (result.value.jsonObject["tools"] as? JsonArray ?: JsonArray(emptyList()))
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content },
            )
            is LifeResult.Failure -> result
        }
    }

    /** Calls a tool; returns the concatenated text content of the result. */
    suspend fun callTool(endpoint: String, tool: String, args: JsonObject): LifeResult<String> {
        val result = rpc(endpoint, "tools/call") {
            put("name", tool)
            put("arguments", args)
        }
        return when (result) {
            is LifeResult.Success -> LifeResult.Success(
                (result.value.jsonObject["content"] as? JsonArray ?: JsonArray(emptyList()))
                    .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    .joinToString("\n"),
            )
            is LifeResult.Failure -> result
        }
    }

    private suspend fun rpc(
        endpoint: String,
        method: String,
        params: JsonObjectBuilder.() -> Unit,
    ): LifeResult<JsonElement> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextId.getAndIncrement())
                put("method", method)
                putJsonObject("params", params)
            }
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json, text/event-stream")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LifeResult.Failure(
                        LifeError.Network("MCP endpoint returned HTTP ${response.code}"),
                    )
                }
                val parsed = json.parseToJsonElement(extractJson(response.body.string())).jsonObject
                parsed["error"]?.let { error ->
                    val message = error.jsonObject["message"]?.jsonPrimitive?.content ?: "MCP error"
                    return@withContext LifeResult.Failure(LifeError.Network(message))
                }
                parsed["result"]
                    ?.let { LifeResult.Success(it) }
                    ?: LifeResult.Failure(LifeError.Network("MCP response has no result"))
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LifeResult.Failure(LifeError.Network("MCP call failed: ${t.message}", t))
        }
    }

    /** SSE responses frame the JSON as `data:` lines; plain JSON passes through. */
    private fun extractJson(body: String): String {
        val trimmed = body.trimStart()
        if (!trimmed.startsWith("event:") && !trimmed.startsWith("data:")) return body
        return body.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .lastOrNull { it.isNotEmpty() } ?: body
    }
}
