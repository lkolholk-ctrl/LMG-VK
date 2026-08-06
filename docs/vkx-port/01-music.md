# 01 — Музыкальные методы VK API (группа 1): мутации трека, поиск, артисты, StreamMix

Спецификация для порта в LMG-VK. Всё, что здесь написано, извлечено из реверс-инжиниринговых
документов и декомпилированных исходников VK X 8.12.1. Где подтверждения нет — так и написано.

## Условные обозначения источников

| Сокращение | Путь |
|---|---|
| `EP` | `/storage/emulated/0/Download/VKLMG_Recovery/VKX-ENDPOINTS.md` |
| `P1` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY1-RECOVERY.md` |
| `P2` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY2-RECOVERY.md` |
| `P3` | `/storage/emulated/0/Download/VKLMG_Recovery/PRIORITY3-RECOVERY.md` |
| `SRC` | `/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/<class>.java` |

Уверенность: **подтверждено** — есть дословный фрагмент кода; **частично** — фрагмент есть,
но источники противоречат друг другу или покрывают не всё; **не найдено в доках** — догадок нет.

## Как читать фрагменты запросов (`C5577e` = аналог `VkMethod`)

`SRC C5577e.java:20-40` — билдер запроса VK X, полностью совпадающий с `VkMethod` в LMG-VK:

| Вызов в декомпиляте | Что значит | Эквивалент LMG-VK |
|---|---|---|
| `.ad("k", str)` | строковый параметр; **если `str == null`, параметр не добавляется** | `param("k", str)` |
| `.vip(n, "k")` | `Int` → `String.valueOf(n)` | `param("k", n: Int)` |
| `.metrica(l, "k")` | `Long` → `String.valueOf(l)` | `param("k", l: Long)` |
| `.license("k", z)` | `Boolean` → `"1"`/`"0"` | `param("k", z: Boolean)` |

Версия API по умолчанию — `"5.272"` (`SRC C5577e.java:16`), совпадает с `VkMethod.apiVersion`.

## Замечание о состоянии `_already-implemented.txt`

Файл `docs/vkx-port/_already-implemented.txt` из этой группы перечисляет только
`audio.getAudioIdsBySource`. Фактически в коде уже есть обёртки (полные или заглушечные) для
`audio.add`, `audio.delete`, `audio.restore`, `audio.addDislike`, `audio.removeDislike`,
`audio.reorderInPlaylist`, `audio.searchMain`, `audio.searchArtists`,
`audio.getRelatedArtistsById`, `audio.getStreamMixSettings` — см.
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/methods/VkAudioApi.kt` и
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/methods/VkMethodsRegistry.kt`.
Ниже по каждому методу отдельно отмечено расхождение спеки и текущего кода.

---

## 1. `audio.add`

**Имя метода:** `audio.add`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `audio_id` | Int | да | — | `.vip(i7, "audio_id")` |
| `owner_id` | Long | да | — | `.metrica(j2, "owner_id")` |
| `access_key` | String | нет | — | `.ad("access_key", str)`; при `null` не отправляется |

Параметра `audio_ids` **нет**. Подпись «Добавить трек (audio_ids)» в таблице `EP:49` — это
подпись строки таблицы, а не контракт; дословный фрагмент вызова её опровергает.

**Источник:** `EP:123-141` (фрагмент `C14078e.java:264`), `SRC C14078e.java:449-467`.
**Уверенность:** подтверждено.

### Форма ответа

Не найдено в доках. Адаптер — `C4524e.f9811e` (`EP:1470`). `P3:153` перечисляет типы ответов
класса `C4524e` (`case 12 → C17710e`, `case 13 → C7555e`, `default → C0884e`), но привязка
`f9811e → номер case` в доках отсутствует. Место вызова результат не использует: после успеха
локально ставится `audioTrack.subs = Boolean.TRUE` (`EP:138`).

**Уверенность:** не найдено в доках.

### Массовый вариант (execute)

`execute` с `code`, собранным из `API.audio.add({audio_id: …, owner_id: …, access_key: …});`
на каждый трек, склеенных через `"\n\n"` (`EP:965-981`, `EP:1413-1428`).
`access_key` добавляется только если не `null`.

