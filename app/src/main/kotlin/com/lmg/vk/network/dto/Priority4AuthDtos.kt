package com.lmg.vk.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Wire-контракты auth/ecosystem, подтверждённые DTO и сериализаторами Priority 4. */

@JsonClass(generateAdapter = true)
data class AuthGetExchangeTokenResponse(
    @Json(name = "users_exchange_tokens")
    val usersExchangeTokens: List<AuthUserExchangeToken>? = null,
)

@JsonClass(generateAdapter = true)
data class AuthUserExchangeToken(
    @Json(name = "user_id") val userId: Long,
    @Json(name = "profile_type") val profileType: AuthProfileType? = null,
    @Json(name = "common_token") val commonToken: String? = null,
    @Json(name = "tier_tokens") val tierTokens: List<AuthExchangeToken>? = null,
)

enum class AuthProfileType {
    @Json(name = "0")
    NORMAL,

    @Json(name = "2")
    EDU,
}

@JsonClass(generateAdapter = true)
data class AuthExchangeToken(
    val tier: Int,
    val token: String,
)

@JsonClass(generateAdapter = true)
data class AuthValidateAccountResponse(
    @Json(name = "is_phone") val isPhone: Boolean? = null,
    @Json(name = "is_email") val isEmail: Boolean? = null,
    @Json(name = "flow_name") val flowName: AuthFlowName? = null,
    @Json(name = "flow_names") val flowNames: List<String>? = null,
    val ads: Boolean? = null,
    val sid: String? = null,
    @Json(name = "pass_sid") val passSid: Boolean? = null,
    val login: String? = null,
    @Json(name = "next_step") val nextStep: AuthValidateAccountNextStep? = null,
    @Json(name = "remember_hash") val rememberHash: String? = null,
)

enum class AuthFlowName {
    @Json(name = "need_password_and_validation")
    NEED_PASSWORD_AND_VALIDATION,

    @Json(name = "need_validation")
    NEED_VALIDATION,

    @Json(name = "need_password")
    NEED_PASSWORD,

    @Json(name = "need_registration")
    NEED_REGISTRATION,

    @Json(name = "need_login_validation")
    NEED_LOGIN_VALIDATION,

    @Json(name = "need_passkey")
    NEED_PASSKEY,

    @Json(name = "need_passkey_otp")
    NEED_PASSKEY_OTP,

    @Json(name = "need_webauthn")
    NEED_WEBAUTHN,
}

@JsonClass(generateAdapter = true)
data class AuthValidateAccountNextStep(
    @Json(name = "verification_method") val verificationMethod: AuthVerificationMethod? = null,
    @Json(name = "has_another_verification_methods")
    val hasAnotherVerificationMethods: Boolean? = null,
    @Json(name = "external_id") val externalId: String? = null,
    @Json(name = "service_code") val serviceCode: AuthServiceCode? = null,
)

enum class AuthVerificationMethod {
    @Json(name = "callreset")
    CALLRESET,

    @Json(name = "codegen")
    CODEGEN,

    @Json(name = "email")
    EMAIL,

    @Json(name = "libverify")
    LIBVERIFY,

    @Json(name = "passkey")
    PASSKEY,

    @Json(name = "password")
    PASSWORD,

    @Json(name = "push")
    PUSH,

    @Json(name = "qr_code")
    QR_CODE,

    @Json(name = "reserve_code")
    RESERVE_CODE,

    @Json(name = "sms")
    SMS,
}

enum class AuthServiceCode {
    @Json(name = "1")
    TYPE_1FA,

    @Json(name = "2")
    TYPE_2FA,
}

@JsonClass(generateAdapter = true)
data class AuthProcessAuthCodeResponse(
    val status: Int = 0,
    @Json(name = "auth_info") val authInfo: AuthCodeAuthInfo? = null,
    val errors: List<AuthIndexedError> = emptyList(),
    val profile: AuthCodeUser? = null,
)

@JsonClass(generateAdapter = true)
data class AuthCodeAuthInfo(
    @Json(name = "auth_id") val authId: String,
    @Json(name = "client_info") val clientInfo: AuthCodeClientInfo,
    @Json(name = "device_info") val deviceInfo: AuthCodeDeviceInfo,
    val domain: String = "",
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "flow_type") val flowType: Int,
)

