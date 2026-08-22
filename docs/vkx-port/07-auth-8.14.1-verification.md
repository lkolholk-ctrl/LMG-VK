# Сверка auth-флоу с VK X 8.14.1 (2026-08-22)

Свежий декомпилят: `/root/decompiled_vkx_deobf` (jadx --deobf), ключевые классы:
`C6144l` (token builder + captcha retry), `C19419l` (validateAccount/checkOtp),
`C12257l` (OTP dispatch), `AbstractC16826l` (sendOtp* builders), `C8341l` (transport
error handling), `C10995l`/`C3680l` (SmartCaptcha WebView + JS bridge), `C3258l`
(success_token).

## 1. Подтверждено дословно (спека 02-auth.md верна)

- `token` (C6144l:906-923): все 16 параметров, порядок, `libverify_support=true`,
  `2fa_supported=true`, `device_trusted_hash_support=true`, `supported_ways=push,email`,
  `flow_type=tg_flow`, `sak_version=1.142`; grant_type: password пуст → `without_password`,
  иначе z → `phone_confirmation_sid` / `password`. Extra (captcha params) кладётся
  последним через putAll.
- `auth.validateAccount` (C19419l:990): login?, force_password=0, passkey_supported=0,
  supported_ways = `callreset,codegen,email,reserve_code,password,push,sms`
  (**индекс 4 = password — подтвердилось**, EnumC12476l порядок:
  CALLRESET, CODEGEN, EMAIL, RESERVE_CODE, PASSWORD, PUSH, SMS),
  flow_type=auth_without_password, sak_version=**1.112**.
- `ecosystem.checkOtp` (C19419l:778): sid, code, verification_method
  (switch по enum: callreset/codegen/email/push/reserve_code/sms), flow_type=tg_flow,
  sak_version=1.142.
- `ecosystem.sendOtp*` (AbstractC16826l): только sid + flow_type=tg_flow + sak_version=1.142.
- `ecosystem.getVerificationMethods` (C4949l:789): sid + tg_flow + 1.142.
- Дискриминатор RequestTokenResponse (C5218l): ровно 6 веток по `error`
  (null→Success, object→NestedApiError, need_validation→2FA, need_captcha→Captcha,
  invalid_client→ClientError, else→Unknown). Ветки Processing НЕТ.
- SmartCaptcha WebView (C10995l/C3680l/C4058l): заголовок
  `X-Requested-With: com.vkontakte.android`, JS-мост `AndroidBridge` с методами
  `VKCaptchaGetResult` (JSON `{"token": ...}`), `VKCaptchaCloseCaptcha`,
  `VKCaptchaListenSensorsStart/Stop`.
- **`success_token`** (C3258l:33): результат SmartCaptcha кладётся в повторный запрос
  как параметр `success_token=<token>`. При закрытии без токена — пустая мапа/отмена
  («Skipped captcha» в C6144l:1034).
- Ошибка 14 в транспорте (C8341l): собирает captcha_sid (+ts/attempt) в параметры
  ретрая; при redirect_uri (error 17 или 14+redirect) — вызывает SmartCaptcha
  и ретраит с success_token. Порядок: сначала проверяется redirect_uri/str,
  потом captcha_sid.
- `get_anonym_token` (C6507l:558): только client_id=2274003 + client_secret.

## 2. Живой эксперимент с сервера (curl, 2026-08-22)

`POST https://api.vk.com/oauth/get_anonym_token` — работает, возвращает
`{token, expired_at}` (плоский JSON, без конверта).

`POST api.vk.com/method/auth.validateAccount` на реальный номер телефона
ВОЗВРАЩАЕТ **error 14 + redirect_uri на `id.vk.com/not_robot_captcha`
(с session_token, variant=popup, blank=1)** — то есть «Я не робот» приходит
УЖЕ на первом шаге validateAccount, до OTP. Поля ошибки:
`captcha_sid, captcha_img, captcha_ts, captcha_attempt, captcha_ratio,
redirect_uri, remixstlid, is_refresh_enabled, captcha_height, captcha_width,
is_sound_captcha_available`.

С фейковым success_token капча остаётся (проверка на сервере, токен должен быть
настоящим от SmartCaptcha).

