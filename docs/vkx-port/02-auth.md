# 02 — Аутентификация и OTP (группа 2)

Спецификация для порта в LMG-VK. Всё извлечено из реверс-инжиниринговых документов и
декомпилированных исходников VK X 8.12.1. Где подтверждения нет — так и написано.

## Условные обозначения источников

| Сокращение | Путь |
|---|---|
| `EP` | `/storage/emulated/0/Download/VKLMG_Recovery/VKX-ENDPOINTS.md` |
| `P1` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY1-RECOVERY.md` |
| `P3` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY3-RECOVERY.md` |
| `P4` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY4-RECOVERY.md` |
| `SRC` | `/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/<class>.java` |

Уверенность: **подтверждено** — есть дословный фрагмент кода; **частично** — фрагмент есть,
но покрывает не всё либо источники противоречат; **не найдено в доках** — догадок нет.

## Как читать фрагменты запросов (`C5577e` = аналог `VkMethod`)

`SRC C5577e.java` — билдер запроса VK X:

| Вызов в декомпиляте | Что значит | Эквивалент LMG-VK |
|---|---|---|
| `.ad("k", str)` | строковый параметр; **при `str == null` параметр не добавляется** | `param("k", str)` |
| `.vip(n, "k")` | `Int` → `String.valueOf(n)` | `param("k", n: Int)` |
| `.metrica(l, "k")` | `Long` → `String.valueOf(l)` | `param("k", l: Long)` |
| `.license("k", z)` | `Boolean` → `"1"` / `"0"` | `param("k", z: Boolean)` |
| `.license = true` (поле) | **флаг OAuth** → путь `/oauth/` вместо `/method/` | `endpoint = VkEndpoint.API_OAUTH` |
| `.metrica = "5.180"` (поле) | версия API для этого вызова; дефолт `"5.272"` | `apiVersion` |
| `.appmetrica = true` (поле) | one-shot: ответ не читается | `isOneShot` |

Сериализаторы kotlinx (`C4707e(fqn, obj, keyCount)` + `advert(key, isOptional)`):
`advert("k", false)` = **ключ обязателен**, `advert("k", true)` = опционален (есть дефолт).

---

# 1. Транспорт

Источник: `SRC C8221e.java` (метод `appmetrica(name, isOAuth, apiVersion, params, userAgent, cont)`,
строки ~490-760; обработка ответа в `license(C5577e, cont)`), `SRC C5577e.java`.

## 1.1. URL

```
https://api.<domain>/<method|oauth>/<имя метода>
```

- Хост — **всегда** `"api." + AbstractC7205e.metrica` (`C8221e.java:617-618`). Отдельного хоста
  `oauth.vk.com` **нет**: OAuth отличается только сегментом пути.
- `<domain>` — `vk.com` или `vk.ru`, переключается `EnumC6583e`
  (`VK_COM_WORKS` / `VK_RU_WORKS` / `NOTHING_WORKS`) по пингу `api.<domain>/ping.txt`.
- Сегмент пути: `isOAuth ? "oauth" : "method"` (`C8221e.java:620-621`).
- HTTP-метод — **POST для всех вызовов, включая OAuth** (`var27_27.vip = C3434e.metrica`,
  `C8221e.java:612` и повторно `:702`).
- `Content-Type: application/x-www-form-urlencoded` (`AbstractC7312e.metrica`,
  `C8221e.java:622-624`). **Комментарий `// application/json` в `EP` неверен** — тело
  собирается form-билдером `C10095e` и уходит как форма.

**Уверенность:** подтверждено.

## 1.2. Заголовки

| Заголовок | Значение | Условие |
|---|---|---|
| `Content-Type` | `application/x-www-form-urlencoded` | всегда |
| `X-VK-Android-Client` | `new` | всегда |
| `X-Screen` | `nowhere` | всегда |
| `Authorization` | `Bearer <токен>` | если токен разрешён (см. 1.4) |
| `User-Agent` | значение аргумента | только если аргумент не `null`; из `license(C5577e,…)` передаётся `null` |

`C8221e.java:626-637`. **Уверенность:** подтверждено.

Важно: `license(C5577e, cont)` вызывает `appmetrica(..., userAgent = null, ...)`, поэтому
сам метод не переопределяет UA. Однако созданный в `C13651l` общий Ktor-клиент устанавливает
плагин UserAgent: `C1483l`, case 22 берёт native bundle slot 13, затем `C2269l` добавляет
`User-Agent`, если заголовок ещё отсутствует. Поэтому обычные auth-запросы VK X имеют UA;
`null` означает только отсутствие локального override.

## 1.3. Параметры, добавляемые транспортом ко всем запросам

Сначала в форму кладутся параметры метода, затем транспорт **добавляет** (`add`, не `set`):

| Параметр | Значение | Источник |
|---|---|---|
| `v` | `C5577e.metrica`, по умолчанию `"5.272"` | `C8221e.java:648` |
| `https` | `"1"` | `C8221e.java:649` |
| `api_id` | `"2274003"` (`String.valueOf(2274003)`) | `C8221e.java:650-651` |
| `lang` | нормализованная локаль: `uk→ua`, `kk→kz`, иначе первое совпадение из белого списка (5 элементов), иначе `en` | `C8221e.java:652-697` |
| `device_id` | `C5170e.amazon()` — постоянный id устройства | `C8221e.java:698-701` |

`api_id` добавляется **и для OAuth-запросов тоже** — ветвления по `isOAuth` в этом блоке нет.

**Уверенность:** подтверждено.

## 1.4. Токен: как выбирается и куда кладётся

Токен уходит **только в заголовке `Authorization: Bearer`**, отдельного form-параметра
`access_token` транспорт не добавляет (`C8221e.java:629-633`).

Порядок разрешения (`C8221e.java:555-596`):

1. Если `params["access_token"]` задан и непуст → берётся он.
   **При этом он остаётся и в теле формы** — код только читает мапу, не удаляет ключ.
2. Иначе если имя метода == `auth.getExchangeToken` → сохранённый exchange-токен
   (`((C18479e) billing).vip`).
3. Иначе если `isOAuth` **или** имя метода == `auth.refreshTokens` → токен не подставляется
   (заголовка `Authorization` нет).
4. Иначе → `ad(cont)`:
   - если сохранённый `userId == 0` → `C17212e(…)` = **`get_anonym_token`**, и его результат
     идёт в `Bearer`;
   - иначе → `C0593e(…)` (`auth.getExchangeToken`) → `C18301e(false, …)`
     (`auth.refreshTokens`) → возвращается `C18479e.vip`.

**Практический вывод для auth-флоу:** во всех вызовах `auth.validateAccount`,
`ecosystem.*` VK X **не передаёт** `access_token` параметром — анонимный токен подставляется
транспортом в `Bearer` автоматически (шаг 4, ветка `userId == 0`).
Исключения, где `access_token` передан явно параметром: `auth.setAuthCodeStatus`,
`auth.processAuthCodeMulti` (там он идёт и в тело, и в `Bearer`).

