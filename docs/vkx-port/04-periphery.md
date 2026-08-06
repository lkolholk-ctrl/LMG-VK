# 04 — Периферийные методы VK API (группа 4): stats, storage, utils, year-stats, execute

Спецификация для порта в LMG-VK. Всё извлечено из реверс-инжиниринговых документов и
декомпилированных исходников VK X 8.12.1. Где подтверждения нет — так и написано.

## Условные обозначения источников

| Сокращение | Путь |
|---|---|
| `EP` | `/storage/emulated/0/Download/VKLMG_Recovery/VKX-ENDPOINTS.md` |
| `P3` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY3-RECOVERY.md` |
| `FRESH` | `/storage/emulated/0/Download/VKX_Fresh/jadx_out/sources/defpackage/<class>.java` |

Уверенность: **подтверждено** — есть дословный фрагмент кода; **частично** — фрагмент есть,
но покрывает не всё; **не найдено в доках** — догадок нет.

### Важно про корпус исходников

`/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/` — **неполный**: например
`src-deobf/C4673e.java` это 76-строчная заглушка с `throw new IllegalStateException("Decompilation
failed")`, а грепы по `storage.set` / `musicStatResults.createPlaylist` там ничего не находят.
Реальный корпус, по которому генерировались `EP`/`P1`..`P4`, указан в
`/storage/emulated/0/Download/VKLMG_Recovery/gen_md.py:5`:

```python
SRC = "/sdcard/Download/VKX_Fresh/jadx_out/sources/defpackage"
```

Поэтому все номера строк ниже даны по `FRESH`. Номера вида `C4673e.java.java:217` в `EP` — это
те же строки того же файла (в `EP` продублировано расширение).

## Как читать фрагменты запросов (`C5577e` = аналог `VkMethod`)

| Вызов в декомпиляте | Что значит | Эквивалент LMG-VK |
|---|---|---|
| `.ad("k", str)` | строковый параметр; если `str == null`, параметр не добавляется | `param("k", str)` |
| `.vip(n, "k")` | `Int` → `String.valueOf(n)` | `param("k", n: Int)` |
| `.metrica(l, "k")` | `Long` → `String.valueOf(l)` | `param("k", l: Long)` |
| `.license("k", z)` | `Boolean` → `"1"`/`"0"` | `param("k", z: Boolean)` |
| `.appmetrica = true` | «one-shot»: тело ответа не разбирается | нет аналога, см. §3 |

Версия API — `"5.272"` глобально.

Имена полей DTO восстановлены двумя независимыми способами:
* **JSON-имена** — из kotlinx-дескриптора: `new C4707e("<fqcn>", serializer, N)` +
  `advert("<json_name>", isOptional)`;
* **Kotlin-имена свойств** — из литеральной строки в `toString()` data-класса.

---

## 1. `utils.resolveScreenName`

**Класс-обёртка:** `C18480e` (`P3` не покрывает; `EP §3`).
**Парсер:** `C11047e.f21912e` → `C11047e(15)` → ветка `default`.

Дословно, `FRESH C18480e.java:86-91`:

```java
defpackage.C8221e c8221eVip = defpackage.AbstractC1831e.vip();
defpackage.C5577e c5577e = new defpackage.C5577e("utils.resolveScreenName", defpackage.C11047e.f21912e);
c5577e.ad("screen_name", str);
java.lang.Object objLicense2 = c8221eVip.license(c5577e, c8935e);
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `screen_name` | String | да | — | `FRESH C18480e.java:90` |

Других параметров у вызова нет. **подтверждено**

### Ответ

`FRESH C11047e.java:651+`, ветка `default` (дискриминатор 15):

```java
interfaceC13984eVip = AbstractC3820e.vip(C11464e.class,
    AbstractC16704e.license(AbstractC3820e.ad(defpackage.C0120e.class)));
```

`C0120e` (`FRESH C0120e.java:7-19`), дескриптор `FRESH C6279e.java:13-15`:

```java
new C4707e("bruhcollective.itaysonlab.vkapi.objects.user.ResolvedScreenName", c6279e, 2);
c4707e.advert("object_id", false);
c4707e.advert("type", false);
```

| JSON | Kotlin | Тип | Nullable | Обяз. |
|---|---|---|---|---|
| `object_id` | — (`ad`) | Long | нет | да (маска `3`) |
| `type` | — (`vip`) | String | нет | да (маска `3`) |

Kotlin-имена свойств: у `C0120e` нет `toString()`, поэтому **не найдено в доках**; используем
JSON-имена. Набор значений `type` (`user`/`group`/`application`/…) — **не найдено в доках**.

### DTO для создания

```kotlin
@JsonClass(generateAdapter = true)
data class ResolvedScreenName(
    @Json(name = "object_id") val objectId: Long,
    @Json(name = "type") val type: String,
)
```

### Текущее состояние в LMG-VK

`app/src/main/kotlin/com/lmg/vk/network/methods/VkMethodsRegistry.kt:356-357`:

```kotlin
suspend fun resolveScreenName(screenName: String) =
    execute<Any>("utils.resolveScreenName") { param("screen_name", screenName) }
```

Параметр совпадает с оригиналом; нужно только заменить `Any` на `ResolvedScreenName`
и завести `MoshiEnvelopeParser`.

**Уверенность: подтверждено.**

---

## 2. `storage.get` (сверка) и `storage.set`

### 2.1 `storage.get` — уже реализовано, формы совпадают

Дословно, `FRESH C4271e.java:1216-1223`:

```java
java.util.List listSingletonList = java.util.Collections.singletonList("annual_result_2025_created_playlists_id");
defpackage.C5577e c5577e3 = new defpackage.C5577e("storage.get", defpackage.C10990e.f21760e);
c5577e3.ad("keys", defpackage.AbstractC13480e.m3608try(listSingletonList, ",", null, null, null, 62));
c5577e3.vip(52384530, "app_id");
```

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `keys` | String (join через `","`) | да | — | `FRESH C4271e.java:1222` |
| `app_id` | Int | да | `52384530` (хардкод) | `FRESH C4271e.java:1223` |

Ответ: парсер `C10990e.f21760e` → `C10990e(15)` → ветка `default` (`FRESH C10990e.java:459`):
`List<C4704e>`. `C4704e` = `bruhcollective.itaysonlab.vkapi.objects.storage.StorageGetDto`
(`FRESH C7011e.java:13-15`, `FRESH C4704e.java:38`), поля `key: String`, `value: String`,
оба обязательные (маска `3`), не nullable.

В LMG-VK: `VkMethodsRegistry.kt:360-369` + `StorageParser` (`:766`) +
`VKX_STORAGE_APP_ID = 52384530` (`:777`). **Формы идентичны, правок не требуется.**
Единственное отличие — LMG-VK сворачивает `List<StorageItem>` в `Map<String, String>`;
оригинал отдаёт список наружу и берёт `first()`.

### 2.2 `storage.set`

**Класс-обёртка:** `C4673e`. **Парсер:** `C6114e.f12853e` → `C6114e(15)` → ветка `default`.

Дословно, `FRESH C4673e.java:208-221` (одна из 4 идентичных по форме площадок:
`:217`, `:312`, `:348`, `:384`):

```java
java.lang.String strPurchase = c8028e.purchase(defpackage.C14625e.Companion.serializer(), c14625e);
...
defpackage.C5577e c5577e = new defpackage.C5577e("storage.set", defpackage.C6114e.f12853e);
c5577e.ad("key", "annual_result_2025_created_playlists_id");
c5577e.ad("value", strPurchase);
c5577e.vip(52384530, "app_id");
```

#### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `key` | String | да | — | `FRESH C4673e.java:218` |
| `value` | String | да | — | `FRESH C4673e.java:219` |
| `app_id` | Int | да | `52384530` (хардкод) | `FRESH C4673e.java:220` |

Никаких `user_id`/`global` в вызовах VK X нет. **подтверждено**

#### Ответ

`FRESH C6114e.java:918` — `java.lang.Class cls = java.lang.Integer.TYPE;`, и ветка `default`
(`FRESH C6114e.java:1053`) собирает `RawVkResponse<Int>`. То есть ответ — `response: 1`.
DTO не нужен, достаточно `Unit`/`Int`. **подтверждено**

#### Что кладётся в `value` (полезно для порта Y25-фичи)

`value` — это не произвольная строка, а JSON от `C14625e`
(`FRESH C14625e.java`, дескриптор `FRESH C2838e.java:13-14`):

```java
new C4707e("ua.itaysonlab.vkxreborn.compose_fragments.ny.Ny26V2Screen.JsonStorageValue", c2838e, 1);
c4707e.advert("id", false);
```

`JsonStorageValue(id: Int)`, поле обязательное. То есть в `annual_result_2025_created_playlists_id`
пишется `{"id": <playlist_id>}`. **подтверждено**

#### DTO для создания

```kotlin
@JsonClass(generateAdapter = true)
data class JsonStorageValue(
    @Json(name = "id") val id: Int,
)
```

### Текущее состояние в LMG-VK

`VkMethodsRegistry.kt:371-374`:

```kotlin
suspend fun storageSet(key: String, value: String, appId: Int) =
    executeUnit("storage.set") {
        param("key", key); param("value", value); param("app_id", appId)
    }
```

Набор параметров совпадает с оригиналом. Отличие: у `storageGet` есть
`appId: Int = VKX_STORAGE_APP_ID`, а у `storageSet` дефолта нет — стоит выровнять.

**Уверенность: подтверждено.**

---

## 3. `stats.trackEvents`

**Класс-обёртка:** `C9438e` (`DelayedAnalyticsFacade`, две площадки — `-VK` и `-VKPodcasts`).
**Парсер:** `C5170e.f11062e` = `new C5170e(b, 15)` (`FRESH C9438e.java` и `FRESH C5170e.java:100`).

Дословно, `FRESH C9438e.java:130-141`:

```java
java.util.ArrayList arrayList2 = new java.util.ArrayList();
arrayList2.addAll(arrayList);
defpackage.C7960e c7960e = new defpackage.C7960e(arrayList2);
defpackage.C8221e c8221eVip = defpackage.AbstractC1831e.vip();
defpackage.C11817e c11817e = c9438e.vip;
java.lang.String strPurchase = c11817e.purchase(defpackage.C7960e.Companion.serializer(), c7960e);
defpackage.C5577e c5577e = new defpackage.C5577e("stats.trackEvents", defpackage.C5170e.f11062e);
c5577e.appmetrica = true;
c5577e.ad("events", strPurchase);
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `events` | String — JSON-массив событий | да | — | `FRESH C9438e.java:140` |

`C7960e` (`FRESH C7960e.java:7`) — это `kotlinx.serialization.json.JsonArray`
(`extends AbstractC1948e implements List<AbstractC1948e>`, `@InterfaceC5413e(with = C9533e.class)`),
то есть `events` = сериализованный JSON-массив объектов. **подтверждено**

### Флаг `appmetrica = true` («one-shot»)

`C5577e.appmetrica` по умолчанию `false`, и `true` его выставляет **только** `stats.trackEvents`.
В `C8221e:561` есть строка `"BH.VkApi - One-Shot methods have no content"`; jadx декомпилирует
условие с инверсией (`if (!c5577e.appmetrica) return …`), что семантически противоречит дефолту,
поэтому: значение флага — «ответ не разбирается», а точная форма проверки — **частично**
(ветка перевёрнута декомпилятором).

Формально парсер всё равно зарегистрирован: `C5170e(15)` → ветка `default`
(`FRESH C5170e.java:970`) → `RawVkResponse<Int>`. DTO не нужен.

### Форма одного события (полностью подтверждена)

`FRESH C9438e.java:48-100`, функция сборки события `advert(name, cur, prev, reason)`:

```java
defpackage.AbstractC10681e.purchase(c13935e, "e", str);                       // имя события
defpackage.AbstractC10681e.purchase(c13935e, "audio_id", <owner_id>_<id>);
defpackage.AbstractC10681e.appmetrica(c13935e, "uuid", UUID.randomUUID().hashCode());
defpackage.AbstractC10681e.purchase(c13935e, "reason", str2);
defpackage.AbstractC10681e.appmetrica(c13935e, "start_time", c4532e.metrica);
defpackage.AbstractC10681e.appmetrica(c13935e, "playback_started_at", j);
defpackage.AbstractC10681e.purchase(c13935e, "track_code", str3);            // если не пустой
defpackage.AbstractC10681e.purchase(c13935e, "streaming_type", "online");
defpackage.AbstractC10681e.appmetrica(c13935e, "duration", j);
defpackage.AbstractC10681e.purchase(c13935e, "repeat", "all");
defpackage.AbstractC10681e.purchase(c13935e, "state", "app");
defpackage.AbstractC10681e.purchase(c13935e, "source", …);                   // или "other"
defpackage.AbstractC10681e.purchase(c13935e, "playlist_id", adcel(…));       // если источник — плейлист
defpackage.AbstractC10681e.purchase(c13935e, "prev_playlist_id", adcel(…));  // если был предыдущий
defpackage.AbstractC10681e.purchase(c13935e, "prev_audio_id", strBilling);   // если трек сменился
```

Имена событий (`"e"`), подтверждённые дословно:

| Значение `e` | Где | Источник |
|---|---|---|
| `music_start_playback` | `FRESH C9438e.java:412`, `:441` | подтверждено |
| `music_stop_playback` | `FRESH C9438e.java:385`, `:409` | подтверждено |
| `podcast_play` | `FRESH C9438e.java:163` | подтверждено |

Значения `reason` (`FRESH C9438e.java:198-210`): `"prev"`, `"next"`, `"new"`, а также литералы
`"pause"` (`:385`) и `"continue"` (`:441`).

Поля события для `podcast_play` (`FRESH C9438e.java:163-194`): `e`, `audio_id`, `duration`,
`play_rate` (`1`), `action`, `position`, `position_from`, `track_code`, `ref` (`"episode"`),
`source` (`"icon_button"`).

Вспомогательное: `adcel(str)` (`FRESH C9438e.java:34-45`) обрезает `owner_playlist_access`
до `owner_playlist` (split по `"_"`, только если частей ровно 3).

### DTO для создания

Не нужны: параметр — уже готовая JSON-строка, ответ не разбирается.

### Текущее состояние в LMG-VK

`VkMethodsRegistry.kt:378-379`:

```kotlin
suspend fun statsTrackEvents(eventsJson: String) =
    executeUnit("stats.trackEvents") { param("events", eventsJson) }
```

Форма совпадает с оригиналом.

**Уверенность: подтверждено** (кроме семантики ветки one-shot — частично).

---

## 4. `studio.getArtistYearRecapData`

**Класс-обёртка:** `C4271e`. **Парсер:** `C16628e.f32603e` → `C16628e(15)` → ветка
`case 10/11/default` (`P3 §7` тоже указывает `case 15: studio.getArtistYearRecapData → C4723e`).

Дословно, `FRESH C4271e.java:812-822`:

```java
defpackage.C8221e c8221eVip = defpackage.AbstractC1831e.vip();
java.lang.String str = this.f9363e;
if (str == null) { return kotlin.Unit.INSTANCE; }
...
defpackage.C5577e c5577e = new defpackage.C5577e("studio.getArtistYearRecapData", defpackage.C16628e.f32603e);
c5577e.ad("artist_id", str);
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `artist_id` | String | да | — | `FRESH C4271e.java:821` |

Токен — обычный основной (`AbstractC1831e.vip()`), никакого `access_token` в параметрах нет.
**подтверждено**

### Ответ

`C4723e` = `bruhcollective.itaysonlab.vkapi.objects.audio.AudioGetAnnualResultBlocksDto`
(`FRESH C0596e.java:13-14`, `FRESH C4723e.java:34`):

| JSON | Kotlin | Тип | Nullable | Обяз. |
|---|---|---|---|---|
| `blocks` | `blocks` | `List<AudioGetAnnualResultBlockDto>` | нет | да (маска `1`) |

Элемент: `C1357e` = `…objects.audio.AudioGetAnnualResultBlockDto`, дескриптор
`FRESH C16093e.java:13-33` (20 полей), дефолты — `FRESH C1357e.java:31-127`,
Kotlin-имена — `FRESH C1357e.java:148-178` (`toString`):

| # | JSON | Kotlin | Тип | Обяз. | Дефолт |
|---|---|---|---|---|---|
| 0 | `name` | `name` | String | да | — |
| 1 | `type` | `type` | String | да | — |
| 2 | `order` | `order` | Int | нет | `0` |
| 3 | `is_visible` | `isVisible` | Boolean | нет | `false` |
| 4 | `is_sharing_enabled` | `isSharingEnabled` | Boolean | нет | `false` |
| 5 | `background_url` | `backgroundUrl` | String | нет | `""` |
| 6 | `story_bg` | `storyBg` | String | нет | `""` |
| 7 | `fallback_background_url` | `fallbackBackgroundUrl` | String | нет | `""` |
| 8 | `audio_preview_url` | `audioPreviewUrl` | String | нет | `""` |
| 9 | `titles` | `titles` | `List<Value>` | нет | `[]` |
| 10 | `subtitles` | `subtitles` | `List<Value>` | нет | `[]` |
| 11 | `metrics` | `metrics` | `List<Value>` | нет | `[]` |
| 12 | `photo_urls` | `photoUrls` | `List<String>` | нет | `[]` |
| 13 | `playlist_photo_url` | `playlistPhotoUrl` | String | нет | `""` |
| 14 | `playlist_title` | `playlistTitle` | String | нет | `""` |
| 15 | `playlist_audio_raw_ids` | `playlistAudioRawIds` | `List<String>` | нет | `[]` |
| 16 | `screen_caption` | `screenCaption` | String | нет | `""` |
| 17 | `screen_title` | `screenTitle` | String | нет | `""` |
| 18 | `screen_subtitle` | `screenSubtitle` | String | нет | `""` |
| 19 | `artist` | `artist` | `Value?` | нет | `null` |

Сериализаторы элементов списков: `FRESH C9283e.java` case 26/27/28 → `ListSerializer(C9046e)`
(индексы 9/10/11), case 29 (`default`) → `ListSerializer(String)` (индекс 12);
`FRESH C14561e.java` case 0 → `ListSerializer(String)` (индекс 15). **подтверждено**

Вложенный `Value` = `C2035e`, дескриптор `FRESH C9046e.java:13-20`
(`…AudioGetAnnualResultBlockDto.Value`), дефолты `FRESH C2035e.java:19-53`,
Kotlin-имена `FRESH C2035e.java:72-78`:

| JSON | Kotlin | Тип | Обяз. | Дефолт |
|---|---|---|---|---|
| `title` | `title` | String | нет | `""` |
| `subtitle` | `subtitle` | String | нет | `""` |
| `caption` | `caption` | String | нет | `""` |
| `name` | `name` | String | нет | `""` |
| `value` | `value` | String | нет | `""` |
| `photo_url` | `photoUrl` | String | нет | `""` |
| `photo_urls` | `photoUrls` | `List<String>` | нет | `[]` |

### DTO для создания

```kotlin
@JsonClass(generateAdapter = true)
data class AudioGetAnnualResultBlocksDto(
    @Json(name = "blocks") val blocks: List<AudioGetAnnualResultBlockDto>,
)

@JsonClass(generateAdapter = true)
data class AudioGetAnnualResultBlockDto(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String,
    @Json(name = "order") val order: Int = 0,
    @Json(name = "is_visible") val isVisible: Boolean = false,
    @Json(name = "is_sharing_enabled") val isSharingEnabled: Boolean = false,
    @Json(name = "background_url") val backgroundUrl: String = "",
    @Json(name = "story_bg") val storyBg: String = "",
    @Json(name = "fallback_background_url") val fallbackBackgroundUrl: String = "",
    @Json(name = "audio_preview_url") val audioPreviewUrl: String = "",
    @Json(name = "titles") val titles: List<AnnualResultValue> = emptyList(),
    @Json(name = "subtitles") val subtitles: List<AnnualResultValue> = emptyList(),
    @Json(name = "metrics") val metrics: List<AnnualResultValue> = emptyList(),
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
    @Json(name = "playlist_photo_url") val playlistPhotoUrl: String = "",
    @Json(name = "playlist_title") val playlistTitle: String = "",
    @Json(name = "playlist_audio_raw_ids") val playlistAudioRawIds: List<String> = emptyList(),
    @Json(name = "screen_caption") val screenCaption: String = "",
    @Json(name = "screen_title") val screenTitle: String = "",
    @Json(name = "screen_subtitle") val screenSubtitle: String = "",
    @Json(name = "artist") val artist: AnnualResultValue? = null,
)

// оригинальное имя — вложенный класс AudioGetAnnualResultBlockDto.Value
@JsonClass(generateAdapter = true)
data class AnnualResultValue(
    @Json(name = "title") val title: String = "",
    @Json(name = "subtitle") val subtitle: String = "",
    @Json(name = "caption") val caption: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "value") val value: String = "",
    @Json(name = "photo_url") val photoUrl: String = "",
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
)
```

### Текущее состояние в LMG-VK

`VkMethodsRegistry.kt:386-387` — `execute<Any>("studio.getArtistYearRecapData")`,
параметр совпадает; нужен типизированный парсер.

**Уверенность: подтверждено.**

---

## 5. `musicStatResults.getMetrics`

**Класс-обёртка:** `C4271e`. **Парсер:** `C17647e.f34577e` → `C17647e(15)` → ветка `default`
(`P3 §8`: `case 15: musicStatResults.getMetrics (C4271e)`).

Дословно, `FRESH C4271e.java:1120-1127` (три идентичные площадки: `:1125`, `:1177`, `:1258`):

```java
defpackage.C8221e c8221eVip3 = defpackage.AbstractC1831e.vip();
java.lang.String str2 = this.f9362e;
...
defpackage.C5577e c5577e2 = new defpackage.C5577e("musicStatResults.getMetrics", defpackage.C17647e.f34577e);
c5577e2.ad("access_token", str2);
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `access_token` | String | да | — | `FRESH C4271e.java:1126` |

