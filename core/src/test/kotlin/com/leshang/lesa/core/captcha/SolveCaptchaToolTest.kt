package com.leshang.lesa.core.captcha

import com.leshang.lesa.core.assembly.toolDispatcher
import com.leshang.lesa.core.model.Part
import com.leshang.lesa.core.tool.ActionPass
import com.leshang.lesa.core.tool.FakeScreen
import com.leshang.lesa.core.vision.JvmTestImageOps
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 接了验证码识别时 solve_captcha 发给模型、答案换回整屏坐标;没先试过时验证码不能直接交给用户。 */
class SolveCaptchaToolTest {
    /** 记下收到的题,点选题回小图正中那一点。 */
    private class FakeSolver : CaptchaSolver {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        override suspend fun solve(type: String, params: JsonObject): CaptchaAnswer {
            calls += type to params
            val img = JvmTestImageOps.decode(java.util.Base64.getDecoder().decode(params["body"]!!.jsonPrimitive.content))
            return CaptchaAnswer(buildJsonObject {
                put("coordinates", buildJsonArray { add(buildJsonObject { put("x", img.w / 2); put("y", img.h / 2) }) })
            }, null, "req_t", 0.0117)
        }
    }

    private fun screen() = FakeScreen().apply {
        png = JvmTestImageOps.encode(500, 1000) { _, _ -> 128 }
    }

    @Test fun offeredOnlyWithASolver() {
        assertFalse("solve_captcha" in toolDispatcher(FakeScreen()).definitions().map { it.name })
        val names = toolDispatcher(FakeScreen(), imageOps = JvmTestImageOps, captcha = FakeSolver()).definitions().map { it.name }
        assertTrue(names.toString(), "solve_captcha" in names)
    }

    @Test fun clickPointsComeBackOnTheWholeScreen() = runTest {
        val solver = FakeSolver()
        val d = toolDispatcher(screen(), imageOps = JvmTestImageOps, captcha = solver)
        val r = d.execute(Part.ToolCall("c", "solve_captcha",
            """{"kind":"click","left":100,"top":200,"right":600,"bottom":500,"instruction":"依次点击「春」"}"""), ActionPass()).content
        assertTrue(r.toString(), solver.calls.size == 1)
        assertEquals(r.toString(), "coordinates", solver.calls.single().first)
        assertEquals("依次点击「春」", solver.calls.single().second["comment"]!!.jsonPrimitive.content)
        val p = (r["points"] as JsonArray).single().jsonArray
        assertTrue(r.toString(), abs(p[0].jsonPrimitive.int - 350) <= 2 && abs(p[1].jsonPrimitive.int - 350) <= 2)
    }

    @Test fun captchaIsNotHandedOverBeforeTryingTheSolver() = runTest {
        val d = toolDispatcher(screen(), imageOps = JvmTestImageOps, captcha = FakeSolver())
        val hand = Part.ToolCall("h", "hand_to_user", """{"reason":"captcha","message":"请过一下验证"}""")
        val first = d.execute(hand, ActionPass()).content.toString()
        assertTrue(first, first.contains("try_solve_captcha"))
        d.execute(Part.ToolCall("c", "solve_captcha", """{"kind":"text","left":100,"top":200,"right":600,"bottom":500}"""), ActionPass())
        val after = d.execute(hand, ActionPass()).content.toString()
        assertTrue(after, after.contains("handedOver"))
    }

    @Test fun withoutASolverCaptchaIsHandedOverAsBefore() = runTest {
        val r = toolDispatcher(screen()).execute(Part.ToolCall("h", "hand_to_user", """{"reason":"captcha","message":"请过一下验证"}"""), ActionPass()).content.toString()
        assertTrue(r, r.contains("handedOver"))
    }
}