**Уверенность:** подтверждено.

## 1.5. Чем auth-запросы отличаются от обычных API-вызовов

| Аспект | Обычный `method.*` | Auth |
|---|---|---|
| Путь | `/method/<name>` | `token`, `get_anonym_token` → `/oauth/<name>`; остальные auth-методы → обычный `/method/` |
| HTTP-ошибка (не 2xx) | сразу `C7220e(status, message)` — тело не парсится | при `isOAuth` **парсинг продолжается**, тело разбирается как обычный ответ (`C8221e.java:920-930`) |
| Конверт ответа | `RawVkResponse{response, error}` (`SRC C11464e.java`, FQN `…objects.internal.RawVkResponse`, оба ключа опциональны) | у `token` — **плоский JSON без конверта** (см. 3.2) |
| Ошибка в OAuth-ответе | — | если распарсенные данные — `C15748e` (`NestedApiError`), берётся его поле `.ad` как `VkErrorDetails` (`C8221e.java:943-950`) |
| Токен | `Bearer` из сессии | см. 1.4 |
| One-shot | — | one-shot метод даёт `C7220e(993, "BH.VkApi - One-Shot methods have no content")` |

Ошибка-объект: `VkErrorDetails` (`SRC C8733e.java`, FQN `…objects.internal.VkErrorDetails`),
9 ключей: `error_code`(обяз.) `error_msg`(обяз.) `request_params` `captcha_img` `captcha_sid`
`captcha_ratio` `captcha_ts` `captcha_attempt` `redirect_uri`.

Маппер ошибок (`SRC C15802e.java:150-300`, метод `smaato(Throwable)`):
код `1117` **или** сообщение содержит `access_token` → `TokenExpired`;
код `5` **и** сообщение содержит `blocked` → `Blocked`; иначе — общие ветки.

**Уверенность:** подтверждено.

---

# 2. Методы

Ниже: параметры → форма ответа → источник → уверенность → расхождение с текущим кодом LMG-VK.

## 2.1. `auth.validateAccount`

Путь: `/method/`. Токен: не передаётся параметром (Bearer с анонимным токеном).

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `login` | String | нет | — | `.ad("login", …)` только если аргумент != `null` |
| `force_password` | Bool→`"0"` | да | `"0"` | `.license("force_password", false)` — литерал |
| `passkey_supported` | Int | да | `0` | `.vip(0, "passkey_supported")`, значение из `new Integer(0)` |
| `supported_ways` | String, CSV | да | — | `joinToString(",")` по массиву из 7 enum-значений |
| `flow_type` | String | да | `"auth_without_password"` | литерал |
| `sak_version` | String | да | `"1.112"` | литерал — **не `1.142`** |

`supported_ways`: массив `EnumC10783e[7]`, индексы 0,1,2,3,5,6 =
`callreset, codegen, email, reserve_code, push, sms`. **Индекс 4 в декомпиляте потерян**
(локальная переменная `var25_68`). Из десяти значений enum
(`SRC EnumC10783e.java`: `callreset, codegen, email, libverify, passkey, password, push,
qr_code, reserve_code, sms`) единственное оставшееся со статическим полем — `password`,
поэтому вероятная строка — `callreset,codegen,email,reserve_code,password,push,sms`.
Это **вывод по исключению, а не дословный фрагмент**.

FQN enum: `…objects.auth.AuthValidateAccountSupportedWaysDto` (`SRC C14582e.java:284-297`).

**Ответ:** `AuthValidateAccountResponseDto` (`SRC C7791e.java`, сериализатор `SRC C5792e.java`),
10 ключей, **все опциональны и nullable**:

| Ключ | Тип | Прим. |
|---|---|---|
| `is_phone` | Boolean? | |
| `is_email` | Boolean? | |
| `flow_name` | enum? | `…AuthValidateAccountResponseDto.FlowNameDto`: `need_password_and_validation`, `need_validation`, `need_password`, `need_registration`, `need_login_validation`, `need_passkey`, `need_passkey_otp`, `need_webauthn` (`SRC C14582e.java:261-273`) |
| `flow_names` | List\<FlowNameDto\>? | |
| `ads` | Boolean? | |
| `sid` | String? | |
| `pass_sid` | Boolean? | |
| `login` | String? | |
| `next_step` | object? | `AuthValidateAccountNextStepDto` (`SRC C13315e.java`): `verification_method`(enum SupportedWays, opt), `has_another_verification_methods`(Boolean?, opt), `external_id`(String?, opt), `service_code`(enum `ServiceCodeDto`, opt) |
| `remember_hash` | String? | |

`ServiceCodeDto` — 2 значения; второе дословно `"2"`, первое в декомпиляте — переиспользованная
локальная строка (`SRC C14582e.java:274-281`), по контексту `"1"`. Первое значение — **частично**.

**Источник:** `SRC C6626e.java:1183-1252`; ответ — `SRC C7791e.java`, `SRC C5792e.java`, `P4:71-79`.
**Уверенность:** параметры — подтверждено (кроме индекса 4 в `supported_ways`: частично);
ответ — подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:445-466`): лишний параметр
`accounts_trusted_hashes` (в доках **отсутствует**) и лишний `access_token` параметром.

## 2.2. `auth.validatePhone`

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `sid` | String | да | — | |
| `client_id` | Int | да | `2274003` | |
| `client_secret` | String | да | `hHbZxrka2uZ6jB1inYsH` | |
| `libverify_support` | String | да | `"0"` | литерал строки, **не** `.license(...)` |
| `allow_callreset` | String | да | `"0"` | тот же литерал |
| `disable_partial` | String | да | `"0"` | тот же литерал |
| `supported_ways` | String | да | `"push,email"` | |

Ни `flow_type`, ни `sak_version` здесь нет.

**Ответ:** `ValidatePhoneResponse` (`SRC C14007e.java`, сериализатор `SRC C18165e.java`),
FQN `…objects.auth.ValidatePhoneResponse`, 10 ключей, все опциональны:

| Ключ | Тип | Дефолт |
|---|---|---|
| `next_sid` | String? | null |
| `validation_type` | enum? | null |
| `validation_resend` | enum? | null |
| `delay` | Int | **120** |
| `external_id` | String? | null |
| `phone` | String? | null |
| `phone_mask` | String? | null |
| `masked_email` | String? | null |
| `code_length` | Int | 0 |
| `device_name` | String? | null |

Enum обоих `validation_*` — `EnumC8519e`, FQN `bruhcollective.itaysonlab.vkshared.ValidationTypeConfirmation`,
wire-имена: `sms`, `push`, `email`, `callreset` (`SRC C8462e.java:164-171`).

**Источник:** `SRC C16600e.java:1237-1250`, `EP` (фрагмент `C16600e.java:207`);
ответ — `SRC C14007e.java:38-68`, `SRC C18165e.java:9-20`.
**Уверенность:** подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:468-483`, DTO
`app/src/main/kotlin/com/lmg/vk/network/dto/gen/auth/ValidatePhoneResponse.kt`): совпадает.