Это **не** основной токен. `this.f9362e` заполняется отдельным потоком VK Mini App OAuth,
дословно `FRESH C4271e.java:1075`:

```java
objLicense = c8221eVip.vip(52384530, "audio,photos",
    "https://prod-app52384530-74ed1fb7d3e1.pages-ac.vk-apps.com/index.html", c7959e);
```

Внутри `C8221e.vip(appId, scope, sourceUrl, cont)` (`FRESH C8221e.java:1429-1650`) —
GET на `oauth.<host>/authorize` c параметрами `scope`, `client_id`, `source_url`,
`display=android`, `response_type=token`, `redirect_uri=https://oauth.<host>/blank.html`,
`device_id`, `v=5.272`, `https=1`, `access_token=<основной токен>`; полученный токен читается
после `access_token=` из заголовка `X-Req-Hash` либо после `/blank.html#access_token=`
из URL редиректа, обрезается по `"&"`. Если токен `null` — вызов не делается
(`FRESH C4271e.java:1211-1214` → `Unit`). **подтверждено**

### Ответ

`C17227e` = `bruhcollective.itaysonlab.vkapi.objects.unofficial.year_stats.Y25Response`
(`FRESH C6204e.java:13-16`, `FRESH C17227e.java:41`):

| JSON | Kotlin | Тип | Nullable | Обяз. |
|---|---|---|---|---|
| `audio_tooltip` | `audioTooltip` | String | нет | да (маска `7`) |
| `blocks` | `blocks` | `List<Y25CBlock>` | нет | да |
| `actions` | `actions` | `List<Y25Action>` | нет | да |