### DTO

Не нужны. Достаточно `UnitParser`.

### Расхождение с кодом LMG-VK

`VkAudioApi.add()` и `VkMethodsRegistry.audioAdd()` совпадают со спекой (`audio_id`, `owner_id`,
`access_key`, `Unit`). Массового execute-варианта нет.

---

## 2. `audio.delete`

**Имя метода:** `audio.delete`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `audio_id` | Int | да | — | `.vip(i8, "audio_id")` |
| `owner_id` | Long | да | — | `.metrica(j3, "owner_id")` |

`access_key` **не отправляется** (в отличие от `audio.add`).

**Источник:** `EP:165-183` (фрагмент `C14078e.java:291`), `SRC C14078e.java:483-497`.
**Уверенность:** подтверждено.

### Форма ответа

Не найдено в доках. Адаптер `C9616e.f19066e` (`EP:1472`); `P3:162` подтверждает, что в
`C9616e.mo600this` case 13 — это `audio.delete`, но тип ответа для этого case не выписан.
Место вызова результат не использует (`audioTrack.subs = Boolean.FALSE`, `EP:177`).

**Уверенность:** не найдено в доках.

### Массовый вариант (execute)

`API.audio.delete({audio_id: …, owner_id: …});` на трек (`EP:945-963` и `EP:983-999`).
В `P1:185` (диспетчер `C13029e`, id 20) тот же батч описан как разбор строк `"owner_audio"`
через `split("_")` → `Unit`.

### DTO

Не нужны.

### Расхождение с кодом LMG-VK

`VkAudioApi.delete()` / `VkMethodsRegistry.audioDelete()` совпадают.

---

## 3. `audio.restore`

**Имя метода:** `audio.restore`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `audio_id` | Int | да | — | `.vip(i6, "audio_id")` |
| `owner_id` | Long | да | — | `.metrica(j, "owner_id")` |

**Источник:** `EP:425-443` (фрагмент `C14078e.java:214`), `SRC C14078e.java:422-439`.
**Уверенность:** подтверждено.

### Форма ответа

`response` — объект `AudioAudioDto` (обфусц. `C18422e`), 39 ключей.
Привязка: `audio.restore → C16628e.f32594e` (`EP:1484`), `P3:121` — «case 13: `audio.restore` →
C18422e», `P1:308-309` — таблица восстановленных имён DTO.
Точные JSON-ключи и обязательность — из дескриптора сериализатора `SRC C14729e.java:12-52`
(FQN `bruhcollective.itaysonlab.vkapi.objects.audio.AudioAudioDto`):

Обязательные (5): `artist`, `id`, `owner_id`, `title`, `duration`.
Опциональные (34): `access_key`, `is_explicit`, `is_focus_track`, `is_licensed`, `track_code`,
`url`, `date`, `album_id`, `has_lyrics`, `genre_id`, `no_search`, `album`, `release_id`,
`track_id`, `mstcp_type`, `track_genre_id`, `content_restricted`, `main_artists`,
`featured_artists`, `subtitle`, `album_part_number`, `performer`, `podcast_info`,
`audio_chart_info`, `original_sound_video_id`, `short_videos_allowed`, `stories_allowed`,
`stories_cover_allowed`, `in_clips_favorite_allowed`, `in_clips_favorite`, `dmca_blocked`,
`kws_skip`, `is_official`, `release_audio_id`.

Это НЕ `{count, items}`.

**Уверенность:** подтверждено (ключи), частично (типы значений: kotlinx-дескриптор даёт имена и
`isOptional`, но не Kotlin-типы; типы `album`/`main_artists`/`featured_artists`/`podcast_info`/
`audio_chart_info` доками не подтверждены поимённо).

### DTO

Расширить существующий `com.lmg.vk.network.dto.music.AudioAudioDto` (сейчас там 21 из 39 ключей).
Недостающие поля, все nullable с дефолтом `null`, имена совпадают с JSON (`@Json` не нужен):

```
genre_id, no_search, release_id, track_id, mstcp_type, track_genre_id,
album_part_number, performer, original_sound_video_id, short_videos_allowed,
stories_allowed, stories_cover_allowed, in_clips_favorite_allowed,
in_clips_favorite, dmca_blocked, kws_skip, is_official,
podcast_info, audio_chart_info
```

