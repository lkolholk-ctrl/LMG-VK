package com.lmg.vk.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lmg.vk.engine.backend.MusicAuth
import com.lmg.vk.engine.backend.VkLoginResult
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidSurfaces
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText
import kotlinx.coroutines.launch

private val VkBlue = Color(0xFF0077FF)
private val DestructiveRed = Color(0xFFFC3C44)

private enum class AuthStep { Phone, TwoFactor, Password, Captcha }

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit = {},
    onBack: () -> Unit = {},
    isAddingAccount: Boolean = false,
) {
    val lc = LiquidTheme.colors
    val isDark = lc.isDark
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(Unit) {
        MusicAuth.beginAuthorization()
        onDispose { MusicAuth.endAuthorization() }
    }

    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    LaunchedEffect(isLoggedIn, isAddingAccount) {
        if (isLoggedIn && !isAddingAccount) onAuthSuccess()
    }

    var step by remember { mutableStateOf(AuthStep.Phone) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var validationSid by remember { mutableStateOf<String?>(null) }
    var captchaSid by remember { mutableStateOf<String?>(null) }
    var captchaUrl by remember { mutableStateOf("") }
    var captchaKey by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var expectedCodeLength by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val phoneFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val captchaFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        phoneFocusRequester.requestFocus()
    }

    fun submit() {
        if (isLoading) return
        val currentLogin = login.trim()
        if (currentLogin.isBlank()) {
            error = "Введите номер телефона или логин"
            return
        }

        if (step == AuthStep.TwoFactor && verificationCode.isBlank()) {
            error = "Введите код из сообщения"
            return
        }

        if (step == AuthStep.Password && password.isBlank()) {
            error = "Введите пароль"
            return
        }

        if (step == AuthStep.Captcha && captchaKey.isBlank()) {
            error = "Введите символы с картинки"
            return
        }

        keyboardController?.hide()
        isLoading = true
        error = null
        scope.launch {
            when (val result = MusicAuth.signIn(
                username = currentLogin,
                password = password,
                validationSid = validationSid,
                code = verificationCode.takeIf(String::isNotBlank),
                captchaSid = captchaSid,
                captchaKey = captchaKey.takeIf(String::isNotBlank),
            )) {
                VkLoginResult.Success -> {
                    password = ""
                    onAuthSuccess()
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
                is VkLoginResult.NeedPassword -> {
                    validationSid = result.validationSid
                    password = ""
                    verificationCode = ""
                    captchaSid = null
                    captchaKey = ""
                    step = AuthStep.Password
                }
                is VkLoginResult.Captcha -> {
                    captchaSid = result.captchaSid
                    captchaUrl = result.imageUrl
                    captchaKey = ""
                    verificationCode = ""
                    step = AuthStep.Captcha
                }
                is VkLoginResult.Failure -> {
                    error = result.message
                }
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidSurfaces.sheet(isDark))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(modifier = Modifier.height(14.dp))

            // ─── Top Bar with Glass Back Button ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val buttonBg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                val buttonBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(buttonBg)
                        .border(1.dp, buttonBorder, CircleShape)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressIcon,
                            onClick = {
                                if (step == AuthStep.Phone) {
                                    password = ""
                                    onBack()
                                } else {
                                    MusicAuth.restartAuthorization()
                                    step = AuthStep.Phone
                                    verificationCode = ""
                                    validationSid = null
                                    captchaSid = null
                                    captchaKey = ""
                                    error = null
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = lmgVector(LmgDrawables.ArrowLeftOutline28),
                        contentDescription = "Back",
                        tint = lc.iconDefault,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Step indicator badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = when (step) {
                            AuthStep.Phone -> if (isAddingAccount) "Добавить аккаунт" else "Вход в VK"
                            AuthStep.TwoFactor -> "Код подтверждения"
                            AuthStep.Password -> "Пароль"
                            AuthStep.Captcha -> "Проверка"
                        },
                        fontFamily = VkSansText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = lc.textSecondary,
                    )
                }

                // Balance spacer for centered header layout
                Spacer(modifier = Modifier.size(42.dp))
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── Hero Header & Logo ───
            val heroIconTint = when (step) {
                AuthStep.Phone -> VkBlue
                AuthStep.TwoFactor -> Color(0xFFFF9500)
                AuthStep.Password -> VkBlue
                AuthStep.Captcha -> DestructiveRed
            }
            val heroIcon = when (step) {
                AuthStep.Phone -> lmgVector(LmgDrawables.UserCircleOutline28)
                AuthStep.TwoFactor -> lmgVector(LmgDrawables.PincodeLockOutline28)
                AuthStep.Password -> lmgVector(LmgDrawables.LockOutline28)
                AuthStep.Captcha -> lmgVector(LmgDrawables.CheckShieldOutline28)
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(heroIconTint.copy(alpha = 0.14f))
                    .border(1.dp, heroIconTint.copy(alpha = 0.25f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = heroIcon,
                    contentDescription = null,
                    tint = heroIconTint,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = when (step) {
                    AuthStep.Phone -> if (isAddingAccount) "Добавить аккаунт" else "Войти в VK"
                    AuthStep.TwoFactor -> "Код подтверждения"
                    AuthStep.Password -> "Введите пароль"
                    AuthStep.Captcha -> "Проверка безопасности"
                },
                color = lc.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = VkSansDisplay,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (step) {
                    AuthStep.Phone -> "используя ваш аккаунт VK"
                    AuthStep.TwoFactor -> buildString {
                        append("Введите код подтверждения, отправленный на ")
                        append(destination.ifBlank { "ваш аккаунт VK" })
                        if (expectedCodeLength > 0) append(" ($expectedCodeLength цифр)")
                    }
                    AuthStep.Password -> "Введите пароль от вашей страницы ВКонтакте"
                    AuthStep.Captcha -> "Введите символы с картинки"
                },
                color = lc.textSecondary,
                fontSize = 14.sp,
                fontFamily = VkSansText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ─── Dynamic Glass Card with Step Transitions ───
            val cardBg = LiquidSurfaces.card(isDark)
            val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBg)
                    .border(1.dp, cardBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "authStepTransition",
                ) { currentStep ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (currentStep) {
                            AuthStep.Phone -> {
                                GlassAuthField(
                                    value = login,
                                    onValueChange = { login = it.trim(); error = null },
                                    label = "Телефон или логин",
                                    leadingIcon = lmgVector(LmgDrawables.UserOutline28),
                                    keyboardType = KeyboardType.Phone,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.focusRequester(phoneFocusRequester),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submit() },
                                    ),
                                )
                            }
                            AuthStep.TwoFactor -> {
                                LaunchedEffect(Unit) {
                                    codeFocusRequester.requestFocus()
                                }

                                GlassAuthField(
                                    value = verificationCode,
                                    onValueChange = { input ->
                                        val digits = input.filter(Char::isDigit)
                                        verificationCode = if (expectedCodeLength > 0) {
                                            digits.take(expectedCodeLength)
                                        } else digits
                                        error = null
                                        if (expectedCodeLength > 0 && verificationCode.length == expectedCodeLength) {
                                            submit()
                                        }
                                    },
                                    label = "Код подтверждения",
                                    leadingIcon = lmgVector(LmgDrawables.KeyOutline28),
                                    keyboardType = KeyboardType.NumberPassword,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.focusRequester(codeFocusRequester),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submit() },
                                    ),
                                )
                            }
                            AuthStep.Password -> {
                                LaunchedEffect(Unit) {
                                    passwordFocusRequester.requestFocus()
                                }

                                GlassAuthField(
                                    value = password,
                                    onValueChange = { input ->
                                        password = input.filter { it.code in 32..126 }
                                        error = null
                                    },
                                    label = "Пароль",
                                    leadingIcon = lmgVector(LmgDrawables.LockOutline28),
                                    trailingIcon = lmgVector(
                                        if (passwordVisible) LmgDrawables.HideOutline28 else LmgDrawables.ViewOutline28
                                    ),
                                    onTrailingIconClick = { passwordVisible = !passwordVisible },
                                    keyboardType = KeyboardType.Password,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Done,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.focusRequester(passwordFocusRequester),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submit() },
                                    ),
                                )
                            }
                            AuthStep.Captcha -> {
                                LaunchedEffect(Unit) {
                                    captchaFocusRequester.requestFocus()
                                }

                                if (captchaUrl.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(112.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA))
                                            .border(
                                                1.dp,
                                                if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model = captchaUrl,
                                            contentDescription = "VK Captcha",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp),
                                        )
                                    }
                                }

                                GlassAuthField(
                                    value = captchaKey,
                                    onValueChange = { input ->
                                        captchaKey = input.filter { it.code in 33..126 }
                                        error = null
                                    },
                                    label = "Символы с картинки",
                                    leadingIcon = lmgVector(LmgDrawables.KeySquareOutline28),
                                    keyboardType = KeyboardType.Ascii,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Done,
                                    modifier = Modifier.focusRequester(captchaFocusRequester),
                                    keyboardActions = KeyboardActions(
                                        onDone = { submit() },
                                    ),
                                )
                            }
                        }
                    }
                }

                // ─── Error Alert Banner ───
                if (!error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DestructiveRed.copy(alpha = 0.12f))
                            .border(1.dp, DestructiveRed.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.ErrorShield20),
                                contentDescription = null,
                                tint = DestructiveRed,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = error.orEmpty(),
                                color = DestructiveRed,
                                fontFamily = VkSansText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Primary Submit Button with Glass Elevation ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(VkBlue)
                        .liquidClickable(
                            enabled = !isLoading,
                            pressedScale = LiquidMotion.PressButton,
                            onClick = { submit() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = when (step) {
                                AuthStep.Phone -> "Войти"
                                AuthStep.TwoFactor -> "Подтвердить"
                                AuthStep.Password -> "Войти"
                                AuthStep.Captcha -> "Отправить"
                            },
                            color = Color.White,
                            fontFamily = VkSansText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }

                // ─── Secondary Back Option for 2FA/Captcha ───
                if (step != AuthStep.Phone) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = !isLoading,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                step = AuthStep.Phone
                                verificationCode = ""
                                validationSid = null
                                captchaSid = null
                                captchaKey = ""
                                error = null
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Назад к вводу телефона",
                            color = lc.textSecondary,
                            fontFamily = VkSansText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val bannerBg = if (isDark) Color(0xFF141416) else Color(0xFFF2F3F5)
            val bannerBorder = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bannerBg)
                    .border(1.dp, bannerBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = lmgVector(LmgDrawables.LockOutline28),
                        contentDescription = null,
                        tint = lc.textSecondary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(20.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Ваш аккаунт в безопасности",
                            fontFamily = VkSansText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = lc.textPrimary,
                        )
                        Text(
                            text = "LMG только отправляет ваши данные на сервера VK и сохраняет их в зашифрованной форме в Android Keystore.",
                            fontFamily = VkSansText,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = lc.textSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Reusable Glass TextField with Liquid styling for auth inputs.
 */
@Composable
private fun GlassAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    autoCorrect: Boolean = true,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val lc = LiquidTheme.colors
    val isDark = lc.isDark

    val containerColor = if (isDark) Color(0xFF161618) else Color(0xFFF7F8FA)
    val focusedBorderColor = VkBlue
    val unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        label = {
            Text(
                text = label,
                fontFamily = VkSansText,
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (value.isNotBlank()) VkBlue else lc.iconMuted,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = trailingIcon?.let { icon ->
            {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressIcon,
                            onClick = { onTrailingIconClick?.invoke() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = lc.iconMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = autoCorrect,
        ),
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedBorderColor = focusedBorderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            focusedLabelColor = VkBlue,
            unfocusedLabelColor = lc.textSecondary,
            focusedTextColor = lc.textPrimary,
            unfocusedTextColor = lc.textPrimary,
            cursorColor = VkBlue,
        ),
    )
}