## 2.3. `auth.processAuthCode`

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `action` | Int | да | — | `.vip(this.<поле лямбды>, "action")` — **значение приходит извне** |
| `auth_code` | String | да | — | |

**Возможные значения `action` в доках не найдены.** Поле — параметр конструктора
`C6046e(C2347e, String, int, cont)`; поиск вызывающих мест по `src-deobf` не завершается
за отведённое время (каталог 718 МБ).

**Ответ:** `AuthProcessAuthCodeResponseDto` (`SRC C8125e.java`, сериализатор `SRC C10425e.java`),
4 ключа, все опциональны: `status`(Int), `auth_info`(object), `errors`(List), `profile`(object).

`auth_info` = `AuthCodeAuthInfoDto` (`SRC C2447e.java`), 6 ключей:
`auth_id`(String, **обяз.**), `client_info`(object, обяз.), `device_info`(object, обяз.),
`domain`(String?, опц.), `expires_in`(Int, обяз.), `flow_type`(Int, обяз.).
Внутренности `client_info` / `device_info` / `profile` / `errors` — **не найдено в доках**
(не разбирал глубже одного уровня).

**Источник:** `SRC C6046e.java:145-160`; ответ — `P4:81-89`, `SRC C10425e.java`, `SRC C2447e.java`.
**Уверенность:** параметры — частично (значения `action` неизвестны); ответ — частично.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:525-538`): параметры совпадают.

## 2.4. `auth.processAuthCodeMulti`

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `action` | Int | да | **`0`** | `.vip(0, "action")` — литерал в коде |
| `auth_code` | String | да | — | |
| `access_token` | String | да | — | явно параметром (и дублируется в `Bearer`) |
| `access_tokens` | String | да | — | CSV-список токенов аккаунтов |

**Ответ:** тот же `AuthProcessAuthCodeResponseDto` (`C8125e`); место вызова берёт только
`auth_info` (`var2_3.vip`), при `null` показывает
«Ошибка при получении данных. Попробуйте другой код.».

**Источник:** `SRC C2347e.java:330-345`.
**Уверенность:** подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:540-543`): `action` принимается аргументом,
хотя в VK X это литерал `0`; **не передаётся `access_token`** (только `access_tokens`);
ответ выбрасывается (`execute<Any>`).

## 2.5. `auth.setAuthCodeStatus`

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `auth_code` | String | да | — |
| `access_token` | String | да | — |

**Ответ:** `AuthSetAuthCodeStatusResponseDto` (`SRC C10878e.java`, сериализатор `SRC C6895e.java`),
5 ключей:

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `status` | Int | **да** | — |
| `expires_in` | Int | нет | `0` |
| `polling_delay` | Int | нет | `0` |
| `faq_url` | String | нет | `""` |
| `domain` | String | нет | `""` |

Место вызова использует `polling_delay` и `domain`.

**Источник:** `SRC C2347e.java:141-160`; ответ — `SRC C6895e.java:9-15`, `SRC C10878e.java:14-35`,
`P1:309` (`C10878e = AuthSetAuthCodeStatusResponseDto`).
**Уверенность:** подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:612-616`): параметры совпадают, но ответ
разбирается как `Unit` — теряются `polling_delay` и `domain`.

## 2.6. `auth.getExchangeToken`

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `create_common_token` | Bool→`"1"` | да | `true` |
| `create_tier_tokens` | String | да | `"0"` |
| (`v`) | String | — | `"5.180"` в 3 из 4 мест вызова, в 4-м не задана (→ `5.272`) |

Токен: транспорт подставляет сохранённый exchange-токен (см. 1.4, шаг 2), параметром не идёт.

**Ответ:** `AuthGetExchangeTokenResponseDto` (`SRC C2654e.java`, сериализатор `SRC C17795e.java`),
1 опциональный ключ `users_exchange_tokens: List<AuthUserExchangeTokenDto>?`
(элемент через `SRC C1349e.java:80-84` → `C12881e`).

`AuthUserExchangeTokenDto` (`SRC C16319e.java`, сериализатор `SRC C12881e.java`), 4 ключа:
`user_id`(Long, **обяз.**), `profile_type`(enum?, опц.), `common_token`(String?, опц.),
`tier_tokens`(List?, опц.).

**Противоречие в доках:** `P3:185` называет ответ `C7862e ExchangeTokenResponse`, а `C7862e` по
`P1:309` — это `AnonymTokenResponseDto` `{token, expired_at}`. В декомпилированных фрагментах
`EP:614-685` действительно фигурируют два разных парсера: `C5107e.f10956e` (переменная
`c7862e`) и `C12575e.f25225e` (переменная `c2654e` + `C16319e`). Достоверно восстановлена
только вторая форма. Первая — **не найдено в доках**.

**Источник:** `EP:614-685` (`C0593e.java:327/345/365/427`), `P4:60-69`, `P4:162`,
`SRC C2654e.java`, `SRC C16319e.java`, `SRC C12881e.java`.
**Уверенность:** параметры — подтверждено; ответ — частично (две конфликтующие формы).

**Расхождение с LMG-VK**: `getExchangeToken()` (`VkMethodsRegistry.kt:485-496`) разбирает ответ
как `AnonymTokenResponse` `{token, expired_at}` — это форма **другого** метода;
`getUserExchangeTokens()` (`:499-523`) разбирает правильно, но **не ставит `v = 5.180`**.

## 2.7. `auth.refreshTokens` (уже реализован в LMG-VK)

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `client_id` | Int | да | `2274003` |
| `client_secret` | String | да | `hHbZxrka2uZ6jB1inYsH` |
| `exchange_tokens` | String, CSV | да | — | в коде — `singletonList(token).joinToString(",")` |
| `active_index` | Int | да | `0` | литерал `Integer(0)` |
| `scope` | String | да | `"all"` |
| `initiator` | String | да | `"expired_token"` |

Токен в `Bearer` **не подставляется** (`auth.refreshTokens` — исключение, см. 1.4 шаг 3).

**Ответ:** `AuthRefreshTokensResponseDto` (`SRC C0210e.java`, сериализатор `SRC C0199e.java`),
2 ключа, **оба обязательны**: `success: List<AuthRefreshTokenDto>`, `errors: List<AuthRefreshTokenErrorDto>`.

`AuthRefreshTokenDto` (`SRC C7946e.java`), 7 ключей:
`index`(Int, обяз.), `user_id`(Long, обяз.), `banned`(Bool, обяз.),
`access_token`(object?, опц.), `webview_access_token`(?, опц.),
`webview_refresh_token`(?, опц.), `silent_token`(?, опц.).
`access_token` = `AuthRefreshAccessTokenDto` (`SRC C8860e.java`):
`token`(String, обяз.), `expires_in`(Int, обяз.).

`AuthRefreshTokenErrorDto` (`SRC C0494e.java`), 3 обязательных ключа:
`index`(Int), `code`(Int), `description`(String).

**Источник:** `SRC C18301e.java:176-195`; ответ — `SRC C0199e.java`, `SRC C7946e.java`,
`SRC C0494e.java`, `SRC C8860e.java`.
**Уверенность:** подтверждено.

**Сравнение с LMG-VK** (`app/src/main/kotlin/com/lmg/vk/network/VkAuthApi.kt`):
**расхождений в параметрах нет** — имена, значения и порядок совпадают дословно.
Отличия только в ответе: LMG-VK не моделирует `webview_access_token`,
`webview_refresh_token`, `silent_token` (все опциональны — безопасно) и даёт `success`/`errors`
дефолт `emptyList()` вместо обязательности (мягче оригинала — безопасно).

## 2.8. `ecosystem.checkOtp`

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `sid` | String | да | — |
| `code` | String | да | — |
| `verification_method` | String | да | — | одно из `callreset`, `codegen`, `email`, `push`, `reserve_code`, `sms` (switch по ordinal) |
| `flow_type` | String | да | `"tg_flow"` |
| `sak_version` | String | да | `"1.142"` |

**Ответ:** `EcosystemCheckOtpResponseDto` (`SRC C15175e.java`, сериализатор `SRC C15054e.java`),
9 ключей:

| Ключ | Тип | Обяз. |
|---|---|---|
| `sid` | String | **да** |
| `profile_exist` | Boolean | **да** |
| `profile` | object? | нет |
| `can_skip_password` | Boolean? | нет |
| `next_step` | enum? | нет |
| `signup_restriction_reason` | String? | нет |
| `signup_fields` | List? | нет |
| `signup_fields_values` | object? | нет |
| `signup_params` | object? | нет |

Внутренности `profile` / `signup_*` и wire-имена enum `next_step` (`EnumC14970e`) —
**не найдено в доках** (не разбирал).

**Источник:** `SRC C6626e.java:1461-1490`; ответ — `SRC C15054e.java:9-22`, `SRC C15175e.java:37-64`,
`P4:91-99`.
**Уверенность:** параметры — подтверждено; ответ — частично (вложенные объекты не раскрыты).

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:546-563`): **нет `flow_type` и `sak_version`**,
зато есть лишний `access_token` параметром и подстановка `codegen` при пустом методе.