Сериализаторы элементов: `FRESH C18420e.java` case 17 → `ListSerializer(C16370e)` (= `Y25CBlock`),
case 18 → `ListSerializer(C11052e)` (= `Y25Action`).

#### `Y25CBlock` (`C2314e`, дескриптор `FRESH C16370e.java:13-26`, дефолты `FRESH C2314e.java:26-77`, имена `FRESH C2314e.java:95`)

Маска обязательных = `195` (`0b11000011`) → обязательны индексы 0, 1, 6, 7.

| # | JSON | Kotlin | Тип | Обяз. | Дефолт |
|---|---|---|---|---|---|
| 0 | `type` | `type` | `Y25CBType` | да | — |
| 1 | `name` | `name` | String | да | — |
| 2 | `titles` | `titles` | `List<Y25Title>` | нет | `[]` |
| 3 | `subtitles` | `subtitles` | `List<Y25Title>` | нет | `[]` |
| 4 | `photo_urls` | `photoUrls` | `List<String>` | нет | `[]` |
| 5 | `background` | `background` | `Y25Background?` | нет | `null` |
| 6 | `is_visible` | `isVisible` | Boolean | да | — |
| 7 | `order` | `order` | Int | да | — |
| 8 | `is_sharing_enabled` | `isSharingEnabled` | Boolean | нет | `false` |
| 9 | `audio_preview_url` | `audioPreviewUrl` | String | нет | `""` |
| 10 | `metrics` | `metrics` | `List<Y25Title>` | нет | `[]` |
| 11 | `color_type` | `colorType` | `Y25CBColor` | нет | первое значение (`blue`) |
| 12 | `playlist` | `playlist` | `Y25Playlist?` | нет | `null` |