Типы для `podcast_info` и `audio_chart_info` в доках не подтверждены — до проверки на живом API
безопаснее `Any?` или отдельные nullable-DTO с полностью опциональными полями.

### Расхождение с кодом LMG-VK

Уже есть два варианта: `VkAudioApi.restore()` → `Unit` и `VkAudioApi.restoreDetailed()` →
`AudioAudioDto`. Второй соответствует подтверждённому контракту. Параметры совпадают.

---

## 4. `audio.addDislike`

**Имя метода:** `audio.addDislike`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `audio_ids` | String (CSV) | да | — | `join(",")` списка полных id |

Формат элемента — `"{owner_id}_{id}"`: `SRC AbstractC6914e.java:64-72` строит строку из
`AudioTrack.metrica` (owner_id, Long) + `'_'` + `AudioTrack.vip` (id, Int).
VK X всегда шлёт `Collections.singletonList(...)`, то есть ровно один id, но параметр —
список через запятую.

**Источник:** `EP:144-162` (фрагмент `C2193e.java:136`), `SRC C2193e.java:134-152`.
**Уверенность:** подтверждено.

### Форма ответа

`response` — `AudioAudioDto` (`C18422e`), тот же 39-ключевой объект, что у `audio.restore`.
Привязки: `audio.addDislike → C17354e.f34037e` (`EP:1471`); `P1:296` — «12 / f34037e |
`audio.addDislike` | RawVkResponse<C18422e>»; `P1:308-309` — `C18422e` = DTO трека.

**Уверенность:** подтверждено (по диспетчеру); ключи — см. п. 3.

### DTO

Переиспользовать `AudioAudioDto` (с расширением из п. 3). Новые DTO не нужны.

### Расхождение с кодом LMG-VK

`VkAudioApi.addDislike()` использует `UnitParser` — параметр верный, но возвращаемый трек
теряется. Если нужен ответ — второй метод по образцу `restoreDetailed`.

---

## 5. `audio.removeDislike`

**Имя метода:** `audio.removeDislike`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `audio_ids` | String (CSV) | да | — | тот же формат `"{owner_id}_{id}"`, singleton в VK X |

**Источник:** `EP:383-401` (фрагмент `C2193e.java:176`), `SRC C2193e.java:195-212`.
**Уверенность:** подтверждено.

### Форма ответа

Не найдено в доках. Адаптер `C17647e.f34581e` (`EP:1482`); `P3:134` подтверждает «case 13:
`audio.removeDislike` (C2193e)» в `C17647e`, но тип DTO для этого case не выписан ни в P1, ни в
P3. Симметрия с `audio.addDislike` намекает на `AudioAudioDto`, но это **догадка, не факт**.

**Уверенность:** не найдено в доках.

### DTO

Пока не нужны — `UnitParser` (текущее поведение LMG) безопасно.

### Расхождение с кодом LMG-VK

`VkAudioApi.removeDislike()` совпадает.

---

## 6. `audio.reorderInPlaylist`

**Имя метода:** `audio.reorderInPlaylist`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `playlist_id` | Int | да | — | `.vip(this.ad, "playlist_id")` |
| `owner_id` | Long | да | — | `.metrica(this.vip, "owner_id")` |
| `actions` | String (JSON) | да | — | JSON-массив массивов, см. ниже |

`actions` — сериализованный kotlinx `JsonArray` из `JsonArray`-элементов; каждый элемент —
**позиционный** массив из трёх значений в порядке:

```
[ trackOwnerId (Long), trackId (Int), newIndex (Int) ]
```

Порядок восстановлен из `SRC C3294e.java:43-55`: сначала добавляется `C1591e.vip`, затем
`C1591e.ad`, затем `C1591e.metrica`. Имена полей — из `SRC C1591e.java:72-84`:
`AudioPlaylistReorderActionDto(trackId=…, trackOwnerId=…, newIndex=…)`.
Значение `newIndex = -1` — дефолт двухаргументного конструктора (`SRC C1591e.java:27-31`).