## 2.9. `ecosystem.getVerificationMethods`

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `sid` | String | да | — |
| `flow_type` | String | да | `"tg_flow"` |
| `sak_version` | String | да | `"1.142"` |

**Ответ:** `EcosystemGetVerificationMethodsResponseDto` (`SRC C3118e.java`,
сериализатор `SRC C16606e.java`), 1 опциональный ключ `methods: List<EcosystemVerificationMethodDto>?`.

`EcosystemVerificationMethodDto` (`SRC C6064e.java`, сериализатор `SRC C17830e.java`),
5 ключей, все опциональны:
`name`(enum `AuthValidateAccountSupportedWaysDto`?), `priority`(Int?), `timeout`(Int?),
`info`(String?), `can_fallback`(Boolean?).

**Источник:** `SRC C15238e.java:370-390`; ответ — `SRC C17830e.java:9-16`, `SRC C6064e.java`, `P4:101-109`.
**Уверенность:** подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:581-596`): **нет `flow_type` и `sak_version`**,
есть лишний `access_token`.

## 2.10-2.13. `ecosystem.sendOtpSms` / `sendOtpEmail` / `sendOtpPush` / `sendOtpCallReset`

Все четыре — **идентичные** по параметрам:

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `sid` | String | да | — |
| `flow_type` | String | да | `"tg_flow"` |
| `sak_version` | String | да | `"1.142"` |

**Ответ:** `EcosystemSendOtpResponseDto` (`SRC C0884e.java`, сериализатор `SRC C12349e.java`),
4 ключа, **все обязательны**:
`status`(Int), `sid`(String), `code_length`(Int), `info`(String).

**Источник:** `SRC AbstractC3062e.java:50-55` (Push), `:249-255` (Sms), `:314-320` (Email),
`:358-365` (CallReset); ответ — `SRC C12349e.java:9-13`, `SRC C0884e.java:12-24`,
`P1:51` / `P1:298` (`ecosystem.sendOtp* → RawVkResponse<C0884e>`), `P1:309` (`C0884e = EcosystemOtpDto`).
**Уверенность:** подтверждено.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:565-579`): **нет `flow_type` и `sak_version`**,
есть лишний `access_token`. DTO (`Priority3Dtos.kt:17`) совпадает.

## 2.14. `get_anonym_token`

Путь: **`/oauth/get_anonym_token`** (`c5577e.license = true`). POST.

| Имя | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `client_id` | Int | да | `2274003` |
| `client_secret` | String | да | `hHbZxrka2uZ6jB1inYsH` |

Больше ничего. Параметра `app_id` **в доках нет**.

**Ответ:** `AnonymTokenResponseDto` (`SRC C7862e.java`, сериализатор `SRC C2870e.java`),
FQN `…objects.auth.AnonymTokenResponseDto`, 2 опциональных ключа:
`token`(String, дефолт `""`), `expired_at`(Int, дефолт `0`).
Место вызова возвращает только `token` (`return ((C7862e) …).ad`).

**Пробел:** класс `C17212e` в выгрузке `src-deobf/` отсутствует (частичная экстракция),
поэтому единственный источник фрагмента запроса — `EP:1020-1035`.
Наличие/отсутствие конверта `{"response": …}` у этого ответа **не подтверждено**.

**Источник:** `EP:1020-1035` (`C17212e.java:548`), `P1:271-278`, `P3:59-76`, `P3:186`;
ответ — `SRC C2870e.java:9-12`, `SRC C7862e.java`, `P1:309`.
**Уверенность:** параметры — подтверждено; конверт ответа — частично.

**Расхождение с LMG-VK** (`VkMethodsRegistry.kt:620-633`): лишний параметр `app_id`
(в доках отсутствует), задан `userAgent`.

## 2.15. `token` (уже реализован в LMG-VK)

Путь: **`api.<domain>/oauth/token`**, метод **POST**.

Порядок параметров дословно (`SRC C14914e.java:356-379`):

| # | Имя | Тип | Обяз. | Значение |
|---|---|---|---|---|
| 1 | `libverify_support` | Bool→`"1"` | да | **`true`** |
| 2 | `scope` | String | да | `"all"` |
| 3 | `device_trusted_hash_support` | Bool→`"1"` | да | `true` |
| 4 | `sid` | String | нет | из аргумента; при `null` не отправляется |
| 5 | `grant_type` | String | да | см. ниже |
| 6 | `username` | String | нет | аргумент |
| 7 | `password` | String | нет | аргумент |
| 8 | `2fa_supported` | Bool→`"1"` | да | `true` |
| 9 | `supported_ways` | String | да | `"push,email"` |
| 10 | `anonymous_token` | String | нет | аргумент |
| 11 | `code` | String | нет | аргумент (код 2FA) |
| 12 | `client_id` | Int | да | `2274003` |
| 13 | `client_secret` | String | да | `hHbZxrka2uZ6jB1inYsH` |
| 14 | `flow_type` | String | да | `"tg_flow"` |
| 15 | `sak_version` | String | да | `"1.142"` |
| 16 | *extra* | Map | нет | `billing.putAll(extra)` — кладётся **последним**, может перетереть предыдущие |

