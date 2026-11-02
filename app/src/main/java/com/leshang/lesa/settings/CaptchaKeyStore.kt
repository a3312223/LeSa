package com.leshang.lesa.settings

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 设置 › 验证码识别:EveryInfra 的 API 密钥;空串表示不用,验证码照旧交给用户。
 * 和服务商的 Key 一样经 [KeyCipher] 加密后单独存一个文件,不进明文的偏好设置,也不写进日志。
 */
class CaptchaKeyStore(private val dir: File, private val cipher: KeyCipher) {
    private val file = File(dir, FILE)
    @Volatile private var cached: String? = null

    /** 存着的密钥;没有、或解不开(换过机、密钥没了)时为空串。 */
    @Synchronized fun get(): String {
        cached?.let { return it }
        val stored = runCatching { if (file.isFile) String(cipher.decrypt(file.readBytes()), Charsets.UTF_8) else "" }.getOrDefault("")
        return stored.also { cached = it }
    }

    /** 存下 [key](去掉两头空白);空的就删掉文件。 */
    @Synchronized fun set(key: String) {
        val v = key.trim()
        if (v.isEmpty()) file.delete() else {
            dir.mkdirs()
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeBytes(cipher.encrypt(v.toByteArray(Charsets.UTF_8)))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        cached = v
    }

    companion object {
        private const val FILE = "captcha-key.bin"
        private const val KEY_ALIAS = "lesa-captcha-key"
        /** 到这里建密钥。 */
        const val KEYS_URL = "https://everyinfra.com/app#keys"

        /** 给人看的写法:只露最后 4 位。 */
        fun masked(key: String): String = if (key.length <= 4) "••••" else "••••" + key.takeLast(4)

        @Volatile private var instance: CaptchaKeyStore? = null

        fun get(context: Context): CaptchaKeyStore = instance ?: synchronized(this) {
            instance ?: CaptchaKeyStore(File(context.applicationContext.filesDir, "settings"), KeystoreKeyCipher(KEY_ALIAS)).also { instance = it }
        }
    }
}
