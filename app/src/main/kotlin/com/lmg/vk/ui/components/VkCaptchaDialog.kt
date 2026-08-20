package com.lmg.vk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lmg.vk.network.CaptchaPrompt
import com.lmg.vk.ui.glass.liquidClickable
import com.lmg.vk.ui.icons.LmgDrawables
import com.lmg.vk.ui.icons.lmgVector
import com.lmg.vk.ui.theme.LiquidMotion
import com.lmg.vk.ui.theme.LiquidTheme
import com.lmg.vk.ui.theme.VkSansDisplay
import com.lmg.vk.ui.theme.VkSansText

private val VkBlue = Color(0xFF0077FF)

/**
 * Глобальный стеклянный диалог ввода капчи VK.
 */
@Composable
fun VkCaptchaDialog(
    prompt: CaptchaPrompt?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    if (prompt == null) return

    var code by remember(prompt) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(prompt) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val colors = LiquidTheme.colors
        val isDark = colors.isDark
        val dialogBg = if (isDark) Color(0xFF1C1C1E) else Color.White
        val dialogBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(dialogBg)
                    .border(1.dp, dialogBorder, RoundedCornerShape(28.dp))
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* prevent dismiss */ },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Header icon
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(VkBlue.copy(alpha = if (isDark) 0.18f else 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = lmgVector(LmgDrawables.CheckShieldOutline28),
                            contentDescription = null,
                            tint = VkBlue,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Security check",
                        fontFamily = VkSansDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Enter the characters from the image to continue",
                        fontFamily = VkSansText,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    // Captcha image container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) Color(0xFF141416) else Color(0xFFE5E5EA))
                            .border(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                                RoundedCornerShape(16.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = prompt.imageUrl,
                            contentDescription = "VK Captcha Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Input field
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.code in 33..126 }
                        },
                        label = { Text("Captcha code", fontFamily = VkSansText) },
                        leadingIcon = {
                            Icon(
                                imageVector = lmgVector(LmgDrawables.KeySquareOutline28),
                                contentDescription = null,
                                tint = colors.iconDefault,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (code.isNotBlank()) onSubmit(code)
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
                            unfocusedContainerColor = if (isDark) Color(0xFF141416) else Color(0xFFF2F2F7),
                            focusedBorderColor = VkBlue,
                            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            focusedLabelColor = VkBlue,
                            unfocusedLabelColor = colors.textSecondary,
                            cursorColor = VkBlue,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )

                    Spacer(Modifier.height(20.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Cancel button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                .liquidClickable(
                                    pressedScale = LiquidMotion.PressButton,
                                    onClick = onDismiss,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Cancel",
                                color = colors.textPrimary,
                                fontFamily = VkSansText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                        }

                        // Submit button
                        val canSubmit = code.isNotBlank()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (canSubmit) VkBlue else VkBlue.copy(alpha = 0.35f))
                                .liquidClickable(
                                    pressedScale = LiquidMotion.PressButton,
                                    enabled = canSubmit,
                                    onClick = {
                                        if (canSubmit) onSubmit(code)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Submit",
                                color = Color.White,
                                fontFamily = VkSansText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