`grant_type` (`SRC C14914e.java:344-345`):
```
password.length() == 0 -> "without_password"
иначе z == true        -> "phone_confirmation_sid"
иначе                  -> "password"
```

Параметров `vk_connect_auth` и `app_id` в доках **нет**.

**Ответ:** sealed `RequestTokenResponse` — раздел 3.

**Источник:** `SRC C14914e.java:344-380`, `EP:1314-1350` (`C14914e.java:222` и `:441`), `P1:281-283`.
**Уверенность:** подтверждено.

**Расхождения с LMG-VK** (`VkMethodsRegistry.kt:636-662`) — их шесть:

1. `endpoint = VkEndpoint.OAUTH` (отдельный хост) вместо `api.<domain>/oauth/`;
2. `httpMethod = GET` вместо POST;
3. отсутствуют `device_trusted_hash_support`, `supported_ways`, `flow_type`, `sak_version`;
4. `libverify_support` передаётся **`false`**, в VK X — `true` (полярность перевёрнута);
5. добавлен `vk_connect_auth = true`, которого в доках нет;
6. `grant_type` приходит аргументом, а не вычисляется по длине пароля.

---

# 3. Sealed `RequestTokenResponse`

FQN базы: `bruhcollective.itaysonlab.vkapi.objects.auth.RequestTokenResponse`
(интерфейс `InterfaceC8399e`). Веток — **шесть**, ветки `Processing` в VK X **нет**.

## 3.1. Дискриминатор — дословно

Сериализатор — `JsonContentPolymorphicSerializer<RequestTokenResponse>`,
`SRC C16803e.java:58-107`. Логика выбора (переписана из декомпилята 1:1):

```
val e = element.jsonObject["error"]
when {
    e == null                              -> Success            // C1479e
    e is JsonObject                        -> NestedApiError     // C15748e
    e.jsonPrimitive.content == "need_validation" -> TwoFactorRequired // C11209e
    e.jsonPrimitive.content == "need_captcha"    -> CaptchaRequired   // C11002e
    e.jsonPrimitive.content == "invalid_client"  -> ClientError       // C0535e
    else                                   -> UnknownError       // C11172e
}
```

В декомпиляте сравнения идут по `hashCode()`; все три значения проверены:
`"need_validation"` = `304348098` (`C16803e.java:81-82`),
`"need_captcha"` = `96713681` (`:79, :86`),
`"invalid_client"` = `-632018157` (`:78, :92`).

**Уверенность:** подтверждено (полностью, включая порядок проверок).

## 3.2. Про конверт

Дискриминатор читает `error` **на верхнем уровне тела ответа**. Если бы `token` заворачивался
в `RawVkResponse{response, error}`, то ключ `error` был бы объектом `VkErrorDetails`, и ветка
со строковым `"need_validation"` была бы недостижима. Значит ответ `token` разбирается
**плоско**, а `C11464e` собирается однoаргументным конструктором `C11464e(data)`
(он есть: `SRC C11464e.java:25-28`). Ветка `NestedApiError` — это как раз случай, когда VK
вернул на верхнем уровне `{"error": {"error_code": …}}`.

**Уверенность:** частично — вывод логический, тело парсера `C15802e.this(...)` не декомпилируется
(`SRC C15802e.java:691` — CFR: «Decompilation failed»).

## 3.3. Ветки и их поля

Все поля ниже — из сериализаторов kotlinx (`advert(key, isOptional)`) и конструкторов DTO.

### `Success` — `SRC C1479e.java`, сериализатор `SRC C18509e.java`

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `user_id` | Long | нет | `0` |
| `access_token` | String | нет | `""` |
| `expires_in` | Int | нет | `0` |
| `trusted_hash` | String | нет | `""` |

Признак: ключа `error` в ответе **нет**.

### `NestedApiError` — `SRC C15748e.java`, сериализатор `SRC C10278e.java`

| Ключ | Тип | Обяз. |
|---|---|---|
| `error` | `VkErrorDetails` | **да** |

`VkErrorDetails` — `SRC C8733e.java` (см. 1.5).
Признак: `error` — JSON-**объект**.

### `TwoFactorRequired` — `SRC C11209e.java`, сериализатор `SRC C2600e.java`

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `validation_type` | enum `ValidationType` | **да** | — |
| `phone_mask` | String | нет | `""` |
| `masked_email` | String | нет | `""` |
| `code_length` | Int | нет | **`4`** |
| `device_name` | String | нет | `""` |
| `validation_sid` | String | нет | `""` |

`ValidationType` — `SRC EnumC16168e.java`, FQN `bruhcollective.itaysonlab.vkshared.ValidationType`,
7 значений, wire-имена (`SRC C13117e.java:37`):
`2fa_sms`, `2fa_push`, `2fa_email`, `2fa_app`, `2fa_libverify`, `2fa_callreset`,
и седьмое — **`null` в массиве имён**, то есть serial name = имя константы `ReserveCode`.

Признак: `error == "need_validation"`.

### `CaptchaRequired` — `SRC C11002e.java`, сериализатор `SRC C8196e.java`

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `captcha_sid` | String | нет | `""` |
| `captcha_img` | String | нет | `""` |
| `captcha_ts` | Double | нет | `0.0` |
| `captcha_ratio` | Double | нет | `0.0` |
| `captcha_attempt` | Int | нет | `0` |
| `redirect_uri` | String | нет | `""` |

Признак: `error == "need_captcha"`.

### `ClientError` — `SRC C0535e.java`, сериализатор `SRC C9836e.java`

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `error` | String | нет | `""` |
| `error_description` | String | нет | `""` |
| `error_type` | String | нет | `""` |

Признак: `error == "invalid_client"`.

### `UnknownError` — `SRC C11172e.java`, сериализатор `SRC C11971e.java`

| Ключ | Тип | Обяз. | Дефолт |
|---|---|---|---|
| `error` | String | нет | `""` |
| `error_description` | String | нет | `""` |

Признак: `error` — строка, не совпавшая ни с одним из трёх литералов.

**Источник (весь 3.3):** `P4:121-146` + дословные дескрипторы
`SRC C18509e.java`, `SRC C10278e.java`, `SRC C2600e.java`, `SRC C8196e.java`,
`SRC C9836e.java`, `SRC C11971e.java` и конструкторы соответствующих DTO.
**Уверенность:** подтверждено.

## 3.4. Как выразить это в Kotlin с Moshi

У Moshi нет автоматических адаптеров для sealed-иерархий, а
`PolymorphicJsonAdapterFactory` здесь не подходит: он требует **одного** поля-дискриминатора
с разными значениями-метками, а тут дискриминация идёт и по *отсутствию* ключа, и по его
*JSON-типу* (строка vs объект).

Рабочий подход — ровно тот же, что уже принят в проекте (`VkResponseParser` + KSP-адаптеры
для веток), но с дискриминатором **по алгоритму 3.1**, а не по эвристике «какие поля есть».
Ветки остаются обычными `@JsonClass(generateAdapter = true) data class`,
дискриминатор — руками, один раз, в парсере:

