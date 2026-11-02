package com.leshang.lesa.core.general

import com.leshang.lesa.core.capability.CapabilityKey
import com.leshang.lesa.core.captcha.CaptchaAnswer
import com.leshang.lesa.core.captcha.CaptchaError
import com.leshang.lesa.core.prompt.Feedback
import com.leshang.lesa.core.prompt.Prompts
import com.leshang.lesa.core.tool.ArgError
import com.leshang.lesa.core.tool.Args
import com.leshang.lesa.core.tool.COORD
import com.leshang.lesa.core.tool.HINT_PROTECTED
import com.leshang.lesa.core.tool.Risk
import com.leshang.lesa.core.tool.ToolContext
import com.leshang.lesa.core.tool.ToolDef
import com.leshang.lesa.core.tool.ToolExecution
import com.leshang.lesa.core.tool.Verb
import com.leshang.lesa.core.tool.bool
import com.leshang.lesa.core.tool.fail
import com.leshang.lesa.core.tool.int
import com.leshang.lesa.core.tool.ok
import com.leshang.lesa.core.tool.str
import com.leshang.lesa.core.uistate.screen
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.math.roundToInt

/**
 * 把屏幕上的验证码交给设置里的识别服务(EveryInfra)解,拿回该填的字、该点的点、该转的角度、该拖的距离;
 * 本工具只读屏不动界面,填、点、拖由模型接着用屏幕工具做。只有识别成功才扣费。
 * 一个任务最多解 [MAX_PER_TASK] 次,用完就只能交给用户。
 */
internal object SolveCaptcha : SurfaceAware {
    override val verb = Verb.SolveCaptcha
    override val label = "识别验证码"
    override val risk = Risk.Read
    override val withScreens = true
    private val KINDS = listOf("text", "click", "rotate", "slider", "question")
    override val def = ToolDef("solve_captcha",
        Prompts.tool("solve_captcha"),
        mapOf(
            "kind" to str("text = letters/digits/math in a picture to type in; click = tap things in the picture in order (点选); rotate = turn a picture upright; slider = drag a piece into a gap; question = a text-only question to answer.", KINDS),
            "left" to int("Left edge of the captcha picture. $COORD", 0, 999),
            "top" to int("Top edge of the captcha picture. $COORD", 0, 999),
            "right" to int("Right edge of the captcha picture. $COORD", 0, 999),
            "bottom" to int("Bottom edge of the captcha picture. $COORD", 0, 999),
            "instruction" to str("For click: the instruction shown on screen, copied exactly (e.g. 请依次点击「春」「风」). For question: the question text, copied exactly."),
            "math" to bool("For text: the picture is a sum to calculate, not text to copy."),
            "digits" to bool("For text: the answer is digits only."),
            "piece_left" to int("For slider: left edge of the small puzzle piece. $COORD", 0, 999),
            "piece_top" to int("For slider: top edge of the piece. $COORD", 0, 999),
            "piece_right" to int("For slider: right edge of the piece. $COORD", 0, 999),
            "piece_bottom" to int("For slider: bottom edge of the piece. $COORD", 0, 999),
        ),
        listOf("kind"))

    const val MAX_PER_TASK = 5
    /** EveryInfra 的图形题限 500KB;超了先压成 JPEG。 */
    private const val MAX_IMAGE_BYTES = 480_000

    /** 本任务已经调了几次识别(不论成败)。 */
    private class Uses { var n = 0 }
    private val USES = CapabilityKey<Uses>("captcha_uses")

    override fun available(ctx: ToolContext) = ctx.config.captcha != null

    /** 这个任务里调过识别没有(不论成败);交给用户前据此判断先试过没有。 */
    fun tried(ctx: ToolContext): Boolean = (ctx[USES]?.n ?: 0) > 0

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        checkSurface(a, ctx)
        val kind = a.str("kind")!!
        if (kind !in KINDS) throw ArgError("kind", "must be one of ${KINDS.joinToString("|")}")
        val solver = ctx.config.captcha!!
        val uses = ctx[USES] ?: Uses().also { ctx[USES] = it }
        if (uses.n >= MAX_PER_TASK) return fail("captcha_limit", Feedback.format("captcha.limit", "max" to MAX_PER_TASK))

        val request: Pair<String, JsonObject>
        var box: IntArray? = null
        var sent = 0 to 0
        if (kind == "question") {
            val q = a.str("instruction", max = 500) ?: throw ArgError("instruction", "required for question: copy the question text")
            request = "text_captcha" to buildJsonObject { put("question", q) }
        } else {
            box = a.box("left", "top", "right", "bottom")
            val png = shot(ctx) ?: return protectedOrMissing(ctx)
            val ops = ctx.config.imageOps ?: return fail("captcha_unavailable", Feedback["captcha.no_image_ops"])
            val (img, size) = encode(ops, ops.crop(png, box))
            sent = size
            request = when (kind) {
                "text" -> "image" to buildJsonObject {
                    put("body", img)
                    if (a.bool("math", false)) put("math", true)
                    if (a.bool("digits", false)) put("charset", "digits")
                }
                "click" -> "coordinates" to buildJsonObject {
                    put("body", img)
                    a.str("instruction", max = 300)?.let { put("comment", it) }
                }
                "rotate" -> "rotate" to buildJsonObject { put("body", img) }
                else -> {
                    val piece = a.box("piece_left", "piece_top", "piece_right", "piece_bottom")
                    "slider" to buildJsonObject { put("body", img); put("piece", encode(ops, ops.crop(png, piece)).first) }
                }
            }
        }

