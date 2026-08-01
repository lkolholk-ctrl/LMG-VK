package com.lmg.vk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.VkLoginResult
import com.lmg.vk.ui.theme.LiquidTheme
import kotlinx.coroutines.launch

private val AuthBlue = Color(0xFF2688EB)
private val AuthError = Color(0xFFFF453A)

private enum class AuthStep { Credentials, TwoFactor, Captcha }

/**
 * Рабочий VK OAuth password-флоу. Пароль живёт только в состоянии Compose до
 * закрытия листа; в [MusicAuth] сохраняется исключительно полученная сессия.
 */
@Composable
fun EmailAuthSheet(
    onSuccess: () -> Unit,
    onClose: () -> Unit,
) {
    val lc = LiquidTheme.colors
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(AuthStep.Credentials) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var validationSid by remember { mutableStateOf<String?>(null) }
    var captchaSid by remember { mutableStateOf<String?>(null) }
    var captchaUrl by remember { mutableStateOf("") }
    var captchaKey by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var expectedCodeLength by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val canSubmit = !isLoading && when (step) {
        AuthStep.Credentials -> login.isNotBlank() && password.isNotBlank()
        AuthStep.TwoFactor -> verificationCode.isNotBlank()
        AuthStep.Captcha -> captchaKey.isNotBlank()
    }

    val submit = {
        if (canSubmit) {
            isLoading = true
            error = null
            scope.launch {
                when (val result = MusicAuth.signIn(
                    username = login,
                    password = password,
                    validationSid = validationSid,
                    code = verificationCode.takeIf(String::isNotBlank),
                    captchaSid = captchaSid,
                    captchaKey = captchaKey.takeIf(String::isNotBlank),
                )) {
                    VkLoginResult.Success -> {
                        // Не держим пароль в UI дольше завершённого запроса.
                        password = ""
                        onSuccess()
                    }
                    is VkLoginResult.TwoFactor -> {
                        validationSid = result.validationSid
                        destination = result.destination
                        expectedCodeLength = result.codeLength
                        verificationCode = ""
                        captchaSid = null
                        captchaKey = ""
                        step = AuthStep.TwoFactor
                    }
                    is VkLoginResult.Captcha -> {
                        captchaSid = result.captchaSid
                        captchaUrl = result.imageUrl
                        captchaKey = ""
                        step = AuthStep.Captcha
                    }
                    is VkLoginResult.Failure -> error = result.message
                }
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (step) {
                AuthStep.Credentials -> "Sign in with VK"
                AuthStep.TwoFactor -> "Verification code"
                AuthStep.Captcha -> "Security check"
            },
            color = lc.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (step) {
                AuthStep.Credentials -> "Use the phone number or email and password from your VK account."
                AuthStep.TwoFactor -> buildString {
                    append("Enter the code sent to ")
                    append(destination.ifBlank { "your VK account" })
                    if (expectedCodeLength > 0) append(" ($expectedCodeLength characters)")
                    append('.')
                }
                AuthStep.Captcha -> "Enter the characters shown in the image."
            },
            color = lc.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))

        when (step) {
            AuthStep.Credentials -> {
                AuthTextField(
                    value = login,
                    onValueChange = { login = it; error = null },
                    label = "Phone or email",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(10.dp))
                AuthTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    password = true,
                )
            }
            AuthStep.TwoFactor -> AuthTextField(
                value = verificationCode,
                onValueChange = { value ->
                    verificationCode = if (expectedCodeLength > 0) {
                        value.take(expectedCodeLength)
                    } else value
                    error = null
                },
                label = "Code",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            )
            AuthStep.Captcha -> {
                if (captchaUrl.isNotBlank()) {
                    AsyncImage(
                        model = captchaUrl,
                        contentDescription = "VK captcha",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(lc.cardSurface),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                AuthTextField(
                    value = captchaKey,
                    onValueChange = { captchaKey = it; error = null },
                    label = "Characters from image",
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                color = AuthError,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .alpha(if (canSubmit) 1f else 0.45f)
                .background(AuthBlue, RoundedCornerShape(percent = 50))
                .clickable(
                    enabled = canSubmit,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { submit() },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = when (step) {
                        AuthStep.Credentials -> "Sign in"
                        AuthStep.TwoFactor -> "Verify"
                        AuthStep.Captcha -> "Continue"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (step == AuthStep.Credentials) "Cancel" else "Back",
            color = lc.textTertiary,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable(
                    enabled = !isLoading,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (step == AuthStep.Credentials) {
                        password = ""
                        onClose()
                    } else {
                        step = AuthStep.Credentials
                        verificationCode = ""
                        validationSid = null
                        captchaSid = null
                        captchaKey = ""
                        error = null
                    }
                }
                .padding(8.dp),
        )
        Text(
            text = "Your password is sent only to VK over HTTPS and is not stored by LMG VK.",
            color = lc.textTertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    password: Boolean = false,
) {
    val lc = LiquidTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = lc.textPrimary,
            unfocusedTextColor = lc.textPrimary,
            focusedContainerColor = lc.cardSurface,
            unfocusedContainerColor = lc.cardSurface,
            focusedBorderColor = AuthBlue,
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = AuthBlue,
            unfocusedLabelColor = lc.textSecondary,
            cursorColor = AuthBlue,
        ),
    )
}