```kotlin
private object RequestTokenParser : VkResponseParser<RequestTokenResponse> {
    override suspend fun parse(raw: RawHttpResponse): VkParsedResponse<RequestTokenResponse> {
        val body = raw.bodyText()
        val json = JSONObject(body)                     // плоский JSON, без конверта
        val error: Any? = if (json.isNull("error")) null else json.opt("error")

        val type: Class<out RequestTokenResponse> = when {
            error == null           -> RequestTokenResponse.Success::class.java
            error is JSONObject     -> RequestTokenResponse.NestedApiError::class.java
            else -> when (error.toString()) {
                "need_validation"   -> RequestTokenResponse.TwoFactorRequired::class.java
                "need_captcha"      -> RequestTokenResponse.CaptchaRequired::class.java
                "invalid_client"    -> RequestTokenResponse.ClientError::class.java
                else                -> RequestTokenResponse.UnknownError::class.java
            }
        }
        val data = requireNotNull(VkJson.moshi.adapter(type).fromJson(body)) {
            "Empty OAuth token response from ${raw.url}"
        }
        return VkParsedResponse(data, null)
    }
}
```

Два момента, которые легко потерять:

- **`json.has("error")` недостаточно.** Нужно отличать отсутствие ключа от `null`;
  в kotlinx `element["error"]` даёт `JsonNull` для явного `null`, и такой ответ уйдёт в
  `UnknownError`, а не в `Success`. Если хочется побайтовой совместимости с VK X —
  `json.opt("error")` при `"error": null` вернёт `JSONObject.NULL`, что **не** `null`;
  поэтому проверка через `isNull(...)` даст `Success`, а VK X — `UnknownError`.
  Расхождение в теории; на практике VK не присылает `"error": null`.
- **Тело парсится целиком, а не только вложенный объект** — `NestedApiError` имеет
  поле `error: VkErrorDetails`, и адаптер сам его прочитает из `body`.

Для `validation_type` — обычный Moshi enum с `@Json(name = "2fa_sms")` и т.д.;
седьмую константу (`ReserveCode`) **не аннотировать**, у неё serial name = имя константы.
Дефолт `code_length = 4` задавать в конструкторе (Moshi + KSP уважает дефолты Kotlin).

---

# 4. Порядок вызовов во флоу

Восстановлено из `SRC C8221e.java` (`ad(cont)`, разрешение токена),
`SRC C14914e.java` (`Signature(AppActivity, sid, username, password, code, z, extra, cont)`),
`P1:281-283`, `SRC C6626e.java`, `SRC AbstractC3062e.java`, `SRC C16803e.java`.

## 4.1. Основной путь (вход по логину без пароля / VK ID OTP)

```
[0] get_anonym_token                       /oauth/, client_id + client_secret
      │  → token (String), expired_at
      │  далее весь auth-флоу идёт с Authorization: Bearer <этот token>
      ▼
[1] auth.validateAccount                   login, force_password=0, passkey_supported=0,
      │                                    supported_ways, flow_type=auth_without_password,
      │                                    sak_version=1.112
      │  → sid, flow_name / flow_names, next_step{verification_method, service_code}
      │
      ├── flow_name == need_password  ──────────────────────────────► [4] token (grant_type=password)
      │
      ├── flow_name == need_validation / next_step.verification_method задан
      │        │
      │        ├─(опц.)─► [2a] ecosystem.getVerificationMethods    sid, flow_type=tg_flow,
      │        │                                                   sak_version=1.142
      │        │              → methods[]{name, priority, timeout, info, can_fallback}
      │        │
      │        ├─────────► [2b] ecosystem.sendOtpSms | sendOtpEmail | sendOtpPush |
      │        │                sendOtpCallReset                    sid, flow_type=tg_flow,
      │        │                                                    sak_version=1.142
      │        │              → status, sid, code_length, info      (sid может обновиться!)
      │        │
      │        └─────────► [3] ecosystem.checkOtp                   sid, code,
      │                          verification_method, flow_type=tg_flow, sak_version=1.142
      │                       → sid, profile_exist, next_step, can_skip_password
      │                          (sid для шага [4] берётся отсюда)
      ▼
[4] token                                  /oauth/token, POST
      │                                    grant_type = without_password |
      │                                                 phone_confirmation_sid | password
      │                                    + sid, username, password, code, anonymous_token,
      │                                      libverify_support=1, scope=all,
      │                                      device_trusted_hash_support=1, 2fa_supported=1,
      │                                      supported_ways=push,email, client_id,
      │                                      client_secret, flow_type=tg_flow, sak_version=1.142
      │
      ├── нет ключа "error"          → Success{user_id, access_token, expires_in, trusted_hash}
      │                                                                        ─────► [5]
      ├── error == "need_validation" → TwoFactorRequired{validation_type, validation_sid,
      │                                 code_length, phone_mask, masked_email, device_name}
      │                                 └─► показать ввод кода → повтор [4] с code (+ sid)
      │
      ├── error == "need_captcha"    → CaptchaRequired{captcha_sid, captcha_img, captcha_ts,
      │                                 captcha_ratio, captcha_attempt, redirect_uri}
      │                                 └─► повтор [4] с extra = {captcha_sid, captcha_key,
      │                                     captcha_ts, captcha_attempt}   ← имя captcha_key
      │                                     в доках НЕ подтверждено (см. Пробелы)
      │
      ├── error == "invalid_client"  → ClientError{error, error_description, error_type} → стоп
      ├── error — объект             → NestedApiError{error: VkErrorDetails} → маппер C15802e
      └── иное                       → UnknownError{error, error_description} → стоп
      ▼
[5] auth.getExchangeToken                  create_common_token=1, create_tier_tokens=0,
      │                                    v=5.180; Bearer = свежий access_token
      │  → users_exchange_tokens[]{user_id, profile_type, common_token, tier_tokens}
      ▼
[6] сессия установлена
```

**Уверенность:** шаги [0], [1], [2a], [2b], [3], [4], [5] и все ветвления после [4] —
подтверждено. Условия перехода `[1] → [2b]` vs `[1] → [4]` (то есть какое именно значение
`flow_name` / `next_step` ведёт в какую ветку) — **частично**: дословный switch, отображающий
`flow_name` в решение, в доках не найден; порядок восстановлен по составу параметров
(`sak_version=1.112` + `flow_type=auth_without_password` у `validateAccount` против
`tg_flow` + `1.142` у `ecosystem.*`) и по `P4:169`
(«флоу `ecosystem.sendOtpSms` → `ecosystem.checkOtp` → `ecosystem.getVerificationMethods`»).
Порядок в `P4:169` **противоречит** восстановленному: `getVerificationMethods` там стоит
последним. Дословных фрагментов, фиксирующих очерёдность, нет ни там, ни в `src-deobf`.

## 4.2. Фоновое обновление токена (не часть логина)

