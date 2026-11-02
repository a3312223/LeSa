package com.leshang.lesa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.leshang.lesa.R
import com.leshang.lesa.i18n.tr
import com.leshang.lesa.settings.CaptchaKeyStore
import com.leshang.lesa.ui.theme.LeSaTheme
import com.leshang.lesa.ui.theme.LeSaType

/** 设置 › 验证码识别 的文案。密钥只露最后 4 位(这一行随时看得到)。 */
object CaptchaCopy {
    val TITLE: String get() = tr(R.string.captcha_title)
    val ROW_SUB: String get() = tr(R.string.captcha_row_sub)

    /** 设置首页那一行右边:没填写「未设置」,填了写「已开启 ••••1234」。 */
    fun value(key: String): String =
        if (key.isBlank()) tr(R.string.settings_not_set) else tr(R.string.captcha_on) + " " + CaptchaKeyStore.masked(key)
}

/** 改验证码识别的密钥:一个输入框(按密码显示),空着表示关掉。 */
@Composable
fun CaptchaKeyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val uri = LocalUriHandler.current
    val c = LeSaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CaptchaCopy.TITLE) },
        text = {
            Column {
                Text(stringResource(R.string.captcha_hint), style = LeSaType.Caption, color = c.ink3)
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("captcha-key-input"),
                    singleLine = true, label = { Text(stringResource(R.string.captcha_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    stringResource(R.string.captcha_get_key),
                    Modifier.padding(top = 8.dp).clickable { uri.openUri(CaptchaKeyStore.KEYS_URL) },
                    style = LeSaType.Caption, color = c.accent,
                )
                Text(stringResource(R.string.captcha_risk), Modifier.padding(top = 8.dp), style = LeSaType.Hint, color = c.ink3)
            }
        },
        confirmButton = { TextButton({ onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