Элементы 2/3/10 — `ListSerializer(C16715e)` = `Y25Title` (`FRESH C2314e.java:9`,
`FRESH C18420e.java` case 11/12/14); элемент 4 — `ListSerializer(String)` (case 13).

#### Перечисления (значения дословно, `FRESH C18420e.java:38-39`)

```java
case 8: return AbstractC7237e.vip("bruhcollective.itaysonlab.vkapi.objects.unofficial.year_stats.Y25CBColor",
    EnumC0767e.values(), new String[]{"blue","blue_light","blue_dark","cyan","violet","pink","pink_dark"}, …);
case 9: return AbstractC7237e.vip("bruhcollective.itaysonlab.vkapi.objects.unofficial.year_stats.Y25CBType",
    EnumC14399e.values(), new String[]{"base","welcome","number","top","summary","base_ext","achievement",
    "playlist","placeholder","video","top_artist"}, …);
```

#### `Y25Title` (`C2277e`, дескриптор `FRESH C16715e.java:13-18`, дефолты `FRESH C2277e.java:15-40`)

| JSON | Kotlin | Тип | Обяз. | Дефолт |
|---|---|---|---|---|
| `title` | — (`ad`) | String | нет | `""` |
| `value` | — (`vip`) | String | нет | `""` |
| `caption` | — (`metrica`) | String | нет | `""` |
| `resource` | — (`license`) | String | нет | `""` |
| `content` | — (`appmetrica`) | `Y25Content?` | нет | `null` |

Kotlin-имена свойств `Y25Title` — `toString()` не удалось прочитать целиком, поэтому
**не найдено в доках**; используем JSON-имена.

#### `Y25Content` (`C5767e`, дескриптор `FRESH C3775e.java:13-15`, имена `FRESH C5767e.java:36`)

| JSON | Kotlin | Тип | Обяз. | Дефолт |
|---|---|---|---|---|
| `cover` | `coverUrl` | String | нет | `""` |
| `video` | `video` | `List<Y25ContentVideoType>` | нет | `[]` |

Элемент — `ListSerializer(C9448e)` (`FRESH C18420e.java` case 16).

#### `Y25ContentVideoType` (`C16991e`, дескриптор `FRESH C9448e.java:13-15`)

| JSON | Тип | Обяз. |
|---|---|---|
| `name` | String | да (маска `3`) |
| `link` | String | да |

#### `Y25Background` (`C1769e`, дескриптор `FRESH C5773e.java:13-17`, имена `FRESH C1769e.java:41`)

| JSON | Kotlin | Тип | Обяз. |
|---|---|---|---|
| `desktop` | `desktop` | `Y25Content` | да (маска `15`) |
| `mobile` | `mobile` | `Y25Content` | да |
| `story` | `story` | `Y25Content` | да |
| `post` | `post` | `Y25Content` | да |

#### `Y25Playlist` (`C16321e`, дескриптор `FRESH C8858e.java:13-16`)

| JSON | Тип | Обяз. |
|---|---|---|
| `title` | String | да (маска `7`) |
| `id` | Long | да |
| `photo_url` | String | да |

#### `Y25Action` (`C13196e`, дескриптор `FRESH C11052e.java:13-15`, имена `FRESH C13196e.java:36`)

| JSON | Kotlin | Тип | Обяз. |
|---|---|---|---|
| `title` | `title` | String | да (маска `3`) |
| `type` | `mobile` | String | да |

**Внимание:** JSON-имя второго поля — `type` (дескриптор), а Kotlin-свойство называется `mobile`
(литерал в `toString`). Порядок кодирования подтверждён дословно, `FRESH C11052e.java:20-28`:
`ads(descriptor, 0, c13196e.ad); ads(descriptor, 1, c13196e.vip);`. Оба факта из кода, догадок нет.

### DTO для создания

```kotlin
@JsonClass(generateAdapter = true)
data class Y25Response(
    @Json(name = "audio_tooltip") val audioTooltip: String,
    @Json(name = "blocks") val blocks: List<Y25CBlock>,
    @Json(name = "actions") val actions: List<Y25Action>,
)

@JsonClass(generateAdapter = true)
data class Y25CBlock(
    @Json(name = "type") val type: Y25CBType,
    @Json(name = "name") val name: String,
    @Json(name = "is_visible") val isVisible: Boolean,
    @Json(name = "order") val order: Int,
    @Json(name = "titles") val titles: List<Y25Title> = emptyList(),
    @Json(name = "subtitles") val subtitles: List<Y25Title> = emptyList(),
    @Json(name = "photo_urls") val photoUrls: List<String> = emptyList(),
    @Json(name = "background") val background: Y25Background? = null,
    @Json(name = "is_sharing_enabled") val isSharingEnabled: Boolean = false,
    @Json(name = "audio_preview_url") val audioPreviewUrl: String = "",
    @Json(name = "metrics") val metrics: List<Y25Title> = emptyList(),
    @Json(name = "color_type") val colorType: Y25CBColor = Y25CBColor.BLUE,
    @Json(name = "playlist") val playlist: Y25Playlist? = null,
)

@JsonClass(generateAdapter = false)
enum class Y25CBType {
    @Json(name = "base") BASE,
    @Json(name = "welcome") WELCOME,
    @Json(name = "number") NUMBER,
    @Json(name = "top") TOP,
    @Json(name = "summary") SUMMARY,
    @Json(name = "base_ext") BASE_EXT,
    @Json(name = "achievement") ACHIEVEMENT,
    @Json(name = "playlist") PLAYLIST,
    @Json(name = "placeholder") PLACEHOLDER,
    @Json(name = "video") VIDEO,
    @Json(name = "top_artist") TOP_ARTIST,
}

@JsonClass(generateAdapter = false)
enum class Y25CBColor {
    @Json(name = "blue") BLUE,
    @Json(name = "blue_light") BLUE_LIGHT,
    @Json(name = "blue_dark") BLUE_DARK,
    @Json(name = "cyan") CYAN,
    @Json(name = "violet") VIOLET,
    @Json(name = "pink") PINK,
    @Json(name = "pink_dark") PINK_DARK,
}

@JsonClass(generateAdapter = true)
data class Y25Title(
    @Json(name = "title") val title: String = "",
    @Json(name = "value") val value: String = "",
    @Json(name = "caption") val caption: String = "",
    @Json(name = "resource") val resource: String = "",
    @Json(name = "content") val content: Y25Content? = null,
)

@JsonClass(generateAdapter = true)
data class Y25Content(
    @Json(name = "cover") val coverUrl: String = "",
    @Json(name = "video") val video: List<Y25ContentVideoType> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Y25ContentVideoType(
    @Json(name = "name") val name: String,
    @Json(name = "link") val link: String,
)

@JsonClass(generateAdapter = true)
data class Y25Background(
    @Json(name = "desktop") val desktop: Y25Content,
    @Json(name = "mobile") val mobile: Y25Content,
    @Json(name = "story") val story: Y25Content,
    @Json(name = "post") val post: Y25Content,
)

@JsonClass(generateAdapter = true)
data class Y25Playlist(
    @Json(name = "title") val title: String,
    @Json(name = "id") val id: Long,
    @Json(name = "photo_url") val photoUrl: String,
)
```

