package com.leshang.lesa.core.captcha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class EveryInfraSolverTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun solver(key: String = "ei_secret_1234") =
        EveryInfraSolver({ key }, server.url("").toString().trimEnd('/'), OkHttpClient())

    @Test fun sendsTypeAndParamsWithBearerKey() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"req_1","type":"image","token":"7xK2","solution":{"text":"7xK2"},"billing":{"charged":true,"credits":60,"cny":0.006}}"""))
        val r = solver().solve("image", buildJsonObject { put("body", "aGVsbG8=") })
        val req = server.takeRequest()
        assertEquals("/api/v1/captcha", req.path)
        assertEquals("Bearer ei_secret_1234", req.getHeader("Authorization"))
        val sent = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("image", sent["type"]!!.jsonPrimitive.content)
        assertEquals("aGVsbG8=", sent["body"]!!.jsonPrimitive.content)
        assertEquals("7xK2", r.token)
        assertEquals("req_1", r.requestId)
        assertEquals(0.006, r.cny!!, 1e-9)
    }

    @Test fun coordinatesComeBackInSolution() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"req_2","type":"coordinates","solution":{"coordinates":[{"x":10,"y":20},{"x":30,"y":40}]}}"""))
        val r = solver().solve("coordinates", buildJsonObject { put("body", "x") })
        assertEquals(null, r.token)
        assertTrue(r.solution.containsKey("coordinates"))
    }

    @Test fun wrongKeyIsReportedWithoutLeakingIt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_key","message":"bad key","request_id":"req_3"}}"""))
        try {
            solver().solve("image", buildJsonObject { put("body", "x") })
            fail("expected CaptchaError")
        } catch (e: CaptchaError) {
            assertEquals("invalid_key", e.code)
            assertEquals("req_3", e.requestId)
            assertFalse(e.message!!.contains("ei_secret_1234"))
        }
    }

    @Test fun busyCarriesRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("""{"error":{"code":"rate_limited","message":"slow down"}}"""))
        try {
            solver().solve("image", buildJsonObject { put("body", "x") })
            fail("expected CaptchaError")
        } catch (e: CaptchaError) {
            assertEquals(429, e.status)
            assertTrue(e.message!!.contains("12"))
        }
    }

    @Test fun emptyKeyNeverCallsTheService() = runBlocking {
        try {
            solver(key = "  ").solve("image", buildJsonObject { put("body", "x") })
            fail("expected CaptchaError")
        } catch (e: CaptchaError) {
            assertEquals("missing_key", e.code)
        }
        assertEquals(0, server.requestCount)
    }
}
