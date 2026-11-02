package com.leshang.lesa.core.captcha

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * 真连 EveryInfra 试一次(会真扣费,每次约 ¥0.02):只在设了环境变量 EVERYINFRA_API_KEY 时跑,平时跳过。
 * 跑法:EVERYINFRA_API_KEY=... ./gradlew :core:test --tests "*EveryInfraLiveTest"
 */
class EveryInfraLiveTest {
    private val key = System.getenv("EVERYINFRA_API_KEY").orEmpty()
    private val solver = EveryInfraSolver({ key }, http = OkHttpClient.Builder().readTimeout(150, TimeUnit.SECONDS).build())

    @Test fun textCaptcha() = runBlocking {
        assumeTrue(key.isNotBlank())
        val img = BufferedImage(200, 70, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = Color(235, 240, 245); fillRect(0, 0, 200, 70)
            color = Color(160, 170, 190); stroke = BasicStroke(2f); drawLine(5, 50, 195, 20); drawLine(10, 15, 190, 60)
            color = Color(30, 60, 120); font = Font("SansSerif", Font.BOLD, 44); drawString("7XK2", 35, 52)
            dispose()
        }
        val r = solver.solve("image", buildJsonObject { put("body", b64(img)) })
        println("LIVE image -> token=${r.token} solution=${r.solution} cny=${r.cny} id=${r.requestId}")
    }

    @Test fun clickCaptcha() = runBlocking {
        assumeTrue(key.isNotBlank())
        val img = BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color.WHITE; fillRect(0, 0, 300, 200)
            color = Color(40, 120, 220); fillRect(30, 40, 50, 50)
            color = Color(220, 40, 40); fillOval(200, 110, 60, 60)
            color = Color(40, 170, 80); fillRect(120, 120, 40, 40)
            dispose()
        }
        val r = solver.solve("coordinates", buildJsonObject { put("body", b64(img)); put("comment", "点击红色的圆") })
        println("LIVE coordinates -> solution=${r.solution} cny=${r.cny} id=${r.requestId} (红圆中心约在 230,140)")
    }

    private fun b64(img: BufferedImage): String =
        Base64.getEncoder().encodeToString(ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray())
}