```
любой /method/ вызов без явного access_token и с userId != 0
   └─► auth.getExchangeToken (Bearer = сохранённый exchange-токен)
         └─► auth.refreshTokens (client_id, client_secret, exchange_tokens,
                                 active_index=0, scope=all, initiator=expired_token)
               → success[]{index, user_id, banned, access_token{token, expires_in}}
                 errors[]{index, code, description}
```

`SRC C8221e.java` (`ad(cont)`: `C17212e(this, null, 12)` при `userId == 0`,
иначе `C0593e(…, 10)` → `C18301e(false, …)` → `C18479e.vip`).
**Уверенность:** подтверждено.

## 4.3. Вход по коду с другого устройства (auth code)

```
auth.processAuthCode(action, auth_code)            → status, auth_info{auth_id, client_info,
                                                     device_info, domain, expires_in, flow_type}
auth.setAuthCodeStatus(auth_code, access_token)    → status, expires_in, polling_delay,
                                                     faq_url, domain
auth.processAuthCodeMulti(action=0, auth_code,
                          access_token, access_tokens) → тот же AuthProcessAuthCodeResponseDto
```

Это **отдельная ветка**, не связанная с [0]-[6]. Точный порядок вызовов, роль `polling_delay`
и значения `action` — **не найдено в доках**.

## 4.4. `auth.validatePhone`

Метод восстановлен полностью (2.2), но **место его вызова во флоу в доках не найдено**.
По составу параметров (`sid`, `supported_ways=push,email`, без `flow_type`/`sak_version`)
это старый, не-VK-ID путь подтверждения телефона, параллельный `ecosystem.*`, а не шаг
основного флоу. Утверждать это как факт нельзя — **не найдено в доках**.

---

# 5. Пробелы

Чего в доках нет — перечислено без догадок.

1. **`auth.processAuthCode` → `action`**: набор допустимых значений. Значение приходит
   параметром конструктора лямбды `C6046e(C2347e, String, int, cont)`; поиск вызывающих мест
   по 718 МБ `src-deobf/` не укладывается в лимит времени. У `processAuthCodeMulti` значение
   зашито литералом `0`, у `processAuthCode` — нет.
2. **`supported_ways` в `auth.validateAccount`, индекс 4**: в декомпиляте потерян
   (`var25_68`). `password` — вывод по исключению из десяти значений enum, не фрагмент кода.
3. **`ServiceCodeDto`, первое из двух значений**: в `SRC C14582e.java:274-281` массив имён
   собран как `new String[]{<переиспользованная локальная строка>, "2"}`. По контексту `"1"`,
   дословно — нет.
4. **`get_anonym_token`**: класс `C17212e` отсутствует в выгрузке `src-deobf/`; единственный
   источник — фрагмент `EP:1020-1035`. Заворачивается ли ответ в `{"response": …}` —
   не подтверждено.
5. **Конверт ответа `token`**: тело парсера `C15802e.this(...)` не декомпилируется
   (`SRC C15802e.java:691`, CFR «Decompilation failed»). Плоский разбор — логический вывод
   из дискриминатора (3.2), не прочитанный код.
6. **Вторая форма ответа `auth.getExchangeToken`**: три из четырёх мест вызова используют
   парсер `C5107e.f10956e` (переменная `c7862e`), четвёртое — `C12575e.f25225e` (`c2654e`).
   Восстановлена только форма второго. `P3:185` называет первую «`C7862e ExchangeTokenResponse`»,
   но `C7862e` по `P1:309` — это `AnonymTokenResponseDto`. Противоречие не разрешено.
7. **Имена параметров повторного запроса `token` после капчи**: `CaptchaRequired` несёт
   `captcha_sid` / `captcha_ts` / `captcha_attempt` / `captcha_img` / `captcha_ratio`, но
   как эти значения (и введённый пользователем текст) кладутся в `extra` при повторе — в доках
   **нет**. Название `captcha_key` — общеизвестное имя параметра VK API, а **не** подтверждённая
   строка из этого декомпилята.
8. **Вложенные объекты**, не раскрытые ни в доках, ни мной:
   `EcosystemCheckOtpResponseDto.profile` / `signup_fields_values` / `signup_params` и
   wire-имена enum `next_step` (`EnumC14970e`);
   `AuthProcessAuthCodeResponseDto.profile` / `errors[]`;
   `AuthCodeAuthInfoDto.client_info` / `device_info`;
   `AuthUserExchangeTokenDto.profile_type` / `tier_tokens[]`.
9. **Очерёдность `ecosystem.getVerificationMethods` относительно `sendOtp*` / `checkOtp`**:
   `P4:169` даёт один порядок, состав параметров подсказывает другой; дословного фрагмента,
   фиксирующего очерёдность, нет.
10. **Условие ветвления после `auth.validateAccount`** (какое `flow_name` ведёт в OTP,
    а какое — сразу в `token`): switch в доках не найден.
11. **Место `auth.validatePhone` во флоу** — см. 4.4.
12. **`User-Agent`**: локальный аргумент метода равен `null`, но общий Ktor-плагин
    подставляет native bundle slot 13 (`C13651l` → `C1483l`, case 22 → `C2269l`).
13. **`lang`**: белый список из 5 языковых кодов (`AbstractC4533e.vip`) — сами коды не прочитаны.

---

# 6. Риски для существующего входа в LMG-VK

Флоу входа сейчас работает (`app/src/main/kotlin/com/lmg/vk/engine/backend/MusicBackend.kt`,
`object MusicAuth`, строки ~2054 и ~2180-2360:
`getAnonymousToken → validateAccount → prepareTwoFactor → ecosystemSendOtp → oauthToken →
branch → finishSignIn(getUserExchangeTokens → installSession)`).
Порядок вызовов совпадает с восстановленным. Ниже — что сломается, если переносить
спецификацию механически.

## 6.1. Высокий риск — сломает вход молча

1. **`token`: смена хоста/метода одним движением.**
   Сейчас `endpoint = VkEndpoint.OAUTH` + `httpMethod = GET`; в спеке —
   `api.<domain>/oauth/token` + POST. Это две независимые правки в одном методе; сделать
   только одну (например, перевести на POST, оставив хост `oauth.<domain>`) — вероятный
   403/пустой ответ **без** структурированной ошибки. Менять только вместе и только с живой
   проверкой входа.

2. **`VkApiClient.rawCall` не добавляет `api_id`.**
   Транспорт VK X добавляет `api_id=2274003` **всем** запросам (1.3). Добавление `api_id`
   глобально затронет все уже работающие музыкальные вызовы, а не только auth. Если добавлять —
   отдельным изменением, не в составе auth-правок.

3. **`hasStructuredOAuthError` в `VkApiClient.kt` привязан к `endpoint == OAUTH && name == "token"`.**
   Перевод `token` на `API_OAUTH` (пункт 1) **обнулит это условие**: HTTP-ошибка снова начнёт
   короткозамыкать на `C7220e`-аналог, и ветки `TwoFactorRequired` / `CaptchaRequired`,
   приходящие с не-2xx статусом, перестанут разбираться. Условие надо перевести на
   «`isOAuth` (путь `/oauth/`)», как в `C8221e` (1.5), одновременно с пунктом 1.