### Текущее состояние в LMG-VK

`VkMethodsRegistry.kt:384`:

```kotlin
suspend fun musicStatGetMetrics() = execute<Any>("musicStatResults.getMetrics") {}
```

**Ошибка порта:** нет обязательного `access_token`. В таком виде вызов на сервере не пройдёт.
Потока VK Mini App OAuth (`C8221e.vip(appId, scope, sourceUrl)`) в LMG-VK нет вообще.

**Уверенность: подтверждено.**

---

## 6. `musicStatResults.createPlaylist`

**Класс-обёртка:** `C4673e`. **Парсер:** `C1400e.f4206e` → `C1400e(15)` → ветка `default`.

Дословно, `FRESH C4673e.java:232-256`:

```java
defpackage.C8221e c8221eVip2 = defpackage.AbstractC1831e.vip();
defpackage.C16321e c16321e2 = c2314e.smaato;              // Y25CBlock.playlist
if (c16321e2 == null || (str = c16321e2.ad) == null) {    // Y25Playlist.title
    str = "My 2025";
}
java.lang.String str2 = c4271e.f9362e;                    // тот же mini-app access_token
if (str2 == null) { return kotlin.Unit.INSTANCE; }
...
defpackage.C5577e c5577e2 = new defpackage.C5577e("musicStatResults.createPlaylist", defpackage.C1400e.f4206e);
c5577e2.ad("title", str);
c5577e2.ad("access_token", str2);
...
if (!defpackage.AbstractC7890e.billing(c6763e != null ? c6763e.ad : null, "pending")) {
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `title` | String | да | `"My 2025"`, если `Y25CBlock.playlist?.title == null` | `FRESH C4673e.java:248` |
| `access_token` | String | да | — (mini-app токен, см. §5) | `FRESH C4673e.java:249` |

**подтверждено**

### Ответ

`C6763e` = `bruhcollective.itaysonlab.vkapi.objects.unofficial.year_stats.Y25PlaylistCreateAction`
(`FRESH C9761e.java:13-15`, `FRESH C6763e.java:37`):

| JSON | Kotlin | Тип | Nullable | Обяз. |
|---|---|---|---|---|
| `status` | `status` | String | нет | да (маска `3`) |
| `id` | `id` | Int | нет | да |

Известное значение `status` — `"pending"` (`FRESH C4673e.java:256`: пока статус `"pending"`,
клиент повторяет опрос; полный перечень значений — **не найдено в доках**).
Логика повторов: до 5 попыток (`i >= i4`, `i4 = 5`, `FRESH C4673e.java:226-231`).

После получения непустого `id` VK X пишет его в `storage.set`
(`annual_result_2025_created_playlists_id` = `{"id": <id>}`, см. §2.2).

### DTO для создания

```kotlin
@JsonClass(generateAdapter = true)
data class Y25PlaylistCreateAction(
    @Json(name = "status") val status: String,
    @Json(name = "id") val id: Int,
)
```

### Текущее состояние в LMG-VK

`VkMethodsRegistry.kt:381-382`:

```kotlin
suspend fun musicStatCreatePlaylist(title: String) =
    execute<Any>("musicStatResults.createPlaylist") { param("title", title) }
```

**Ошибка порта:** нет обязательного `access_token`.

**Уверенность: подтверждено.**

---

## 7. `apps.get` (сверка — уже реализовано)

**Класс-обёртка:** `C10943e`. **Парсер:** `C17647e.f34580e` → `C17647e(12)`
(`P3 §8`: `case 12: apps.get (C10943e)`).

Дословно, `FRESH C10943e.java:28-58` — весь метод:

```java
defpackage.C5577e c5577e = new defpackage.C5577e("apps.get", defpackage.C17647e.f34580e);
c5577e.metrica(51931326L, "app_id");
objLicense = c8221eVip.license(c5577e, c6407e);
...
java.lang.String str = ((defpackage.C0133e) defpackage.AbstractC13480e.m3591interface(
    ((defpackage.C8641e) defpackage.AbstractC3425e.startapp((defpackage.AbstractC9200e) objLicense)).vip)).f1306break;
if (str == null) { str = org.conscrypt.BuildConfig.FLAVOR; }
java.lang.Object[] objArr = ua.itaysonlab.vkxnative.VKXNative.x02(str).ad;
if (objArr.length - 1 < 0) { throw new java.lang.IllegalStateException("idx 0 size mismatch"); }
java.lang.Object obj = objArr[0];
if (obj == null) { throw new java.lang.IllegalStateException("idx 0 is empty"); }
if (obj instanceof java.lang.String) { return java.util.Collections.singletonMap("code", obj); }
throw new java.lang.IllegalStateException("idx 0 type mismatch");
```

### Параметры

| Имя | Тип | Обяз. | Дефолт | Источник |
|---|---|---|---|---|
| `app_id` | Long | да | `51931326` (хардкод) | `FRESH C10943e.java:32` |

Больше ничего (`extended`, `platform`, `fields` — не передаются). **подтверждено**

### Ответ

`C8641e<C0133e>` = `bruhcollective.itaysonlab.vkapi.objects.RootItemsResponseDto`
(`FRESH C8641e.java:13-15`), поля `count: Int?` (optional), `items: List<T>?` (optional).
Элемент — `C0133e` = `bruhcollective.itaysonlab.vkapi.objects.apps.AppsAppDto`
(`FRESH C8475e.java:13`), **76 полей**. Обязательные — только `type`, `id`, `title`
(индексы 0/1/2, `FRESH C8475e.java:14-16`), остальные optional.

Используется ровно одно поле: индекс `41` = `webview_url` (`FRESH C8475e.java:55`),
соответствие подтверждено дословно — `FRESH C8475e.java:112`
(`java.lang.String str11 = c0133e.f1306break;`) и `FRESH C8475e.java:274`
(`abstractC15920e.Signature(interfaceC9998e, 41, C9582e.ad, str11);`).

### Текущее состояние в LMG-VK

`app/src/main/kotlin/com/lmg/vk/network/methods/AppsGetSilentAuth.kt` (71 строка):
`SILENT_AUTH_APP_ID = 51931326L`, `param("app_id", …)`, `AppItem(webview_url)`,
`items.firstOrNull()?.webviewUrl`, затем `LmgNative.getSilentAuthorizationEnvironment(code).ad[0]`
и `mapOf("code" to obfuscated)`.

**Формы совпадают.** Отличия — только в обработке ошибок: оригинал бросает три разных
`IllegalStateException` (`"idx 0 size mismatch"`, `"idx 0 is empty"`, `"idx 0 type mismatch"`),
LMG-VK — только `"idx 0 is empty"`. Правок в параметрах не требуется.

**Уверенность: подтверждено.**

---

# 8. Execute-запросы (массовые операции)

Это самая ценная часть: тела `code=` в VK X **не хранятся строками** для этой группы — они
собираются билдерами VKScript. Ниже сначала разобраны все билдеры (чтобы тела можно было
воспроизвести байт-в-байт), затем — сами вызовы.

## 8.1 Как VK X собирает `code=`

`EP §4` (`C12309e.java:754-756`) даёт только шаблон одной операции. Полная машинерия
(всё дословно из `FRESH`):

### Аргументы вызова — `C8167e` (аналог `Map<String, Expr>`)

`FRESH C8167e.java:377-386` (конструктор `C8167e(int i)`, `default` → `new LinkedHashMap()`):

| Метод | Код | Что кладёт |
|---|---|---|
| `ad(int i, String key)` | `put(key, new C5401e(i, 18, (byte)0))` | `FRESH C8167e.java:47-49` |
| `license(String k, String v)` | `put(k, new C17089e(v, 1))` | `FRESH C8167e.java:154-156` |
| `metrica(String k, InterfaceC7004e e)` | `put(k, e)` — сырое выражение | `FRESH C8167e.java:186-188` |
| `vip(long j)` | `put("owner_id", new C14829e(j))` | `FRESH C8167e.java:330-332` |

Рендер значений:
* `C5401e(i, 18, 0).toString()` → `String.valueOf(i)` — **без кавычек** (`FRESH C5401e.java:534-541`);
* `C14829e(j).toString()` → `String.valueOf(j)` — **без кавычек** (`FRESH C14829e.java:14-21`);
* `C17089e(v, 1).toString()` → `"\"" + v + "\""` — **в кавычках** (`FRESH C17089e.java:47-53`).

Рендер всей карты — `FRESH C8167e.java:317-324`:

```java
case 0:
    return defpackage.AbstractC13480e.m3608try(((java.util.LinkedHashMap) this.f16626e).entrySet(),
        ",", null, null, new defpackage.C2091e(2), 30);
