package com.leshang.lesa.core.captcha

import com.leshang.lesa.core.model.HttpSupport
import com.leshang.lesa.core.model.ModelError
import com.leshang.lesa.core.net.NetProxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 识别验证码的服务:设置里填了 EveryInfra 的密钥才有;没有时 solve_captcha 不发给模型。 */
interface CaptchaSolver {
    /** 交一道题([type] 与参数按 EveryInfra 的目录原样),拿回答案;失败抛 [CaptchaError]。 */
    suspend fun solve(type: String, params: JsonObject): CaptchaAnswer
}

/** 一次识别的结果:[solution] 的形状按题型(文字、坐标、角度、距离);[cny] 是这次实际扣的钱,失败不扣。 */
data class CaptchaAnswer(val solution: JsonObject, val token: String?, val requestId: String?, val cny: Double?)

/** 识别失败:[code] 是服务返回的错误码(或 network / bad_response),[requestId] 报问题时用。 */
class CaptchaError(val code: String, message: String, val requestId: String? = null, val status: Int = 0) : Exception(message)

/**
 * EveryInfra 万有引擎的验证码识别(EverySolve):POST /api/v1/captcha,请求体 {type, ...参数},同步返回。
 * 只有识别成功才扣费,失败自动退回。密钥只放在请求头里,不写进日志、结果和错误信息。
 * 结果未知(断网、超时)时不自动重发,免得重复扣费。
 */
class EveryInfraSolver(
    private val apiKey: () -> String,
    private val baseUrl: String = BASE_URL,
    private val http: OkHttpClient = NetProxy.shared.client(
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(150, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS),
    ),
) : CaptchaSolver {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun solve(type: String, params: JsonObject): CaptchaAnswer {
        val key = apiKey().trim()
        if (key.isEmpty()) throw CaptchaError("missing_key", "没有设置 EveryInfra 的 API 密钥")
        val body = buildJsonObject {
            put("type", type)
            params.forEach { (k, v) -> put(k, v) }
        }
        val req = Request.Builder().url("$baseUrl/api/v1/captcha")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val raw = try {
            HttpSupport.execute(http, req)
        } catch (e: ModelError) {
            throw CaptchaError("network", "连不上 EveryInfra(${e.message ?: e::class.simpleName}),结果未知,没有重发")
        }
        val o = runCatching { json.parseToJsonElement(raw.body).jsonObject }.getOrNull()
        if (raw.status !in 200..299) throw errorOf(raw.status, raw.retryAfter, o)
        o ?: throw CaptchaError("bad_response", "EveryInfra 返回的不是 JSON", status = raw.status)
        val solution = (o["solution"] as? JsonObject) ?: JsonObject(emptyMap())
        val billing = o["billing"] as? JsonObject
        return CaptchaAnswer(
            solution = solution,
            token = (o["token"] as? JsonPrimitive)?.contentOrNull,
            requestId = (o["id"] as? JsonPrimitive)?.contentOrNull,
            cny = (billing?.get("cny") as? JsonPrimitive)?.doubleOrNull,
        )
    }

    private fun errorOf(status: Int, retryAfter: String?, o: JsonObject?): CaptchaError {
        val e = o?.get("error") as? JsonObject
        val code = (e?.get("code") as? JsonPrimitive)?.contentOrNull ?: "http_$status"
        val msg = (e?.get("message") as? JsonPrimitive)?.contentOrNull ?: "HTTP $status"
        val id = (e?.get("request_id") as? JsonPrimitive)?.contentOrNull
        val hint = when (status) {
            401 -> "API 密钥不对或已失效,请到设置里重新填"
            402 -> "EveryInfra 余额不足,请先充值"
            429, 503 -> "EveryInfra 繁忙" + (retryAfter?.let { ",${it} 秒后再试" } ?: ",稍后再试")
            else -> msg
        }
        return CaptchaError(code, hint, id, status)
    }

    companion object {
        const val BASE_URL = "https://api.everyinfra.com"
        private val JSON = "application/json".toMediaType()
    }
}