4. **`libverify_support` у `token`: полярность перевёрнута.**
   В LMG-VK `false`, в VK X `true`. Смена значения меняет набор способов подтверждения,
   которые VK предложит, — то есть может переключить ответ с `Success` на `TwoFactorRequired`
   с `validation_type = 2fa_libverify`, который UI, скорее всего, не обрабатывает.
   Менять только вместе с проверкой ветки libverify в UI.

5. **`RequestTokenParser` дискриминирует не по тому признаку.**
   Текущая логика (`VkMethodsRegistry.kt:698-727`) смотрит на `processing`,
   `error.error_code == 14`, наличие `validation_type` / `captcha_sid` / `access_token` /
   `error_type`. Подтверждённый алгоритм — по значению `error` (3.1). Расхождения, которые
   выстрелят:
   - `error_code == 14` уводит nested-ошибку в `CaptchaRequired` и парсит **вложенный объект**
     вместо тела; по VK X это `NestedApiError` с полным `VkErrorDetails`
     (у которого капча-поля есть внутри) — при переходе на правильный дискриминатор
     код, читающий `CaptchaRequired` в этом сценарии, перестанет получать данные;
   - `json.has("access_token") → Success` срабатывает раньше проверки `error`, а по VK X
     `Success` определяется **отсутствием** `error`;
   - ветка `Processing` (см. 6.2).
   Заменять дискриминатор — только вместе с обновлением всех `when`-ов по
   `RequestTokenResponse` в `MusicAuth`.

6. **`data object Processing` — ветки, которой нет в VK X.**
   `SRC C16803e.java` не содержит ни `Processing`, ни проверки ключа `processing`. Если
   удалить ветку из sealed-иерархии, перестанут компилироваться `when`-и в `MusicAuth`;
   если оставить, но убрать проверку `json.has("processing")` из парсера — ветка станет
   недостижимой, и ответ, который сейчас распознаётся как «в обработке», уйдёт в
   `UnknownError` и оборвёт вход. Откуда взялась ветка — неизвестно; **не удалять без
   выяснения, откуда пришло требование** (возможно, реальное поведение VK, не покрытое доками).

7. **Добавление `flow_type` / `sak_version` в `ecosystem.*` меняет серверный флоу.**
   Сейчас четыре метода (`checkOtp`, `getVerificationMethods`, `sendOtp*`) идут **без**
   `flow_type=tg_flow` и `sak_version=1.142`, и вход работает. `flow_type` — это выбор
   серверного сценария VK ID; добавление его может изменить и `sid`, и набор
   `verification_method`, и требования к следующему шагу. Это не «дописать забытый параметр»,
   а смена флоу. Добавлять все четыре метода одним батчем и с проверкой живого входа,
   иначе половина флоу окажется в `tg_flow`, а половина — нет.

## 6.2. Средний риск — сломает нестандартные ветки

8. **`access_token` параметром vs `Bearer`.**
   LMG-VK передаёт `anonymousToken` как form-параметр `access_token` в `validateAccount`,
   `ecosystem.*`. VK X полагается на `Bearer` (1.4). Убирать параметр **опасно**: подстановка
   в `Bearer` в LMG-VK устроена иначе, и без параметра запросы могут уйти неавторизованными.
   Заметим: в VK X при явном `access_token` он **и остаётся в теле, и идёт в `Bearer`** — то
   есть текущее поведение LMG-VK не противоречит транспорту, просто избыточно. Трогать без
   нужды не стоит.

9. **`accounts_trusted_hashes` в `validateAccount` и `vk_connect_auth` / `app_id`
   (`token`, `get_anonym_token`) — параметров нет в доках.**
   Это не значит, что их надо удалить: они могут быть подсмотрены из другой версии клиента и
   работать. Удаление — риск потерять «доверенное устройство» (пропуск 2FA) без видимой ошибки.
   Помечать как неподтверждённые, но не выпиливать наугад.

10. **`getExchangeToken()` разбирает ответ чужим DTO.**
    `VkMethodsRegistry.kt:485-496` парсит `auth.getExchangeToken` в
    `AnonymTokenResponse{token, expired_at}`. Подтверждённая форма —
    `{users_exchange_tokens: [...]}`. Метод, судя по всему, не используется в живом флоу
    (в `MusicAuth` вызывается `getUserExchangeTokens`), но при попытке «починить» его надо
    сначала найти вызывающие места — иначе можно сломать то, что молча работало на пустом
    результате.

11. **`getUserExchangeTokens()` не ставит `v = 5.180`.**
    Три из четырёх мест вызова в VK X ставят. Добавление версии API меняет форму ответа
    у VK в принципе; менять — только с проверкой, что `users_exchange_tokens` всё ещё приходит.

12. **`setAuthCodeStatus` теряет ответ.**
    Сейчас `Unit`. Подтверждённый ответ несёт `polling_delay` и `domain`. Само по себе
    безопасно, но если появится поллинг — без `polling_delay` он будет с выдуманным интервалом.

13. **`processAuthCodeMulti` не передаёт `access_token`.**
    В VK X он обязателен и идёт **и** параметром, **и** в `Bearer`. Добавление — низкий риск;
    отсутствие — вероятная причина неработающего входа по коду, если эта ветка вообще
    используется.

## 6.3. Низкий риск

14. **`User-Agent`.** Auth-клиент VK X получает UA из native bundle slot 13 через общий
    Ktor-плагин. Для auth-запросов LMG-VK должен сохранять `VkUserAgents.auth`; его удаление
    меняет серверную классификацию клиента.

15. **Недостающие опциональные поля в DTO** (`webview_access_token`, `webview_refresh_token`,
    `silent_token` в `AuthRefreshTokenDto`) — добавление безопасно, отсутствие безопасно.

16. **`auth.refreshTokens` — расхождений в параметрах нет.** Единственный метод группы,
    который можно не трогать вообще: имена, значения и порядок совпадают с
    `SRC C18301e.java:176-195` дословно.

## 6.4. Порядок безопасного переноса

Если менять — то батчами, в которых каждый батч оставляет вход рабочим:

1. Чисто аддитивное, без изменения запросов: DTO `setAuthCodeStatus`, недостающие
   опциональные поля, `access_token` в `processAuthCodeMulti`.
2. Дискриминатор `RequestTokenResponse` по 3.1 + судьба ветки `Processing`
   (одним изменением, вместе с `when`-ами в `MusicAuth`).
3. `token`: хост + метод + `hasStructuredOAuthError` + недостающие параметры + полярность
   `libverify_support` — **одним** батчем, с живой проверкой входа.
4. `flow_type` / `sak_version` в `ecosystem.*` — отдельным батчем, все четыре метода сразу.
5. `api_id` в транспорте — последним и отдельно, потому что затрагивает всё приложение.

Пункты 3-5 без возможности проверить вход на реальном аккаунте лучше не выполнять:
все три ломаются молча.