@JsonClass(generateAdapter = true)
data class AuthCodeClientInfo(
    val agreements: List<Any?> = emptyList(),
    @Json(name = "icon_150") val icon150: String? = null,
    @Json(name = "icon_75") val icon75: String? = null,
    val id: Int,
    @Json(name = "is_official") val isOfficial: Boolean = false,
    val name: String,
    val scopes: List<Any?> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AuthCodeDeviceInfo(
    @Json(name = "browser_name") val browserName: String? = null,
    @Json(name = "browser_package") val browserPackage: String? = null,
    @Json(name = "browser_page_link") val browserPageLink: String? = null,
    @Json(name = "browser_url_scheme") val browserUrlScheme: String? = null,
    val ip: String? = null,
    val location: String? = null,
    @Json(name = "location_map") val locationMap: String? = null,
    val name: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthIndexedError(
    val index: Int,
    val description: String,
)

@JsonClass(generateAdapter = true)
data class AuthCodeUser(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    val phone: String? = null,
    @Json(name = "photo_200") val photo200: String? = null,
    @Json(name = "photo_50") val photo50: String? = null,
)

@JsonClass(generateAdapter = true)
data class EcosystemCheckOtpResponse(
    val sid: String = "",
    @Json(name = "profile_exist") val profileExist: Boolean = false,
    val profile: AuthUser? = null,
    @Json(name = "can_skip_password") val canSkipPassword: Boolean? = null,
    @Json(name = "next_step") val nextStep: EcosystemNextStep? = null,
    @Json(name = "signup_restriction_reason") val signupRestrictionReason: String? = null,
    @Json(name = "signup_fields") val signupFields: List<String>? = null,
    @Json(name = "signup_fields_values") val signupFieldsValues: AuthSignupFieldsValues? = null,
    @Json(name = "signup_params") val signupParams: AuthValidateSignupParams? = null,
)

@JsonClass(generateAdapter = true)
data class AuthSignupFieldsValues(
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "middle_name") val middleName: String? = null,
    val gender: AuthGender? = null,
    val birthday: Any? = null,
    val avatar: String? = null,
)

enum class AuthGender {
    @Json(name = "0")
    UNKNOWN,

    @Json(name = "1")
    FEMALE,

    @Json(name = "2")
    MALE,
}

@JsonClass(generateAdapter = true)
data class AuthUser(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "has_2fa") val has2fa: Boolean,
    @Json(name = "last_name") val lastName: String,
    @Json(name = "photo_200") val photo200: String,
    val deactivated: String? = null,
    val phone: String? = null,
    @Json(name = "has_password") val hasPassword: Boolean? = null,
    @Json(name = "can_unbind_phone") val canUnbindPhone: Boolean? = null,
)

enum class EcosystemNextStep {
    @Json(name = "auth")
    AUTH,

    @Json(name = "registration")
    REGISTRATION,

    @Json(name = "show_with_password")
    SHOW_WITH_PASSWORD,

    @Json(name = "show_without_password")
    SHOW_WITHOUT_PASSWORD,
}

@JsonClass(generateAdapter = true)
data class AuthValidateSignupParams(
    @Json(name = "password_min_length") val passwordMinLength: Int? = null,
    @Json(name = "birth_date_max") val birthDateMax: String? = null,
)

@JsonClass(generateAdapter = true)
data class EcosystemGetVerificationMethodsResponse(
    val methods: List<EcosystemVerificationMethod>? = null,
)

@JsonClass(generateAdapter = true)
data class EcosystemVerificationMethod(
    val name: AuthVerificationMethod? = null,
    val priority: Int? = null,
    val timeout: Int? = null,
    val info: String? = null,
    @Json(name = "can_fallback") val canFallback: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AuthGetAuthCodeStatusResponse(
    val status: Int,
    @Json(name = "expires_in") val expiresIn: Int = 0,
    @Json(name = "user_id") val userId: Long = 0L,
    @Json(name = "access_token") val accessToken: String = "",
)

sealed interface RequestTokenResponse {
    @JsonClass(generateAdapter = true)
    data class Success(
        @Json(name = "user_id") val userId: Long = 0L,
        @Json(name = "access_token") val accessToken: String = "",
        @Json(name = "expires_in") val accessTokenExpiresIn: Int = 0,
        @Json(name = "trusted_hash") val trustedHash: String = "",
    ) : RequestTokenResponse

    @JsonClass(generateAdapter = true)
    data class ClientError(
        val error: String = "",
        @Json(name = "error_description") val errorDescription: String = "",
        @Json(name = "error_type") val errorType: String = "",
    ) : RequestTokenResponse

    @JsonClass(generateAdapter = true)
    data class TwoFactorRequired(
        @Json(name = "validation_type") val validationType: AuthValidationType,
        @Json(name = "phone_mask") val phoneMask: String = "",
        @Json(name = "masked_email") val maskedEmail: String = "",
        @Json(name = "code_length") val codeLength: Int = 4,
        @Json(name = "device_name") val deviceName: String = "",
        @Json(name = "validation_sid") val validationSid: String = "",
    ) : RequestTokenResponse

    @JsonClass(generateAdapter = true)
    data class NestedApiError(
        val error: VKError,
    ) : RequestTokenResponse

    @JsonClass(generateAdapter = true)
    data class CaptchaRequired(
        @Json(name = "captcha_sid") val captchaSid: String = "",
        @Json(name = "captcha_img") val captchaImg: String = "",
        @Json(name = "captcha_ts") val captchaTs: Double = 0.0,
        @Json(name = "captcha_ratio") val captchaRatio: Double = 0.0,
        @Json(name = "captcha_attempt") val captchaAttempt: Int = 0,
        @Json(name = "redirect_uri") val redirectUri: String = "",
    ) : RequestTokenResponse

    data object Processing : RequestTokenResponse

    @JsonClass(generateAdapter = true)
    data class UnknownError(
        val error: String = "",
        @Json(name = "error_description") val errorDescription: String = "",
    ) : RequestTokenResponse
}

enum class AuthValidationType {
    @Json(name = "2fa_sms")
    Sms,

    @Json(name = "2fa_push")
    Push,

    @Json(name = "2fa_email")
    Email,

    @Json(name = "2fa_app")
    App,

    @Json(name = "2fa_libverify")
    LibVerify,

    @Json(name = "2fa_callreset")
    CallReset,

    ReserveCode,
}