```

Трансформер записи — `FRESH C2091e.java:33-35`:

```java
case 2:
    java.util.Map.Entry entry = (java.util.Map.Entry) obj;
    return defpackage.AbstractC17540e.license("\n                    \"" + entry.getKey() + "\": " + entry.getValue() + "\n                ");
```

`AbstractC17540e.license` = `String.trimIndent()`. После `trimIndent` от
`"\n                    \"k\": v\n                "` остаётся ровно `"k": v` — то есть одна запись
рендерится как `"audio_id": 123`, а карта — как `"audio_id": 123,"owner_id": -456`
(разделитель `","`, **без пробела после запятой**).

### Одна операция — `C12309e`

`FRESH C12309e.java:863-869` (конструктор) и `:750-756` (`toString`):

```java
public C12309e(java.lang.String str, defpackage.C8167e c8167e, int i) {
    this.f24696e = 16;
    boolean z = (i & 4) == 0;
    this.f24693e = str;
    this.f24695e = c8167e;
    this.f24694e = z;
}
...
case 16:
    defpackage.C8167e c8167e = (defpackage.C8167e) this.f24695e;
    java.lang.String str = (java.lang.String) this.f24693e;
    if (this.f24694e) {
        return "API." + str + "({" + c8167e + "});";
    }
    return "API." + str + "({" + c8167e + "})";
```

Все четыре вызова этой группы используют `new C12309e(<method>, c8167e, 8)`;
`8 & 4 == 0` → `true` → форма **с точкой с запятой**.

### `return` — `C12916e`

`FRESH C12916e.java:486-487`:

```java
case 28:
    return "return " + ((defpackage.InterfaceC7004e) this.f25784e) + ";";
```

Все четыре вызова передают `new C5401e(1, 18, (byte) 0)` → `1` → `return 1;`.

### Склейка операций

Во всех четырёх местах: `AbstractC13480e.m3608try(arrayList, "\n\n", null, null, null, 62)` —
операции соединяются **двумя переводами строки**.

## 8.2 `execute` → пакетное `audio.delete` (без чанков) — `C15513e`

Класс: `FRESH C15513e.java` (весь `ad(List, C14771e)`, строки 9-28), `toString()` = `"Library"`.
Парсер `C6114e.f12858e` → `C6114e(4)` → `RawVkResponse<Int>`.
Вход — `List<C3637e>`, где `C3637e{ ad: int, vip: long, metrica: int }` (`FRESH C3637e.java:7-15`).

Дословно:

```java
public final java.lang.Object ad(java.util.List list, defpackage.C14771e c14771e) {
    defpackage.C8221e c8221eVip = defpackage.AbstractC1831e.vip();
    defpackage.C5577e c5577e = new defpackage.C5577e("execute", defpackage.C6114e.f12858e);
    java.util.ArrayList arrayList = new java.util.ArrayList();
    java.util.Iterator it = list.iterator();
    while (it.hasNext()) {
        defpackage.C3637e c3637e = (defpackage.C3637e) it.next();
        defpackage.C8167e c8167e = new defpackage.C8167e(0);
        c8167e.ad(c3637e.ad, "audio_id");
        c8167e.vip(c3637e.vip);
        kotlin.Unit unit = kotlin.Unit.INSTANCE;
        arrayList.add(new defpackage.C12309e("audio.delete", c8167e, 8));
    }
    arrayList.add(new defpackage.C12916e(28, new defpackage.C5401e(1, 18, (byte) 0)));
    kotlin.Unit unit2 = kotlin.Unit.INSTANCE;
    c5577e.ad("code", defpackage.AbstractC13480e.m3608try(arrayList, "\n\n", null, null, null, 62));
    java.lang.Object objLicense = c8221eVip.license(c5577e, c14771e);
    return objLicense == defpackage.EnumC2821e.f6782e ? objLicense : kotlin.Unit.INSTANCE;
}
```

**Результирующее тело `code`** (реконструкция по шаблонам выше; литеральной строки в APK нет,
поэтому это *вывод из билдеров*, а не цитата — но каждый символ подтверждён шаблоном):

```vkscript
API.audio.delete({"audio_id": 456239018,"owner_id": -2000123456});

API.audio.delete({"audio_id": 456239019,"owner_id": -2000123456});

return 1;
```

Параметры запроса: только `code`. Чанкования нет — весь список одним запросом.
Ответ: `Int` (значение `1`).

## 8.3 `execute` → пакетное `audio.add` (чанки по 25) — `C6759e`

Класс: `FRESH C6759e.java:102-150`. Парсер `C17354e.f34054e`.
Вход — `List<C18422e>`, поля используются как `vip: int` → `audio_id`,
`metrica: long` → `owner_id`, `purchase: String?` → `access_key`.

Дословно:

```java
defpackage.C5577e c5577e = new defpackage.C5577e("execute", defpackage.C17354e.f34054e);
java.util.ArrayList arrayList = new java.util.ArrayList();
for (defpackage.C18422e c18422e : list) {
    defpackage.C8167e c8167e = new defpackage.C8167e(i5);
    c8167e.ad(c18422e.vip, "audio_id");
    c8167e.vip(c18422e.metrica);
    java.lang.String str = c18422e.purchase;
    if (str != null) {
        c8167e.license("access_key", str);
    }
    kotlin.Unit unit = kotlin.Unit.INSTANCE;
    arrayList.add(new defpackage.C12309e("audio.add", c8167e, 8));
    i5 = 0;
}
i5 = 0;
arrayList.add(new defpackage.C12916e(28, new defpackage.C5401e(1, 18, (byte) 0)));
kotlin.Unit unit2 = kotlin.Unit.INSTANCE;
c5577e.ad("code", defpackage.AbstractC13480e.m3608try(arrayList, "\n\n", null, null, null, 62));
```

Чанкование и пауза, дословно `FRESH C6759e.java:162` и `:135`:

```java
it = defpackage.AbstractC13480e.applovin(25, c15942e).iterator();   // chunked(25)
...
jBilling = defpackage.AbstractC15440e.f30517e.billing(1500L, 2500L); // Random.nextLong(1500, 2500)
```

то есть **чанк 25 операций на запрос** и **случайная пауза 1500–2500 мс между чанками**.

**Результирующее тело `code`** (реконструкция по шаблонам; `access_key` — в кавычках,
`audio_id`/`owner_id` — без):

```vkscript
API.audio.add({"audio_id": 456239018,"owner_id": 123456,"access_key": "abcdef0123456789"});