Ключей у элементов нет — это массив, а не объект.
`access_key` плейлиста класс-обёртка хранит (`SRC C3294e.java:119-129`, `Playlist(id, ownerId,
accessKey)`), но в запрос **не кладёт**.

**Источник:** `EP:404-422` (фрагмент `C3294e.java:26`, обрывается до `actions`),
`SRC C3294e.java:17-70` (полное тело, включая `actions`).
**Уверенность:** подтверждено.

### Форма ответа

Место вызова возвращает `Unit` и результат не разбирает (`SRC C3294e.java:64-69`).
Диспетчер объявляет `RawVkResponse<C7555e>` (`P1:278`, `EP:1483` → `C14914e.f29575e`), но
`C7555e` — это **не** «AudioReorderInPlaylistDto», как назван в `P1:309`: его `toString`
(`SRC C7555e.java:116-131`) и дескриптор сериализатора (`SRC C11382e.java:11-17`, FQN
`…objects.audio.AudioGetPlaylistsResponseDto`, ключи `count`, `items`, `groups`, `profiles`,
`next_from`) говорят, что это ответ `audio.getPlaylists`. Скорее всего — артефакт слияния
case-веток jadx (об этом прямо предупреждает `P1:307`).

**Уверенность:** частично — параметры точны, форма ответа противоречива; парсить как `Unit`.

### DTO

JSON-DTO не нужен. Нужна вспомогательная модель для сборки `actions`:

```kotlin
data class AudioPlaylistReorderAction(
    val trackId: Int,
    val trackOwnerId: Long,
    val newIndex: Int = -1,
)
```

Сериализовать вручную через `org.json.JSONArray` (как уже сделано для `options` в
`VkAudioApi.getStreamMixAudios`), а не Moshi: элементы позиционные.

### Расхождение с кодом LMG-VK

`VkMethodsRegistry.reorderInPlaylist(playlistId, ownerId)` **неполон** — не передаёт обязательный
`actions`, то есть в текущем виде метод бесполезен. Это главный кандидат на правку в группе.

---

## 7. `audio.searchMain`

**Имя метода:** `audio.searchMain`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `q` | String | да | — | `.ad("q", str)` |
| `count` | Int | да | 5 в VK X | `5.coerceIn(0..300)` → фактически шлётся `5` |
| `offset` | Int | да | 0 | `.vip(0, "offset")` |

Про `count`: в декомпиляте `AbstractC3062e.license(5, new C15926e(0, 300, 1))` — это
`coerceIn(0..300)` от литерала `5`. То есть VK X на этом экране (CatalogKit-поиск) просит 5
элементов на секцию, а `0..300` — клиентский диапазон допустимых значений. Верхняя граница
`count <= 300` подтверждается только этим клиентским клампом (тот же кламп у `audio.search`,
`EP:453`), а не документацией сервера.

**Источник:** `EP:482-500` (фрагмент `C18378e.java:375`), `SRC C18378e.java:112-127`.
**Уверенность:** подтверждено.

### Форма ответа

`response` — объект `AudioSearchMainResponseDto` (`C6207e`), 7 секций, **все опциональные**,
дефолт каждой — пустой `RootItemsResponseDto`.
Ключи (`SRC C3649e.java:9-18`, FQN `…objects.audio.AudioSearchMainResponseDto`):

| Ключ | Тип | Элемент |
|---|---|---|
| `albums` | `{count, items}` | `AudioPlaylistDto` |
| `audios` | `{count, items}` | `AudioAudioDto` |
| `artists` | `{count, items}` | `AudioArtistDto` |
| `playlists` | `{count, items}` | `AudioPlaylistDto` |
| `own_audios` | `{count, items}` | `AudioAudioDto` |
| `own_playlists` | `{count, items}` | `AudioPlaylistDto` |
| `own_albums` | `{count, items}` | `AudioPlaylistDto` |

Типы элементов — из `SRC C1349e.java` (cases 5–11 возвращают `C8641e.Companion.serializer(X)`,
где X = `C1471e` = `AudioPlaylistDto` (`SRC C1471e.java:13`), `C14729e` = `AudioAudioDto`,
`C5992e` = `AudioArtistDto` (`SRC C5992e.java:11`)). Порядок полей — `SRC C6207e.java:135-157`.