        uses.n++
        val answer = try {
            solver.solve(request.first, request.second)
        } catch (e: CancellationException) {
            throw e
        } catch (e: CaptchaError) {
            return ToolExecution(buildJsonObject {
                put("ok", false); put("error", "captcha_failed"); put("code", e.code)
                put("message", Feedback.format("captcha.failed", "error" to e.message))
                e.requestId?.let { put("requestId", it) }
                put("recoverable", true); put("triesLeft", MAX_PER_TASK - uses.n)
            })
        }
        return ok {
            put("kind", kind)
            fill(this, kind, answer, box, sent)
            answer.cny?.let { put("costCny", it) }
            answer.requestId?.let { put("requestId", it) }
            put("triesLeft", MAX_PER_TASK - uses.n)
            put("hint", next(kind))
        }
    }

    /** 拿到答案后下一步怎么做。 */
    private fun next(kind: String): String = when (kind) {
        "text" -> Feedback["captcha.next_text"]
        "question" -> Feedback["captcha.next_question"]
        "click" -> Feedback["captcha.next_click"]
        "rotate" -> Feedback["captcha.next_rotate"]
        else -> Feedback["captcha.next_slider"]
    }

    /** 把服务的答案换成模型能直接用的:文字原样;点、距离从小图的像素换回整屏 0-999。 */
    private fun fill(b: kotlinx.serialization.json.JsonObjectBuilder, kind: String, r: CaptchaAnswer, box: IntArray?, sent: Pair<Int, Int>) {
        val s = r.solution
        when (kind) {
            "text", "question" -> b.put("answer", r.token ?: s.str("text").orEmpty())
            "rotate" -> b.put("clockwiseDegrees", s.num("angle")?.roundToInt() ?: 0)
            "click" -> {
                val bx = box!!
                // 0-999 是按整屏宽、高各自分的,横竖要分开换算
                val sx = (bx[2] - bx[0]).toDouble() / sent.first.coerceAtLeast(1)
                val sy = (bx[3] - bx[1]).toDouble() / sent.second.coerceAtLeast(1)
                val points = (s["coordinates"] as? JsonArray).orEmpty().mapNotNull { p -> point(p) }
                b.put("points", buildJsonArray {
                    points.forEach { (x, y) ->
                        add(buildJsonArray {
                            add(JsonPrimitive((bx[0] + x * sx).roundToInt().coerceIn(0, 999)))
                            add(JsonPrimitive((bx[1] + y * sy).roundToInt().coerceIn(0, 999)))
                        })
                    }
                })
            }
            else -> {
                val bx = box!!
                val px = s.num("distance") ?: 0.0
                b.put("dragRight", (px * (bx[2] - bx[0]) / sent.first.coerceAtLeast(1)).roundToInt())
            }
        }
    }

    private fun point(p: kotlinx.serialization.json.JsonElement): Pair<Double, Double>? = when (p) {
        is JsonObject -> p.num("x")?.let { x -> p.num("y")?.let { y -> x to y } }
        is JsonArray -> if (p.size >= 2) (p[0] as? JsonPrimitive)?.doubleOrNull?.let { x -> (p[1] as? JsonPrimitive)?.doubleOrNull?.let { y -> x to y } } else null
        else -> null
    }

    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

    /** 图片 base64(不换行、不带 data: 前缀)和发出去那张的宽、高。太大时先压成 JPEG。 */
    private fun encode(ops: com.leshang.lesa.core.vision.ImageOps, img: ByteArray): Pair<String, Pair<Int, Int>> {
        val out = if (img.size > MAX_IMAGE_BYTES) ops.toJpeg(img, maxSide = 1280, quality = 85) else img
        return Base64.getEncoder().encodeToString(out) to ops.size(out)
    }

    private fun Args.box(l: String, t: String, r: String, b: String): IntArray {
        val v = listOf(l, t, r, b).map { k -> int(k, 0, 999) ?: throw ArgError(k, "required: where the picture is on screen") }
        if (v[2] - v[0] < 5 || v[3] - v[1] < 5) throw ArgError(r, "the box is too small; give the whole picture")
        return v.toIntArray()
    }

    private suspend fun shot(ctx: ToolContext): ByteArray? {
        val screen = ctx.screen
        if (screen.isProtected()) return null
        return screen.screenshotPng()
    }

    private suspend fun protectedOrMissing(ctx: ToolContext): ToolExecution =
        if (ctx.screen.isProtected()) ToolExecution(buildJsonObject {
            put("ok", false); put("error", "protected_content"); put("protected", true)
            put("message", HINT_PROTECTED); put("recoverable", true)
        }) else fail("screenshot_unavailable", Feedback["screenshot_unavailable"])
}