API.audio.add({"audio_id": 456239019,"owner_id": 123456});

return 1;
```

Параметры запроса: только `code`.

## 8.4 `execute` → пакетное `audio.delete` (чанки по 25) — `C9518e:131`

Класс: `FRESH C9518e.java:129-150`. Парсер `C16628e.f32597e` → `C16628e(9)` →
`RawVkResponse<Int>` (`P3 §7`: `case 9: execute → Integer.TYPE`).
Вход — `List<C6571e>`, пара `{f13544e: Number → audio_id, f13543e: Number → owner_id}`
(`FRESH C6571e.java:7-17`).

Дословно:

```java
defpackage.C5577e c5577e = new defpackage.C5577e("execute", defpackage.C16628e.f32597e);
java.util.ArrayList arrayList = new java.util.ArrayList();
for (defpackage.C6571e c6571e : list) {
    int iIntValue = ((java.lang.Number) c6571e.f13544e).intValue();
    long jLongValue = ((java.lang.Number) c6571e.f13543e).longValue();
    defpackage.C8167e c8167e = new defpackage.C8167e(i6);
    c8167e.ad(iIntValue, "audio_id");
    c8167e.vip(jLongValue);
    kotlin.Unit unit = kotlin.Unit.INSTANCE;
    arrayList.add(new defpackage.C12309e("audio.delete", c8167e, 8));
    obj2 = obj2;
    i6 = 0;
}
i6 = 0;
arrayList.add(new defpackage.C12916e(28, new defpackage.C5401e(1, 18, (byte) 0)));
kotlin.Unit unit2 = kotlin.Unit.INSTANCE;
c5577e.ad("code", defpackage.AbstractC13480e.m3608try(arrayList, "\n\n", null, null, null, 62));
```

Чанк `applovin(25, …)` — `FRESH C9518e.java:193`; пауза `billing(1500L, 2500L)` —
`FRESH C9518e.java:98` и `:166`.

**Результирующее тело `code`** — идентично §8.2, но не более 25 операций на запрос:

```vkscript
API.audio.delete({"audio_id": 456239018,"owner_id": -2000123456});

API.audio.delete({"audio_id": 456239019,"owner_id": -2000123456});

return 1;
```

## 8.5 `execute` → пакетное `audio.deletePlaylist` (чанки по 25) — `C9518e:284`

Класс: `FRESH C9518e.java:282-305`. Парсер `C17647e.f34583e` → `C17647e(9)` →
`RawVkResponse<Int>` (`P3 §8`: `case 9: execute (C9518e)`).
Вход — `List<C10985e>`, тройка `{f21742e: Number → playlist_id, f21741e: Number → owner_id,
f21740e: String? → access_key}` (`FRESH C10985e.java:7-20`).

Дословно:

```java
defpackage.C5577e c5577e = new defpackage.C5577e("execute", defpackage.C17647e.f34583e);
java.util.ArrayList arrayList = new java.util.ArrayList();
for (defpackage.C10985e c10985e : list) {
    int iIntValue = ((java.lang.Number) c10985e.f21742e).intValue();
    java.lang.Object obj3 = obj2;
    long jLongValue = ((java.lang.Number) c10985e.f21741e).longValue();
    java.lang.String str = (java.lang.String) c10985e.f21740e;
    defpackage.C8167e c8167e = new defpackage.C8167e(i6);
    c8167e.ad(iIntValue, "playlist_id");
    c8167e.vip(jLongValue);
    if (str != null) {
        c8167e.license("access_key", str);
    }
    kotlin.Unit unit = kotlin.Unit.INSTANCE;
    arrayList.add(new defpackage.C12309e("audio.deletePlaylist", c8167e, 8));
    obj2 = obj3;
    i6 = 0;
}
i6 = 0;
arrayList.add(new defpackage.C12916e(28, new defpackage.C5401e(1, 18, (byte) 0)));
kotlin.Unit unit2 = kotlin.Unit.INSTANCE;
c5577e.ad("code", defpackage.AbstractC13480e.m3608try(arrayList, "\n\n", null, null, null, 62));
```

Чанк `applovin(25, …)` — `FRESH C9518e.java:345`; пауза `billing(1500L, 2500L)` —
`FRESH C9518e.java:257` и `:322`.

**Результирующее тело `code`** (реконструкция по шаблонам):

```vkscript
API.audio.deletePlaylist({"playlist_id": 12345,"owner_id": 123456,"access_key": "abcdef0123456789"});

API.audio.deletePlaylist({"playlist_id": 12346,"owner_id": 123456});