`RootItemsResponseDto` (`C8641e`) = **`{count: Int?, items: []}`**, оба ключа опциональные,
дефолты `null` и `[]` (`SRC C8641e.java:11-14`, FQN
`bruhcollective.itaysonlab.vkapi.objects.RootItemsResponseDto`). Это ровно `VkItems<T>` /
`VkRootItems<T>` в LMG-VK.

**Уверенность:** подтверждено.

### DTO

Новых не нужно. Уже точно совпадают: `AudioSearchMainResponse`, `AudioAudioDto`,
`AudioArtistDto`, `AudioPlaylistDto`, `VkRootItems` в
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/dto/music/Priority2MusicDtos.kt`
и `Priority1MusicDtos.kt`.

### Расхождение с кодом LMG-VK

`VkAudioApi.searchMain()` совпадает; отличается только кламп `count.coerceIn(1, 300)` против
оригинального `0..300`.

---

## 8. `audio.searchArtists`

**Имя метода:** `audio.searchArtists`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `q` | String | да | — | `.ad("q", str)` |
| `offset` | Int | да | 0 | `.vip(0, "offset")` |
| `count` | Int | да | 100 | `.vip(100, "count")` — литерал, кламп отсутствует |

**Источник:** `EP:461-470` (фрагмент `C14197e.java:77`), `SRC C14197e.java:86-98`, `P1:64`.
**Уверенность:** подтверждено.

### Форма ответа

`response` = `RootItemsResponseDto<AudioArtistDto>` — то есть **`{count, items}`**, маппится
напрямую на существующий `VkItems<AudioArtistDto>` (или `VkRootItems<AudioArtistDto>`).
`count: Int?` опционален, `items` опционален с дефолтом `[]` (`SRC C8641e.java:11-14`).
Элемент — `AudioArtistDto` (`P1:77`: «f9883e | 13 | `audio.searchArtists` →
RawVkResponse<C8641e> (элемент C0004e — карточка исполнителя)»); каст ответа к `C8641e` и взятие
`.items` виден в `SRC C14197e.java:115-124`.

`AudioArtistDto` — 15 ключей (`SRC C5992e.java:11-27`, FQN `…objects.audio.AudioArtistDto`):
обязательный `name`; опциональные `domain`, `id`, `is_album_cover`, `photo`, `photos`,
`is_followed`, `can_follow`, `can_play`, `genres`, `bio`, `pages`, `profiles`, `groups`,
`track_code`.

**Уверенность:** подтверждено.

### DTO

Новых не нужно: `VkItems<AudioArtistDto>` + существующий `AudioArtistDto` (Priority2MusicDtos.kt)
совпадают с дескриптором ключ в ключ.

### Расхождение с кодом LMG-VK

`VkAudioApi.searchArtists()` возвращает `VkRootItems<VkArtistDto>`. `VkArtistDto`
(Priority1MusicDtos.kt) — тот же набор минус `pages`/`profiles`/`groups`; Moshi лишние ключи
игнорирует, так что это рабочий, но неполный вариант. Точный тип — `AudioArtistDto`.

---

## 9. `audio.getRelatedArtistsById`

**Имя метода:** `audio.getRelatedArtistsById`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `artist_id` | String | да | — | `.ad("artist_id", str2)` |
| `offset` | Int | да | 0 | `.vip(0, "offset")` |
| `count` | Int | да | 10 | `.vip(10, "count")` — литерал, кламп отсутствует |

**Источник:** `EP:320-338` (фрагмент `C17019e.java:363`), `P1:97`.
**Уверенность:** подтверждено.

### Форма ответа

`response` — объект с **одним обязательным** ключом:

```
{ "artists": [ AudioArtistDto, … ] }
```

Источники: `SRC C7346e.java:11-13` (FQN `…objects.audio.AudioGetRelatedArtistsResponseDto`,
`advert("artists", false)` — не опционален), `SRC C16339e.java:53-59` (`toString` =
`AudioGetRelatedArtistsResponseDto(artists=…)`), тип элемента — `C5992e` = `AudioArtistDto`
(`SRC C14561e.java` case 7). Это **не** `{count, items}`.

**Уверенность:** подтверждено.

### DTO

Новых не нужно, но точнее — параметризовать существующий
`AudioRelatedArtistsResponse` элементом `AudioArtistDto`:

```kotlin
@JsonClass(generateAdapter = true)
data class AudioRelatedArtistsResponse(
    val artists: List<AudioArtistDto> = emptyList(),
)
```

### Расхождение с кодом LMG-VK

`VkAudioApi.getRelatedArtistsById()` совпадает по параметрам (дефолт `count = 10` тот же);
элемент списка — `VkArtistDto` вместо `AudioArtistDto` (см. п. 8, разница только в трёх
неиспользуемых ключах).
`VkMethodsRegistry.getRelatedArtists()` — заглушка с `execute<Any>`, дублирует `VkAudioApi`.

---

## 10. `audio.getStreamMixSettings`

**Имя метода:** `audio.getStreamMixSettings`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `mix_id` | String | да | — | `.ad("mix_id", str2)` — единственный параметр |

**Источник:** `EP:341-359` (фрагмент `C7914e.java:395`), `P1:128`.
**Уверенность:** подтверждено.

### Форма ответа

Все ключи **обязательные** (`advert(…, false)` во всех четырёх дескрипторах):

```
{
  "settings": {                                   // SRC C12924e.java:9-10
    "title": String,
    "subtitle": String,
    "mix_categories": [                           // SRC C18102e.java:11-14
      {
        "id": String, "title": String, "type": String,   // SRC C14713e.java:11-15
        "options": [
          { "id": String, "icon": String,               // SRC C15663e.java:9-13
            "selected": Boolean, "title": String }
        ]
      }
    ]
  }
}
```

FQN: `…objects.audio.AudioGetStreamMixSettingsResponseDto` / `AudioStreamMixSettingsDto` /
`AudioStreamMixSettingsCategoryDto` / `AudioStreamMixSettingsOptionDto`.
Не `{count, items}`.

**Уверенность:** подтверждено.

### DTO

Новых не нужно. Уже совпадают ключ в ключ:
`AudioStreamMixSettingsResponse`, `AudioStreamMixSettings`, `AudioStreamMixSettingsCategory`,
`AudioStreamMixSettingsOption` в
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/dto/music/Priority1MusicDtos.kt`.

