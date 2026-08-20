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

private enum class AuthStep { Credentials, TwoFactor, Captcha }

/**
 * Liquid Glass Authorization Screen for VK ID OAuth flow.
 *
 * Fully reactive and self-contained with:
 * - Credentials entry (phone/email + password) with show/hide toggle
 * - Two-Factor authentication / OTP check
 * - Visual captcha security check
 * - Keystore security disclaimer and smooth step transitions
 */
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

    val isLoggedIn by MusicAuth.isLoggedIn.collectAsState()
    LaunchedEffect(isLoggedIn, isAddingAccount) {
        if (isLoggedIn && !isAddingAccount) onAuthSuccess()
    }

    var step by remember { mutableStateOf(AuthStep.Credentials) }
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

    val passwordFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    val captchaFocusRequester = remember { FocusRequester() }

    val canSubmit = !isLoading && when (step) {
        AuthStep.Credentials -> login.isNotBlank() && password.isNotBlank()
        AuthStep.TwoFactor -> verificationCode.isNotBlank()
        AuthStep.Captcha -> captchaKey.isNotBlank()
    }

    val submit = {
        if (canSubmit) {
            keyboardController?.hide()
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
                                if (step == AuthStep.Credentials) {
                                    password = ""
                                    onBack()
                                } else {
                                    step = AuthStep.Credentials
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
                            AuthStep.Credentials -> if (isAddingAccount) "Add account" else "VK ID"
                            AuthStep.TwoFactor -> "2-Step verification"
                            AuthStep.Captcha -> "Security check"
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
                AuthStep.Credentials -> VkBlue
                AuthStep.TwoFactor -> Color(0xFFFF9500)
                AuthStep.Captcha -> DestructiveRed
            }
            val heroIcon = when (step) {
                AuthStep.Credentials -> lmgVector(LmgDrawables.UserCircleOutline28)
                AuthStep.TwoFactor -> lmgVector(LmgDrawables.PincodeLockOutline28)
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
                    AuthStep.Credentials -> if (isAddingAccount) "Add VK account" else "Sign in with VK ID"
                    AuthStep.TwoFactor -> "Verification code"
                    AuthStep.Captcha -> "Security confirmation"
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
                    AuthStep.Credentials -> "Access your music library, personalized recommendations, and playlists."
                    AuthStep.TwoFactor -> buildString {
                        append("Enter the confirmation code sent to ")
                        append(destination.ifBlank { "your VK account" })
                        if (expectedCodeLength > 0) append(" ($expectedCodeLength digits)")
                        append('.')
                    }
                    AuthStep.Captcha -> "Enter the characters shown in the security image below."
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
                            AuthStep.Credentials -> {
                                GlassAuthField(
                                    value = login,
                                    onValueChange = { login = it.trim(); error = null },
                                    label = "Phone number or email",
                                    leadingIcon = lmgVector(LmgDrawables.UserOutline28),
                                    keyboardType = KeyboardType.Email,
                                    autoCorrect = false,
                                    imeAction = ImeAction.Next,
                                    keyboardActions = KeyboardActions(
                                        onNext = { passwordFocusRequester.requestFocus() },
                                    ),
                                )

                                GlassAuthField(
                                    value = password,
                                    onValueChange = { input ->
                                        // Only ASCII printable characters: English letters, digits, and symbols
                                        password = input.filter { it.code in 32..126 }
                                        error = null
                                    },
                                    label = "Password",
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
                            AuthStep.TwoFactor -> {
                                LaunchedEffect(Unit) {
                                    codeFocusRequester.requestFocus()
                                }

                                GlassAuthField(
                                    value = verificationCode,
                                    onValueChange = { input ->
                                        // Strictly numeric keyboard and digits only
                                        val digits = input.filter(Char::isDigit)
                                        verificationCode = if (expectedCodeLength > 0) {
                                            digits.take(expectedCodeLength)
                                        } else digits
                                        error = null
                                        if (expectedCodeLength > 0 && verificationCode.length == expectedCodeLength) {
                                            submit()
                                        }
                                    },
                                    label = "Confirmation code",
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
                                    label = "Characters from image",
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
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = error ?: "",
                                color = DestructiveRed,
                                fontFamily = VkSansText,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Primary Submit Button ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            if (canSubmit) VkBlue else VkBlue.copy(alpha = 0.35f)
                        )
                        .liquidClickable(
                            pressedScale = LiquidMotion.PressButton,
                            enabled = canSubmit,
                            onClick = { submit() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    } else {
                        Text(
                            text = when (step) {
                                AuthStep.Credentials -> if (isAddingAccount) "Add account" else "Sign in"
                                AuthStep.TwoFactor -> "Verify & Continue"
                                AuthStep.Captcha -> "Submit code"
                            },
                            color = Color.White,
                            fontFamily = VkSansText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }

                // ─── Secondary Back Option for 2FA/Captcha ───
                if (step != AuthStep.Credentials) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                enabled = !isLoading,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                step = AuthStep.Credentials
                                verificationCode = ""
                                validationSid = null
                                captchaSid = null
                                captchaKey = ""
                                error = null
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Back to credentials",
                            color = lc.textSecondary,
                            fontFamily = VkSansText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Security and Privacy Trust Footer ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = lmgVector(LmgDrawables.CheckShieldOutline28),
                    contentDescription = null,
                    tint = lc.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Encrypted in Android Keystore • Passwords are never stored",
                    color = lc.textTertiary,
                    fontFamily = VkSansText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

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
    autoCorrect: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val lc = LiquidTheme.colors
    val isDark = lc.isDark

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = VkSansText,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = lc.iconDefault,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingIcon = trailingIcon?.let { icon ->
            {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = onTrailingIconClick != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTrailingIconClick?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = lc.iconDefault,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            autoCorrect = autoCorrect,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = lc.textPrimary,
            unfocusedTextColor = lc.textPrimary,
            focusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
            unfocusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
            focusedBorderColor = VkBlue,
            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
            focusedLabelColor = VkBlue,
            unfocusedLabelColor = lc.textSecondary,
            cursorColor = VkBlue,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