return 1;
```

## 8.6 Остальные конструкции VKScript-билдера (для будущих `execute`)

Все шаблоны прочитаны дословно и годятся, чтобы собирать новые тела **без выдумывания**:

| Класс | Кейс | Шаблон |
|---|---|---|
| `C11883e(a,b,n)` | 24 | `var <a> = <b>;` |
| `C11883e(a,b,n)` | 25 | `<a> + <b>` |
| `C11883e(a,b,n)` | 26 | `<a> = <b>;` |
| `C5891e(a,b,n)` | 23 | `<a> - <b>` |
| `C5891e(a,b,n)` | 24 | `<a> = <b>;` |
| `C10312e(a,b,n)` | 27 | `var <a> = <b>;` |
| `C10312e(a,b,n)` | 28 | `<a> + <b>` |
| `C10312e(a,b,n)` | 29 | `while (<a>) {\n    <join(b, "\n")>\n};` (через `trimIndent`) |
| `C3168e(a,b,n)` | 22 | `if (<a>) {\n    <join(b, ";\n")>\n};` (через `trimIndent`) |
| `C3168e(a,b,n)` | 23 | `<a>.push(<b>);` |
| `C3168e(a,b,n)` | 24 | `<a> != <b>` |
| `C12916e` | 28 | `return <x>;` |
| `C2443e(name,3)` | — | `<name>`; `.ad(x)` → `<name>.<x>` |
| `C14911e(name,5)` | — | `Args.<name>` |
| `C9770e(26,x)` | — | `String.valueOf(x)` |
| `C7364e(s,4)` | — | `<s>` (сырое) |
| `C17354e.f34047e` | — | `{}` |
| `C4524e.f9804e` | — | `[]` |

**Уверенность по §8: подтверждено** (все шаблоны и все четыре вызова процитированы дословно).
Сами тела помечены как «реконструкция по шаблонам» — в APK они существуют только как результат
работы билдера, литеральных строк для них нет.

---

# 9. Сводная таблица: метод → DTO → готовность к порту

| Метод | Нужные DTO | Состояние в LMG-VK | Готовность к порту |
|---|---|---|---|
| `utils.resolveScreenName` | `ResolvedScreenName` | `VkMethodsRegistry.kt:356` — `execute<Any>` | параметры верны, нужен DTO + парсер |
| `storage.get` | `StorageGetDto` (есть как `StorageItem`) | `:360-369` + `StorageParser:766` | **готово, правок нет** |
| `storage.set` | нет (ответ `Int`), для Y25 — `JsonStorageValue` | `:371-374` — `executeUnit` | **готово**; добавить дефолт `appId` |
| `stats.trackEvents` | нет | `:378` — `executeUnit` | **готово** (но см. §11) |
| `studio.getArtistYearRecapData` | `AudioGetAnnualResultBlocksDto`, `AudioGetAnnualResultBlockDto`, `AnnualResultValue` | `:386` — `execute<Any>` | параметры верны, нужны 3 DTO |
| `musicStatResults.getMetrics` | `Y25Response`, `Y25CBlock`, `Y25Title`, `Y25Content`, `Y25ContentVideoType`, `Y25Background`, `Y25Playlist`, `Y25Action`, enum `Y25CBType`, enum `Y25CBColor` | `:384` — `execute<Any>`, **нет `access_token`** | нужен mini-app OAuth + 8 DTO + 2 enum |
| `musicStatResults.createPlaylist` | `Y25PlaylistCreateAction` | `:381` — `execute<Any>`, **нет `access_token`** | нужен mini-app OAuth + 1 DTO |
| `apps.get` | `RootItemsResponseDto`, `AppsAppDto` (нужно 1 поле) | `AppsGetSilentAuth.kt` | **готово, правок нет** |
| `execute` → `audio.delete` (без чанков) | нет (ответ `Int`) | нет обёртки | тело собирается билдером — §8.2 |
| `execute` → `audio.add` (чанк 25) | нет | нет обёртки | §8.3 + пауза 1500–2500 мс |
| `execute` → `audio.delete` (чанк 25) | нет | нет обёртки | §8.4 + пауза 1500–2500 мс |
| `execute` → `audio.deletePlaylist` (чанк 25) | нет | нет обёртки | §8.5 + пауза 1500–2500 мс |

Итого новых DTO: 15 классов + 2 enum.

---

# 10. Пробелы

1. **Значения `utils.resolveScreenName.type`** — набор строк (`user`, `group`, `application`,
   `page`, `vk_app`…) в доках и в декомпилированном коде не встречается: `C0120e.vip` —
   обычный `String` без enum-обёртки. **не найдено в доках.**
2. **Kotlin-имена свойств `ResolvedScreenName` и `Y25Title`** — у `C0120e` нет `toString()`,
   у `C2277e` строку `toString` целиком прочитать не удалось. Использованы JSON-имена.
   **не найдено в доках.**
3. **Полный перечень `Y25PlaylistCreateAction.status`** — подтверждено только `"pending"`.
4. **Расхождение `Y25Action`**: JSON-имя второго поля `type`, Kotlin-имя `mobile`.
   Оба факта из кода; какое из них «правильно» с точки зрения VK — проверяемо только запросом.
5. **`stats.trackEvents`: точная семантика ветки one-shot** — `C8221e:561` декомпилируется
   с инверсией условия. Смысл («тело ответа не разбирается») подтверждён косвенно.
6. **Полный перечень имён событий `stats.trackEvents`** — подтверждены только три
   (`music_start_playback`, `music_stop_playback`, `podcast_play`); другие площадки
   (`DelayedAnalyticsFacade-VKPodcasts`, ветка `case 1` конструктора `C9438e`) прочитаны не целиком.
7. **`AppsAppDto`** — 76 полей; полный дескриптор не выписан, так как для `apps.get`
   нужно только `webview_url`. Полный список при необходимости: `FRESH C8475e.java:14-95`.
8. **Тела `execute` этой группы — не литералы.** Они собираются билдером, поэтому приведены как
   реконструкция. Если нужен байт-в-байт эталон — его можно получить только логированием
   реального запроса VK X либо повторной сборкой по шаблонам §8.1.
9. **Литеральные тела VKScript в APK есть, но у других групп.** Точки входа (на случай, если
   понадобятся): `FRESH C13029e.java:245` (`audio.getPlaylistById` + `audio.get`, ответ
   `AudioGetPlaylist.PlaylistResponse`), `FRESH C13029e.java:302` (создание/редактирование
   плейлиста, ответ `AudioCreatePlaylist.NewPlaylistResponse`), `FRESH C13029e.java` конструктор
   `ArrayList` (пакетное `audio.delete`, лимит 20 элементов, `+ "return true;"`),
   `FRESH C4600e.java:208` (`SearchInProfile$SearchResponse`), `FRESH C4600e.java:297`
   (`PrivacySetting`), `FRESH C4600e.java:187-196` (хранимая процедура
   `execute.getPodcastEpisodesWithInfo`, параметры `owner_id`, `count=100`, `offset=0`,
   `func_v=4`, тела нет), `FRESH C14561e.java` case 11 (`audio.get` +
   `audio.getAudioPreviewUrl` в цикле, `shuffle_seed`; потребитель — `FRESH C11459e.java`).
   Здесь их дословно не привожу: два из них (`C4600e:208`, `C4600e:297`) уже перенесены в
   `VkMethodsRegistry.kt:800` (`SEARCH_IN_PROFILE_EXECUTE_CODE`) и `:793`
   (`AUDIO_PRIVACY_EXECUTE_CODE`) и совпадают с оригиналом; остальные — зона группы 1.
10. **VK Mini App OAuth (`C8221e.vip(appId, scope, sourceUrl)`)** в LMG-VK отсутствует целиком.
    Без него `musicStatResults.*` не работают. Разбор потока — §5, но код в LMG-VK не написан
    (вне зоны этого документа).

---

# 11. Стоит ли портировать

## Не стоит

* **`stats.trackEvents` — нет.** Это телеметрия VK: `music_start_playback` /
  `music_stop_playback` / `podcast_play` с `uuid`, `track_code`, `source`, `prev_audio_id`,
  `start_time`, `playback_started_at`. Она не влияет ни на одну функцию клиента — ни на
  рекомендации через API, ни на права. Флаг `appmetrica = true` прямо говорит, что ответ
  не разбирается: это односторонний слив истории прослушиваний. При server-first архитектуре
  (вся логика и секреты на своём сервере, клиентским проверкам доверия нет) отправлять
  на VK детальный лог воспроизведения — чистый минус: лишний трафик, лишний fingerprint
  и лишний сигнал «это не официальный клиент», потому что подделать все поля события
  правдоподобно всё равно не получится. Спецификация выше нужна **не для порта**, а чтобы
  сознательно не отправлять эти события и не удивляться, если VK начнёт их требовать.
  Форма события выписана только для этого случая.
* **`studio.getArtistYearRecapData` — низкий приоритет.** Это «итоги года» для артиста
  в VK Studio (нужен `artist_id` своего артиста). Для музыкального клиента-слушателя
  бесполезно; порт оправдан только если планируется раздел для музыкантов.
* **`apps.get`** — портировать *отдельно* не нужно: он уже есть и нужен ровно в одном
  качестве — как шаг silent-auth. Расширять его (`AppsAppDto` целиком, 76 полей) смысла нет.

## Спорно

* **`musicStatResults.getMetrics` / `createPlaylist` («Итоги 2025»)** — фича сезонная и стоит
  дорого: mini-app OAuth-поток (app `52384530`, scope `audio,photos`, фиксированный
  `source_url` на `pages-ac.vk-apps.com`), 9 DTO, 2 enum, опрос статуса `"pending"` до 5 раз,
  плюс `storage.set` для запоминания созданного плейлиста. Хардкод `source_url` и `app_id`
  привязывает клиент к конкретному мини-приложению VK — оно перестанет отвечать после
  окончания кампании. Если «Итоги» не в планах — **не портировать**, и тогда не нужны
  ни `storage.set` с `JsonStorageValue`, ни `Y25*`.
* **`storage.get` / `storage.set`** — сами по себе полезны как бесплатное серверное KV
  на 52384530, но в VK X они используются **только** под «Итоги 2025». Если Y25 не портируется,
  KV остаётся без потребителя. С учётом server-first: своё хранилище на своём сервере надёжнее
  и не зависит от `app_id` чужого мини-приложения.

## Стоит

* **`utils.resolveScreenName` — да.** Дёшево (1 параметр, 2 поля), закрывает разбор
  ссылок вида `vk.com/<screen_name>` → `object_id` + `type`, без чего не работают
  deep-link'и на профили/группы/плейлисты.
* **`execute`-паттерны (§8) — да, это главное.** Пакетные `audio.add`, `audio.delete`,
  `audio.deletePlaylist` через `execute` экономят раунд-триты в 25 раз на массовых операциях
  (перенос библиотеки, чистка, «сохранить всё»). Отдельно ценно то, что VK X не шлёт чанки
  вплотную: `Random.nextLong(1500, 2500)` между запросами — эмпирически подобранный
  антифлуд-интервал VK, и его стоит скопировать вместе с телами. Чанк 25 — тоже не догадка,
  а `chunked(25)` из оригинала.
* **Порядок применения:** сначала `utils.resolveScreenName` (типизация) → потом `execute`-пакеты
  (реальный выигрыш) → и только если нужна фича «Итоги» — mini-app OAuth и `Y25*`.