### Расхождение с кодом LMG-VK

`VkAudioApi.getStreamMixSettings()` полностью соответствует.
`VkMethodsRegistry.getStreamMixSettings()` — заглушка `execute<Any>`, дубль.

---

## 11. `audio.recommendationsOnboarding`

**Имя метода:** `audio.recommendationsOnboarding`

### Параметры

**Нет ни одного.** Запрос создаётся и сразу отправляется:
`new C5577e("audio.recommendationsOnboarding", C1400e.f4197e)` — ни `.ad`, ни `.vip`, ни
`.metrica` не вызываются.

**Источник:** `EP:362-380` (фрагмент `C14197e.java:85`), `EP:474`, `SRC C14197e.java:104-109`.
**Уверенность:** подтверждено.

### Форма ответа

Источники расходятся:

* `P1:241` — «13 / f4197e | `audio.recommendationsOnboarding` | RawVkResponse<AudioArtistDto>
  (C0004e)», то есть один объект артиста.
* Место вызова (`SRC C14197e.java:115-124`) кастует результат **обеих** веток (и
  `searchArtists`, и `recommendationsOnboarding`) к `C8641e` = `RootItemsResponseDto` и берёт
  `.items` в тот же список исполнителей.

Учитывая предупреждение `P1:307` о том, что jadx сливает case-ветки диспетчеров, версия из
места вызова достовернее: ожидаемая форма — **`{count, items: [AudioArtistDto]}`**, то есть
`VkItems<AudioArtistDto>`. Но это не однозначно подтверждено.

**Уверенность:** частично.

### DTO

Новых не нужно: при подтверждении формы — `VkItems<AudioArtistDto>`.
Порт делать с парсером, устойчивым к обеим формам, либо сначала проверить на живом API.

### Смежный метод (вне группы, для контекста)

`audio.finishRecomsOnboarding` с параметром `artist_ids` — `P1:171` (диспетчер `C13029e`, id 4).
Это завершение того же онбординга; в LMG-VK его нет.