`ecosystem.sendOtpSms` с невалидным sid → error 104 "Not found" (error_subcode=4).

## 3. Диагноз текущего бага LMG-VK («английский текст вместо номера, SMS не приходит»)

Экран TwoFactor показывает `destination` = `EcosystemSendOtpResponse.info`.
В `MusicAuth.prepareTwoFactor` при ошибке sendOtp в destination попадает
`sent.message` — **английское сообщение об ошибке VK**. Это ровно симптом
пользователя: значит `ecosystem.sendOtp*` падает, а экран всё равно показывается.

Направления проверки:
1. Взять DebugLog с устройства: `API ecosystem.sendOtpSms упал: ...` — там точная
   ошибка (код+сообщение).
2. Проверить, что sid после прохождения not_robot_captcha (ретрай validateAccount
   с success_token) доходит до sendOtp, и что anonymous_token в Bearer актуален
   (не истёк, тот же, с которым sid создан).
3. VK X 8.14.1 показывает выбор методов через getVerificationMethods; для SMS
   сначала validatePhone? — нет: в 8.14.1 sendOtpSms идёт напрямую с sid от
   validateAccount. Но ПОРЯДОК C12257l.m4067import: next_step.verification_method
   решает какой sendOtpX вызвать (ordinal switch), VK сам выбирает метод.
4. Возможная причина: LMG-VK шлёт `access_token` параметром в дополнение к
   Bearer (VK X — только Bearer). Формально допустимо, но стоит проверить
   на живом запросе.

## 4. Аудит соответствия LMG-VK ↔ 8.14.1

Совпадает: все параметры token/validateAccount/checkOtp/sendOtp*, шесть веток
дискриминатора, success_token, X-Requested-With, captcha-параметры.

Расхождения (кандидаты):
- Аргумент `User-Agent` отдельного метода равен `null`, но общий Ktor-плагин клиента
  всё равно добавляет значение из native bundle, slot 13 (`C13651l` → `C1483l`, case 22 →
  `C2269l`). Поэтому auth-запросы VK X не являются запросами без UA. В LMG-VK этой
  ветке соответствует `VkUserAgents.auth`.

## 5. Повторная полная сверка после полевого теста (2026-08-22)

- Причина перехода сразу к паролю найдена в `MusicAuth.startAuthAttempt`: LMG-VK
  заменял выбранный сервером `next_step.verification_method` на `password`, когда
  пароль лишь присутствовал среди альтернатив. В `C19419l`, case 22, такой замены нет:
  `PASSWORD` открывается только когда `next_step == null` либо сервер вернул
  `verification_method == PASSWORD`; иначе вызывается OTP-dispatch.
- Из `auth.validateAccount` удалён отсутствующий в билдере 8.14.1 параметр
  `accounts_trusted_hashes`.
- Дискриминатор ответа `oauth/token` приведён к исходным шести веткам по значению
  поля `error`; неподтверждённая ветка `processing` удалена.
- Легаси-`need_validation` из `oauth/token` теперь открывает ввод кода и повторяет
  `token` с `validation_sid` и `code`, а не пытается вызвать `ecosystem.checkOtp`.
- Успешный OAuth больше не зависит от немедленного получения exchange token. VK X
  сначала сохраняет access token и завершает вход; exchange token получается отдельно.
  LMG-VK теперь сохраняет сессию сразу, а exchange token получает как необязательное
  продолжение.

## 6. Сквозная идентичность Android-клиента

- Все вызовы `VkApiClient` передают Android `api_id=2274003`, стабильный
  `device_id`, Android `User-Agent`, `X-VK-Android-Client: new` и
  `X-Screen: nowhere`. Для `/oauth/` остаются точные параметры
  `client_id`/`client_secret` и auth UA.
- Общий OkHttp-транспорт теперь добавляет Android UA к запросам на
  домены VK и его CDN, если метод не задал более точный auth UA.
  Сторонние хосты не получают эту идентичность.
- Та же проверка хоста применяется к прямым `HttpURLConnection`-загрузкам
  аудио, обложек, Mix-анимаций и upload URL профиля.
