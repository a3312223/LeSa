package com.leshang.lesa.about

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.leshang.lesa.R

/** 交流群二维码(随 App 打包,换码只换 res/drawable-nodpi/wechat_qr.jpg)。LeSa 暂未开放交流群,入口已从关于页去掉,这里是空白占位。 */
object QrImage {
    val RES = R.drawable.wechat_qr
    private const val FILE_NAME = "LeSa-author-wechat.jpg"

    /**
     * 存进相册的 Pictures/LeSa。经 MediaStore 写自己新建的图片不要存储权限(安卓 10 起如此,LeSa 最低支持安卓 11)。
     * 写完前标成待定,别的 App 看不到半张图;失败时删掉那一条。
     */
    fun saveToPictures(context: Context): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LeSa")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return false
        return runCatching {
            resolver.openOutputStream(uri)!!.use { out -> context.resources.openRawResource(RES).use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