### Расхождение с кодом LMG-VK

Метода нет ни в `VkAudioApi`, ни в `VkMethodsRegistry`. Порт с нуля.

---

## 12. `audio.getAudioIdsBySource`

**Имя метода:** `audio.getAudioIdsBySource`

### Параметры

| Имя | Тип | Обяз. | Дефолт | Примечание |
|---|---|---|---|---|
| `source` | String (enum) | да | — | `.ad("source", enumC4755e.value)` |
| `entity_id` | String | да | — | `.ad("entity_id", str)` |

Допустимые значения `source` (`SRC EnumC4755e.java:19-34`, восемь констант с их wire-строками):

`artist`, `catalog`, `curator`, `feed`, `im`, `playlist`, `similar_track`, `wall`.

**Источник:** `EP:223-241` (фрагмент `AbstractC18159e.java:51`), `SRC AbstractC18159e.java:90-98`.
**Уверенность:** подтверждено.

### Форма ответа

```
{ "audios": [ { "audio_id": String, "track_code": String } ] }
```

Ключ верхнего уровня `audios` — `SRC C11462e.java:12-13` (FQN
`…objects.audio.AudioGetAudioIdsBySourceResponseDto`).
Элемент — `AudioAudioRawIdTrackedDto`, оба ключа **обязательные**: `SRC C9245e.java:9-11`,
`SRC C11515e.java:54-58`.
Место вызова маппит `items → audio_id` и отдаёт наружу `List<String>`
(`SRC AbstractC18159e.java:104-121`). Не `{count, items}`.

**Уверенность:** подтверждено.

### DTO

Новых не нужно — в LMG-VK они уже есть и совпадают точно:
`VkMethodsRegistry.AudioRawId(@Json("audio_id") audioId, @Json("track_code") trackCode)` и
`VkMethodsRegistry.AudioIdsResponse(audios)`
(`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/methods/VkMethodsRegistry.kt:705-712`).

### Расхождение с кодом LMG-VK

Параметры и форма ответа **совпадают полностью**. Единственное улучшение — сузить `source: String`
до enum из восьми подтверждённых значений, чтобы опечатка не уходила на сервер.

---

## Сводная таблица: метод → DTO → готовность к порту

| # | Метод VK | Параметры | Ответ | Нужные DTO | Готовность |
|---|---|---|---|---|---|
| 1 | `audio.add` | подтверждено | не найдено в доках | — (`Unit`) | готов; уже в коде, совпадает |
| 2 | `audio.delete` | подтверждено | не найдено в доках | — (`Unit`) | готов; уже в коде, совпадает |
| 3 | `audio.restore` | подтверждено | подтверждено: `AudioAudioDto` (39 ключей) | расширить `AudioAudioDto` (+19 полей) | готов; в коде есть оба варианта |
| 4 | `audio.addDislike` | подтверждено | подтверждено: `AudioAudioDto` | переиспользовать `AudioAudioDto` | готов; в коде парсится как `Unit` |
| 5 | `audio.removeDislike` | подтверждено | не найдено в доках | — (`Unit`) | готов; уже в коде, совпадает |
| 6 | `audio.reorderInPlaylist` | подтверждено (`playlist_id`, `owner_id`, `actions`) | частично (парсить `Unit`) | `AudioPlaylistReorderAction` (не JSON-DTO, позиционный массив) | **в коде неполно — нет `actions`** |
| 7 | `audio.searchMain` | подтверждено | подтверждено: 7 секций `{count, items}` | — (всё есть) | готов; уже в коде, совпадает |
| 8 | `audio.searchArtists` | подтверждено | подтверждено: `{count, items}` = `VkItems<AudioArtistDto>` | — (заменить `VkArtistDto` → `AudioArtistDto`) | готов; уже в коде |
| 9 | `audio.getRelatedArtistsById` | подтверждено | подтверждено: `{artists: [...]}` | — (уточнить элемент `AudioRelatedArtistsResponse`) | готов; уже в коде |
| 10 | `audio.getStreamMixSettings` | подтверждено | подтверждено (4 уровня, все поля обязательны) | — (всё есть) | готов; уже в коде, совпадает |
| 11 | `audio.recommendationsOnboarding` | подтверждено (параметров нет) | частично | — (вероятно `VkItems<AudioArtistDto>`) | **в коде отсутствует** |
| 12 | `audio.getAudioIdsBySource` | подтверждено (+ enum `source`) | подтверждено: `{audios:[{audio_id, track_code}]}` | — (всё есть) | готов; уже в коде, совпадает |

**Итого новых Kotlin-классов:** один вспомогательный — `AudioPlaylistReorderAction`
(не сериализуется Moshi). Плюс расширение существующего `AudioAudioDto` до 39 полей.

---

## Пробелы

Чего в доках нет и что придётся выяснять экспериментально на живом API.

1. **Форма ответа `audio.add`.** Адаптер `C4524e.f9811e` известен, но соответствие
   «f-поле → case диспетчера» в доках не выписано (`P3:153` даёт только список типов класса).
   Место вызова ответ выбрасывает. Проверить: возвращается `1`/`Int` или объект трека.
2. **Форма ответа `audio.delete`.** То же самое: `C9616e.f19066e` известен, тип ответа для
   `case 13` не выписан (`P3:162`).
3. **Форма ответа `audio.removeDislike`.** `C17647e.f34581e`, `case 13` подтверждён (`P3:134`),
   тип DTO — нет. Соблазн взять `AudioAudioDto` по симметрии с `addDislike` — не подтверждён.
4. **Форма ответа `audio.reorderInPlaylist`.** Диспетчер объявляет `C7555e`, а `C7555e` при
   проверке оказался `AudioGetPlaylistsResponseDto` (`SRC C11382e.java:11-17`) — то есть либо
   ошибка имени в `P1:309`, либо артефакт слияния case-веток jadx. Реальную форму (скорее всего
   `1`) надо смотреть на живом API.
5. **Семантика `newIndex` в `actions` у `audio.reorderInPlaylist`.** Подтверждено только имя поля
   и позиция в массиве; что означает `-1` (дефолт двухаргументного конструктора `C1591e`) —
   «в конец», «не менять» или что-то ещё — в доках нет. Порядок элементов
   `[trackOwnerId, trackId, newIndex]` подтверждён кодом, но перепутать owner и track местами
   при таком позиционном формате очень легко — проверять на тестовом плейлисте.
6. **Форма ответа `audio.recommendationsOnboarding`.** `P1:241` (одиночный `AudioArtistDto`)
   против места вызова (`{count, items}`). Разрешается одним живым запросом с пустым query.
7. **Верхняя граница `count`.** `count <= 300` для `audio.searchMain` — это клиентский
   `coerceIn(0..300)` внутри VK X, а не задокументированный серверный лимит. Для
   `audio.searchArtists` (100) и `audio.getRelatedArtistsById` (10) в коде вообще нет клампа —
   это просто литералы конкретного экрана, границы неизвестны.
8. **Типы значений вложенных полей `AudioAudioDto`.** kotlinx-дескриптор `C14729e` даёт имена
   ключей и `isOptional`, но не Kotlin-типы. Для `album`, `main_artists`, `featured_artists`,
   `podcast_info`, `audio_chart_info`, `mstcp_type`, `performer`, `release_id`, `track_id`
   типы доками не подтверждены.
9. **`audio.searchMain` с ненулевым `offset`.** Все найденные места вызова шлют `offset = 0`;
   поведение пагинации (в частности, применяется ли offset ко всем 7 секциям сразу) не проверено.
10. **Имя метода в P2 указано неверно.** `P2:14` и `P2:145` называют `AudioSearchMainResponseDto`
    ответом `audio.search`; по адаптер-карте `EP:1485-1487` и `P1:50` `C6207e` привязан к
    `audio.searchMain` (`C11047e.f21907e`), а `audio.search` использует `C5438e.f11690e` и
    возвращает обычный `{count, items}` с треками. При портировании ориентироваться на
    `EP`/`P1`, а не на `P2`.
11. **Массовые (`execute`) варианты `audio.add`/`audio.delete`.** Формат `code` подтверждён
    (`EP:1413-1428`), но лимит числа операций в одном `execute` и формат ответа (массив
    результатов?) в доках отсутствуют.
