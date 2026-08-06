# 03 — Подкасты и аудиокниги: спецификация для порта в LMG-VK

Группа методов: `podcasts.subscribe`, `podcasts.unsubscribe`, `execute.getPodcastEpisodesWithInfo`
(он же `PodcastGetProfilePage`), `audioBooks.addToFavorites`, `audioBooks.deleteFromFavorites`,
`audioBooks.getAudioBookById`, `audioBooks.setProgress`.

Всё ниже опирается только на дословные фрагменты из реверс-инжиниринговых документов и
декомпилированных исходников. Там, где фрагмента нет, стоит явное «не найдено в доках».

## 0. Предварительные замечания

### 0.1. Транспорт (общий для всех методов)

`C5577e` = обёртка вызова: `ad` — имя метода, `vip` — парсер ответа, `billing` — `LinkedHashMap`
параметров, `metrica` — версия API (по умолчанию `"5.272"`).
Базовый URL — `https://api.<домен>/method/<имя_метода>`.

Источник: `/storage/emulated/0/Download/VKLMG_Recovery/VKX-ENDPOINTS.md:6-42` (раздел 1) и
`/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/C5577e.java` (весь файл, 42 строки).

Хелперы установки параметров (`src-deobf/C5577e.java:21-40`) — именно по ним определяется тип параметра:

| хелпер | сигнатура | смысл |
|---|---|---|
| `ad(name, value)` | `(String, String)` | строковый параметр; `null` **не** добавляется |
| `license(name, bool)` | `(String, boolean)` | булев → `"1"`/`"0"` |
| `metrica(value, name)` | `(long, String)` | **long**-параметр (порядок аргументов обратный!) |
| `vip(value, name)` | `(int, String)` | **int**-параметр (порядок аргументов обратный!) |

Это соответствует `VkMethod.param(String, String?/Boolean/Long/Int)` в LMG-VK
(`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/VkMethod.kt:38-52`).

### 0.2. ВАЖНО: заглушки в LMG-VK уже есть

`_already-implemented.txt` эти методы не перечисляет, но в
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/methods/VkMethodsRegistry.kt:126-186`
**уже лежат нетипизированные обёртки всех семи** (`executeUnit(...)` / `execute<Any>(...)`).
То есть порт = не «завести метод», а «дать методу типизированный парсер и DTO».
Строки:

| метод | текущая обёртка | строка |
|---|---|---|
| `audioBooks.addToFavorites` | `executeUnit` + `…Detailed` → `BaseResult` | 128-138 |
| `audioBooks.deleteFromFavorites` | `executeUnit` (типизированного варианта нет) | 140-141 |
| `audioBooks.getAudioBookById` | `execute<Any>` | 143-144 |
| `audioBooks.setProgress` | `executeUnit` + `…Detailed` → `BaseResult` | 146-163 |
| `podcasts.subscribe` | `executeUnit` | 167-168 |
| `podcasts.unsubscribe` | `executeUnit` | 170-171 |
| `execute.getPodcastEpisodesWithInfo` | `execute<Any>` | 178-185 |

### 0.3. Две генерации DTO в исходнике VK X

В APK сосуществуют два слоя моделей, и их JSON-ключи **различаются**:

1. **`ua.itaysonlab.vkapi2.*`** — Moshi, необфусцированный, старый слой
   (`VKX-ENDPOINTS.md:1430-1440`, раздел 5). Именно из него `PodcastGetProfilePage`.
2. **`bruhcollective.itaysonlab.vkapi.objects.*`** — kotlinx.serialization, новый слой;
   ключи восстановлены дословно из `PluginGeneratedSerialDescriptor` (вызовы `advert("ключ", isOptional)`).
   Именно он используется ответами `audioBooks.*`.

Для аудиокниг ниже приведён **новый** слой (он подтверждён как тип ответа `audioBooks.getAudioBookById`),
а старый vkapi2-`AudioBook` показан отдельно как источник расхождений.

---

## 1. `podcasts.subscribe`

- **Имя метода в строке запроса:** `podcasts.subscribe`
- **Класс-обёртка:** `C2122e` (suspend-лямбда, `extends AbstractC7185e implements Function2`)
- **Уверенность:** подтверждено (параметры и имя), частично (форма ответа — см. ниже)

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `owner_id` | `Long` | обязательный (единственный переданный) | нет |

Дословно (`VKX-ENDPOINTS.md:1119-1126`, фрагмент помечен `C2122e.java.java:581`):

```java
defpackage.C5577e c5577e2 = new defpackage.C5577e("podcasts.subscribe", defpackage.C12575e.f25229e);
c5577e2.metrica(j, "owner_id");
```

`metrica(long, name)` ⇒ `owner_id` — **long**. Других `.ad/.vip/.license` вызовов на этом объекте
во фрагменте нет ⇒ параметр ровно один. Идентификатора самого подкаста в вызове нет — подписка
на **владельца** (сообщество-подкастера), не на конкретный выпуск.

### Форма ответа

Парсер — `C12575e.f25229e` (`VKX-ENDPOINTS.md:1515`, `adapter-map.txt`, строка `podcasts.subscribe`).
`C12575e` обслуживает ровно три VK-запроса с DTO `C14475e`, `C2654e`, `EnumC6664e`
(`PRIORITY3-RECOVERY.md:86`). Из них:

- `C14475e` = `AudioGetAudioIdsBySourceResponseDto` (`src-deobf/C14475e.java:51`) → `audio.getAudioIdsBySource`;
- `C2654e` = `AuthGetExchangeTokenResponseDto` (`src-deobf/C2654e.java:54`) → `auth.getExchangeToken`;
- остаётся `EnumC6664e` → `podcasts.subscribe`.

`EnumC6664e` = `bruhcollective.itaysonlab.vkapi.objects.base.BaseBoolIntDto`, значения
сериализуются как `"0"` / `"1"` (`src-deobf/C14582e.java:222-229`; сам enum — `src-deobf/EnumC6664e.java:10-14`, константы `NO`, `YES`).

⇒ Ответ: `{"response": 1}` — голый bool-int, **без** обёртки `{result: …}`.

**Уверенность формы ответа: частично** — тип получен методом исключения (три известных DTO минус
два однозначно привязанных), прямого фрагмента `case … → EnumC6664e` для `podcasts.subscribe` в доках нет.

### Какие DTO создавать

**Ни одного.** В LMG-VK уже есть `BaseBoolInt` (`dto/Priority3Dtos.kt:25-31`) с
`@Json(name = "0") NO` / `@Json(name = "1") YES`. Достаточно
`MoshiEnvelopeParser<BaseBoolInt>(BaseBoolInt::class.java)`.

---

## 2. `podcasts.unsubscribe`

- **Имя метода:** `podcasts.unsubscribe`
- **Класс-обёртка:** `C2122e` (тот же, что и subscribe — одна лямбда с ветвлением по флагу)
- **Уверенность:** подтверждено (параметры), частично (форма ответа)

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `owner_id` | `Long` | обязательный | нет |

Дословно (`VKX-ENDPOINTS.md:1140-1147`, фрагмент `C2122e.java.java:570`):

```java
defpackage.C5577e c5577e = new defpackage.C5577e("podcasts.unsubscribe", defpackage.C5107e.f10966e);
c5577e.metrica(j, "owner_id");
```

### Форма ответа

Парсер — `C5107e.f10966e` (`VKX-ENDPOINTS.md:1516`, `adapter-map.txt`).
`PRIORITY3-RECOVERY.md:91-96` про `C5107e`: `case 12` — `audio.getPlaylistById`, `case 13` —
`auth.getExchangeToken`, `case 14` — `podcasts.unsubscribe`, `default: generic с сериализатором EnumC6664e`.
Единственный оставшийся DTO — `EnumC6664e` (`BaseBoolIntDto`), симметрично `subscribe`.

⇒ Ответ: `{"response": 1}`.

**Уверенность: частично** — jadx слил `case 14` с `default`, точной привязки в доках нет.

### Какие DTO создавать

Ни одного — `BaseBoolInt` уже есть.

---

## 3. `PodcastGetProfilePage` → реальный метод `execute.getPodcastEpisodesWithInfo`

- **Имя метода в строке запроса:** `execute.getPodcastEpisodesWithInfo`
- **Уверенность:** подтверждено

### Как установлено имя

`/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/C4600e.java:225-238` (конструктор `C4600e(long l)`, `purchase = 24`):

```java
super((Type)((Object)PodcastGetProfilePage.PodcastPage.class));
this.billing = "execute";
this.yandex  = "getPodcastEpisodesWithInfo";
this.Signature("owner_id", (Long)number);
this.smaato((Integer)100, "count");
this.smaato((Integer)0,   "offset");
this.smaato((Integer)4,   "func_v");
```

Имя метода собирается базовым классом как `<namespace>.<name>`:
`/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/AbstractC18406e.java:34-44`

```java
charSequence.append(abstractC18406e.adcel());   // namespace
charSequence.append('.');
charSequence.append(abstractC18406e.mopub());   // name
… c8221e.appmetrica((String)charSequence, false, "5.272", hashMap, …)
```

⇒ `execute.getPodcastEpisodesWithInfo`, версия API `5.272`. Это **stored-функция** VK
(признак — `func_v=4`), а не `execute` с параметром `code` (для сравнения: `SearchInProfile`,
`C4600e.java:265-277`, имеет `yandex = ""` и передаёт `code`).
Дублирующее подтверждение: `PRIORITY1-RECOVERY.md:225` — «`execute` (getPodcastEpisodesWithInfo) |
owner_id, count=100, offset=0, func_v=4 → PodcastPage».
Реестр vkapi2: `VKX-ENDPOINTS.md:1439` — `methods/podcasts/PodcastGetProfilePage` → PodcastPage/PodcastPageInfo.

В LMG-VK имя уже угадано верно (`VkMethodsRegistry.kt:180`), включая все 4 параметра.

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `owner_id` | `Long` | обязательный (аргумент конструктора) | нет |
| `count` | `Int` | всегда передаётся | `100` (жёстко зашито) |
| `offset` | `Int` | всегда передаётся | `0` (жёстко зашито) |
| `func_v` | `Int` | всегда передаётся | `4` (жёстко зашито) |

Пагинация в самом VK X не используется (`offset` константа `0`), но параметр существует.

### Форма ответа — `PodcastPage`

`src-deobf/ua_itaysonlab_vkapi2_methods_podcasts_PodcastGetProfilePage_PodcastPageJsonAdapter.java:17-32`:

```java
C16911e.firebase(new String[]{"info", "popular", "recent"});
… vip(PodcastGetProfilePage$PodcastPageInfo.class, …, "info");
… purchase(VKResponseWithItems.class, new Type[]{ purchase(List.class, AudioTrack.class) });   // для "popular" и "recent"
```

| поле | тип | nullability |
|---|---|---|
| `info` | `PodcastPageInfo` | non-null (дефолт — пустой объект, `…$PodcastPage.java:22-31`) |
| `popular` | `VKResponseWithItems<List<AudioTrack>>` | non-null (дефолт — пустой) |
| `recent` | `VKResponseWithItems<List<AudioTrack>>` | non-null (дефолт — пустой) |

`VKResponseWithItems` (`src-deobf/ua_itaysonlab_vkapi2_internal_objects_VKResponseWithItemsJsonAdapter.java:17-35`
+ `…_VKResponseWithItems.java:5-18`):

| ключ | тип | nullability |
|---|---|---|
| `items` | `T` (здесь `List<AudioTrack>`) | nullable, дефолт `null` |
| `count` | `Integer` | nullable |
| `profiles` | `List<VKProfile>` | nullable |
| `groups` | `List<?>` | nullable (тип элемента в адаптере — общий `List`, элемент не уточнён) |
| `next_from` | `String` | nullable |

⇒ `popular`/`recent` **частично** ложатся на существующий `VkItems<AudioTrack>`
(`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/VkResponseParser.kt:56-60`) — там есть только
`count` + `items`; `profiles`, `groups`, `next_from` при таком маппинге теряются.
Для страницы подкаста они не нужны (`friends` живёт в `info`), так что `VkItems<AudioTrack>` — рабочий вариант.

### Форма ответа — `PodcastPageInfo`

`src-deobf/ua_itaysonlab_vkapi2_methods_podcasts_PodcastGetProfilePage_PodcastPageInfoJsonAdapter.java:22-38`
(порядок ключей) + `…$PodcastPageInfo.java:8-32` (типы полей) + `…$PodcastPageInfo.java` toString (сопоставление имя↔поле):

| ключ | тип | nullability / дефолт |
|---|---|---|
| `category` | `String` | дефолт `""` |
| `friends_text` | `String` | дефолт `""` |
| `podcast_description` | `String` | дефолт `""` |
| `name` | `String` | дефолт `""` |
| `owner_id` | `Long` | дефолт `0` |
| `podcast_cover` | `PodcastCover` | non-null, дефолт — пустой `PodcastCover` |
| `can_subscribe_podcasts` | `Boolean` | дефолт `false` |
| `is_subscribed_podcasts` | `Boolean` | дефолт `false` |
| `can_subscribe` | `Boolean` | дефолт `false` |
| `friends` | `List<VKProfile>` | тип элемента подтверждён адаптером (строка 36) |
| `trailer` | `AudioTrack?` | **nullable** (в `hashCode` явная проверка на null) |
| `url` | `String` | дефолт `""` |

### Какие DTO нужно создать

Переиспользуется: `AudioTrack` (`dto/music/AudioTrack.kt`), `VKProfile` (`dto/gen/users/VKProfile.kt`),
`PodcastCover` (`dto/podcasts/PodcastInfo.kt:25-28`), `VkItems`.

Создать (предлагаю в `dto/podcasts/`):

```kotlin
@JsonClass(generateAdapter = true)
data class PodcastPage(
    val info: PodcastPageInfo = PodcastPageInfo(),
    val popular: VkItems<AudioTrack> = VkItems(),
    val recent: VkItems<AudioTrack> = VkItems(),
)

@JsonClass(generateAdapter = true)
data class PodcastPageInfo(
    val category: String = "",
    @Json(name = "friends_text") val friendsText: String = "",
    @Json(name = "podcast_description") val podcastDescription: String = "",
    val name: String = "",
    @Json(name = "owner_id") val ownerId: Long = 0L,
    @Json(name = "podcast_cover") val podcastCover: PodcastCover = PodcastCover(),
    @Json(name = "can_subscribe_podcasts") val canSubscribePodcasts: Boolean = false,
    @Json(name = "is_subscribed_podcasts") val isSubscribedPodcasts: Boolean = false,
    @Json(name = "can_subscribe") val canSubscribe: Boolean = false,
    val friends: List<VKProfile> = emptyList(),
    val trailer: AudioTrack? = null,
    val url: String = "",
)
```

(Существующие DTO в `dto/music/` пишут поля в snake_case без `@Json`, а в `dto/gen/` — camelCase с `@Json`.
Выбрана вторая, более явная конвенция; при желании единообразия с `dto/podcasts/PodcastInfo.kt`
можно перейти на snake_case-свойства без аннотаций.)

### Найденное расхождение в существующем `PodcastCoverSize`

`src-deobf/ua_itaysonlab_vkapi2_objects_podcasts_PodcastCoverSizeJsonAdapter.java:14-21` даёт **5** ключей:
`height` (`int`), `type` (`String`), `src` (`String`), `url` (`String`), `width` (`int`).
В LMG-VK (`dto/podcasts/PodcastInfo.kt:30-35`) есть только `width`, `height`, `src` —
**отсутствуют `type` и `url`**. Стоит дополнить, иначе `podcast_cover` парсится с потерями.

Дополнительно: новый слой `PodcastCoverDto` (`src-deobf/C8655e.java:12-13`) имеет одно поле `sizes` —
совпадает с текущей моделью.

---

## 4. `audioBooks.addToFavorites`

- **Имя метода:** `audioBooks.addToFavorites`
- **Класс-обёртка:** `C1247e`
- **Уверенность:** подтверждено

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `audio_book_id` | `Int` | обязательный (единственный) | нет |

Дословно (`VKX-ENDPOINTS.md:539-546`, фрагмент `C1247e.java.java:1025`):

```java
defpackage.C5577e c5577e2 = new defpackage.C5577e("audioBooks.addToFavorites", defpackage.C16628e.f32595e);
c5577e2.vip(i16, "audio_book_id");
```

`vip(int, name)` ⇒ **int**. Соседний фрагмент (`VKX-ENDPOINTS.md:571-577`) показывает происхождение
значения: `int i16 = c6943e.mopub;` — это поле `id` объекта `AudioBookDto` (см. §6).

### Форма ответа

Парсер `C16628e.f32595e` (`VKX-ENDPOINTS.md:1490`, `adapter-map.txt`).
`PRIORITY3-RECOVERY.md:122`: «case 12: `audioBooks.addToFavorites` → C2610e = **AudioBooksSetProgressDto**».
`C2610e` фактически — `bruhcollective.itaysonlab.vkapi.objects.base.BaseResultDto`
(`src-deobf/C2610e.java:52` — `toString` начинается с `"BaseResultDto(result="`;
дескриптор — `src-deobf/C9281e.java:9-10`, один обязательный ключ `result`).
Тип `result` — `EnumC6664e` = `BaseBoolIntDto` (`"0"`/`"1"`, `src-deobf/C14582e.java:222-229`).

⇒ Ответ: `{"response": {"result": 1}}`.

| поле | тип | nullability |
|---|---|---|
| `result` | `BaseBoolInt` (`"0"`/`"1"`) | обязательное (`isOptional = false`) |

### Какие DTO создавать

**Ни одного.** `BaseResult` + `BaseBoolInt` уже есть (`dto/Priority3Dtos.kt:25-37`),
и `audioBookAddToFavoritesDetailed` (`VkMethodsRegistry.kt:132-138`) их уже использует корректно.

---

## 5. `audioBooks.deleteFromFavorites`

- **Имя метода:** `audioBooks.deleteFromFavorites`
- **Класс-обёртка:** `C1247e` (тот же, ветка «выключить»)
- **Уверенность:** подтверждено (параметры), частично (форма ответа)

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `audio_book_id` | `Int` | обязательный | нет |

Дословно (`VKX-ENDPOINTS.md:560-567`, фрагмент `C1247e.java.java:1013`):

```java
defpackage.C5577e c5577e = new defpackage.C5577e("audioBooks.deleteFromFavorites", defpackage.C5438e.f11684e);
c5577e.vip(i15, "audio_book_id");
```

### Форма ответа

Парсер `C5438e.f11684e` (`VKX-ENDPOINTS.md:1491`, `adapter-map.txt`).
`PRIORITY3-RECOVERY.md:147`: «C5438e … `mo600this`: case 12 → RawVkResponse<**C2610e**>
(AudioBooksSetProgressDto), case 13 → C12896e, case 14 → C15175e».
`case 13` = `audio.search`, `case 14` = `ecosystem.checkOtp` / `users.get`
(по `adapter-map.txt`: `C5438e.f11690e` = `audio.search`, `f11681e` = `ecosystem.checkOtp`,
`f11675e` = `users.get`), ⇒ `deleteFromFavorites` = `C2610e` = `BaseResultDto`.

⇒ Ответ: `{"response": {"result": 1}}` — как у `addToFavorites`.

**Уверенность: частично** — прямой строки «`deleteFromFavorites` → C2610e» в доках нет, вывод по
номеру case внутри `C5438e` и симметрии с парной операцией.

### Какие DTO создавать

Ни одного. Нужен лишь типизированный вариант обёртки (сейчас в `VkMethodsRegistry.kt:140-141`
только `executeUnit`, `…Detailed`-варианта нет — в отличие от `addToFavorites`).

---

## 6. `audioBooks.getAudioBookById`

- **Имя метода:** `audioBooks.getAudioBookById`
- **Класс-обёртка:** `C11210e`
- **Уверенность:** подтверждено

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `audio_book_id` | `Int` | обязательный (единственный) | нет |

Дословно (`VKX-ENDPOINTS.md:581-588`, фрагмент `C11210e.java.java:273`):

```java
defpackage.C5577e c5577e = new defpackage.C5577e("audioBooks.getAudioBookById", defpackage.C4590e.f9878e);
c5577e.vip(this.f22471e, "audio_book_id");
```

### Форма ответа

Парсер `C4590e.f9878e`; `PRIORITY1-RECOVERY.md:76`: «f9878e | 12 | `audioBooks.getAudioBookById`
→ RawVkResponse<**C4189e**>»; `PRIORITY1-RECOVERY.md:309`: «C4189e = AudioBookDto».
`C4189e` — обёртка: `src-deobf/C4189e.java:45-52` — `toString` = `"AudioBookResultDto(audioBook=…)"`;
дескриптор `src-deobf/C17884e.java:9-10` — один обязательный ключ **`audio_book`**.

⇒ Ответ: `{"response": {"audio_book": { …AudioBookDto… }}}`.
Это **не** `{count, items}` — `VkItems` здесь неприменим.

#### `AudioBookDto` — 23 поля, ключи дословно

Дескриптор: `/storage/emulated/0/Download/VKLMG_Recovery/src-deobf/C4105e.java:11-36`
(`"bruhcollective.itaysonlab.vkapi.objects.audiobook.AudioBookDto"`, 23 элемента;
второй аргумент `advert(…)` — `isOptional`).
Типы полей: `src-deobf/C6943e.java:5-27`; сопоставление имя↔поле: `src-deobf/C6943e.java:464-533` (`toString`).

| ключ | тип | обязательное? | примечание |
|---|---|---|---|
| `access_status` | enum `free`\|`paid`\|`started` | да (`isOptional=false`) | `src-deobf/C9283e.java:98-104` |
| `annotation` | `String` | нет | |
| `authors` | `List<AudioBooksItemPersonDto>` | нет | |
| `chapters` | `List<AudioBooksChapterDto>` | нет | |
| `code` | `String` | нет | |
| `copyright` | `String` | нет | |
| `cover` | `List<AudioBooksImageDto>` | нет | |
| `duration` | `Int` | да | секунды (тип не указан в доках явно, поле `int`) |
| `file_size` | `Long` | да | |
| `genres` | `List<AudioBooksGenreDto>` | нет | |
| `id` | `Int` | да | значение для `audio_book_id` |
| `in_favorites` | `Boolean` | нет | |
| `is_explicit` | `Boolean` | нет | |
| `main_genre` | `AudioBooksGenreDto` | нет | |
| `minimum_age` | `Int?` | нет | поле объявлено как `Integer` ⇒ nullable |
| `narrators` | `List<AudioBooksItemPersonDto>` | нет | |
| `progress_percentage` | `Int` | нет | |
| `publisher` | `AudioBooksPublisherDto` | да | |
| `release_date` | `Int` | нет | unix-time (тип `int`; семантика в доках не указана) |
| `title` | `String` | нет | |
| `track_code` | `String` | нет | |
| `translators` | `List<AudioBooksItemPersonDto>` | нет | |
| `updated_at` | `Int` | нет | |

Типы элементов списков (`authors`/`narrators`/`translators` → `AudioBooksItemPersonDto`) подтверждены
`src-deobf/C9283e.java:105-115` (case 16/17 — `C13913e` = сериализатор `AudioBooksItemPersonDto`).

#### Вложенные DTO (все ключи дословно из дескрипторов)

**`AudioBooksChapterDto`** — `src-deobf/C1317e.java:9-17`; типы — `src-deobf/C2567e.java:3-12` + `:133-153`:

| ключ | тип | обязательное? |
|---|---|---|
| `audio_file` | `AudioBooksAudioFileDto` | да |
| `id` | `String` | да |
| `progress_status` | enum `done`\|`in_progress`\|`unread` (`src-deobf/C9283e.java:86-92`) | да |
| `progress_time` | `Int` | нет |
| `special_project_id` | `Int?` | нет (поле `Integer`) |
| `title` | `String` | да |
| `track_code` | `String` | да |

**`AudioBooksAudioFileDto`** — `src-deobf/C3879e.java:9-12`; типы — `src-deobf/C15669e.java:3-7` + `:67`:
`duration` (`Int`, обяз.), `file_size` (`Long`, обяз.), `url` (`String`, обяз.).

**`AudioBooksItemPersonDto`** — `src-deobf/C13913e.java:11-18`; типы — `src-deobf/C2662e.java:3-9` + `:141-160`:
`description` (`String`), `id` (`Int?`), `legal_notice` (`AudioBooksLegalNoticeDto`),
`name` (`String`), `photo` (`List<AudioBooksImageDto>`), `roles` (`List<AudioBooksItemPersonRoleDto>`) — все `isOptional=true`.

**`AudioBooksLegalNoticeDto`** — `src-deobf/C14288e.java:9-12`; типы — `src-deobf/C18243e.java:3-5`:
`title` (`String`), `text` (`String`), оба опциональные.

**`AudioBooksItemPersonRoleDto`** — `src-deobf/C3316e.java:9-10`; тип — `src-deobf/C0430e.java:3-4`:
`id` (`String`, обязательное).

**`AudioBooksImageDto`** — `src-deobf/C16030e.java:9-12`; типы — `src-deobf/C4059e.java:3-6` + `:66-73`:
`height` (`Int`), `url` (`String`), `width` (`Int`) — все обязательные.

**`AudioBooksPublisherDto`** — `src-deobf/C4964e.java:9-11`; типы — `src-deobf/C7715e.java:3-5` + `:54`:
`id` (`Int`), `name` (`String`) — оба обязательные.

**`AudioBooksGenreDto`** — `src-deobf/C1442e.java:9-11`; типы — `src-deobf/C3367e.java:3-5` + `:54`:
`id` (`Int`), `name` (`String`) — оба обязательные.

### Что уже есть в LMG-VK и что не так

`dto/gen/music/AudioBook.kt` — автовосстановленный DTO из **старого** vkapi2-Moshi-адаптера
(`src-deobf/ua_itaysonlab_vkapi2_objects_music_AudioBookJsonAdapter.java`, 19 ключей).
Отличия от подтверждённого ответа `getAudioBookById`:

1. Нет полей `annotation`, `file_size`, `main_genre`, `updated_at` (4 из 23).
2. `narrators`, `translators`, `genres`, `authors`, `cover`, `chapters` объявлены как `List<Any?>` — типов нет.
3. `publisher: Link?` ссылается на `dto/gen/music/Link.kt`, а это **`AudioStreamMix.Link` (`id: String`, `title: String`)**,
   тогда как аудиокнижный publisher — `id: Int`, `name: String` (`src-deobf/C7715e.java:3-5`;
   старый vkapi2-аналог — `…AudioBook_LinkJsonAdapter.java:14-21`, ключи `id`/`name`). **Это ошибка типа.**
4. `access_status: String?` и `progress_status` — стоит завести enum'ами (`free/paid/started`, `done/in_progress/unread`).

`dto/gen/music/AudioBookPerson.kt` (5 ключей: `description`, `id`, `name`, `photo`, `roles`) —
в новом дескрипторе ключей **6**: не хватает `legal_notice`; `photo`/`roles` — `List<Any?>`.

### Какие DTO нужно создать

| Kotlin-класс | ключи |
|---|---|
| `AudioBookResult` | `@Json("audio_book") val audioBook: AudioBook` |
| `AudioBook` (переписать `dto/gen/music/AudioBook.kt` под 23 поля) | см. таблицу выше |
| `AudioBookChapter` | `audio_file`, `id`, `progress_status`, `progress_time`, `special_project_id`, `title`, `track_code` |
| `AudioBookAudioFile` | `duration`, `file_size`, `url` |
| `AudioBookImage` | `height`, `url`, `width` |
| `AudioBookPublisher` | `id`, `name` (нельзя переиспользовать `Link`) |
| `AudioBookGenre` | `id`, `name` (структурно совпадает с `AudioBookPublisher`, можно объединить) |
| `AudioBookLegalNotice` | `title`, `text` |
| `AudioBookPersonRole` | `id` |
| `AudioBookPerson` (дополнить существующий) | + `legal_notice`, типизировать `photo`/`roles` |
| `AudioBookAccessStatus` (enum) | `free`, `paid`, `started` |
| `AudioBookChapterProgressStatus` (enum) | `done`, `in_progress`, `unread` |

Переиспользовать из существующих `AudioTrack`/`AudioPlaylist` **нечего**: аудиокнига в VK — отдельная
сущность (`chapters` с `audio_file.url`, а не `AudioTrack`), пересечений по ключам нет.

---

## 7. `audioBooks.setProgress`

- **Имя метода:** `audioBooks.setProgress`
- **Класс-обёртка:** `AbstractC8178e` (статический хелпер `ad(C8221e, String, int, Continuation)`)
- **Уверенность:** подтверждено

### Параметры

| имя | тип | обязательность | дефолт |
|---|---|---|---|
| `chapter_id` | `String` | обязательный | нет |
| `time_from_start` | `Int` | обязательный | нет |

Дословно (`VKX-ENDPOINTS.md:602-610`, фрагмент `AbstractC8178e.java.java:13`):

```java
defpackage.C5577e c5577e = new defpackage.C5577e("audioBooks.setProgress", defpackage.C11047e.f21902e);
c5577e.ad("chapter_id", str);
c5577e.vip(i, "time_from_start");
return c8221e.license(c5577e, abstractC10731e);
```

`ad(name, String)` ⇒ `chapter_id` — **строка** (согласуется с `AudioBooksChapterDto.id: String`, §6).
`vip(int, name)` ⇒ `time_from_start` — **int**. Единица измерения (секунды/мс) **не найдена в доках**.
`audio_book_id` в вызов **не передаётся** — прогресс адресуется главой.

Сигнатура хелпера: `AbstractC8178e.ad(C8221e, String, int, AbstractC10731e)`
(`VKX-ENDPOINTS.md:1809-1822`, листинг класса).

### Форма ответа

Парсер `C11047e.f21902e`; `PRIORITY1-RECOVERY.md:49`: «f21902e | 12 | `audioBooks.setProgress`
→ RawVkResponse<**C2610e**>» = `BaseResultDto`.

⇒ Ответ: `{"response": {"result": 1}}`.

| поле | тип | nullability |
|---|---|---|
| `result` | `BaseBoolInt` (`"0"`/`"1"`) | обязательное |

### Какие DTO создавать

Ни одного — `BaseResult` уже есть и уже подключён в `audioBookSetProgressDetailed`
(`VkMethodsRegistry.kt:151-163`).

---

## 8. Сводная таблица

| Метод | Параметры | Ответ | Нужные DTO | Готовность к порту |
|---|---|---|---|---|
| `podcasts.subscribe` | `owner_id: Long` | `BaseBoolInt` (`0`/`1`) | — (`BaseBoolInt` есть) | готов; заменить `executeUnit` на типизированный парсер |
| `podcasts.unsubscribe` | `owner_id: Long` | `BaseBoolInt` | — | готов (форма ответа — частично) |
| `execute.getPodcastEpisodesWithInfo` | `owner_id: Long`, `count=100`, `offset=0`, `func_v=4` | `PodcastPage{info, popular, recent}`; `popular`/`recent` → `VkItems<AudioTrack>` | `PodcastPage`, `PodcastPageInfo` (+ дополнить `PodcastCoverSize`) | готов к порту, но бесполезен без UI |
| `audioBooks.addToFavorites` | `audio_book_id: Int` | `BaseResult{result}` | — | **уже полностью реализован** (`…Detailed`) |
| `audioBooks.deleteFromFavorites` | `audio_book_id: Int` | `BaseResult{result}` | — | готов; не хватает `…Detailed`-обёртки |
| `audioBooks.getAudioBookById` | `audio_book_id: Int` | `{audio_book: AudioBookDto}` (23 поля) | `AudioBookResult` + 11 классов/enum'ов (§6) | самый объёмный кусок; текущий `dto/gen/music/AudioBook.kt` неполон и содержит ошибку типа `publisher` |
| `audioBooks.setProgress` | `chapter_id: String`, `time_from_start: Int` | `BaseResult{result}` | — | **уже полностью реализован** (`…Detailed`) |

---

## 9. Пробелы (чего в доках нет)

1. **Точный тип ответа `podcasts.subscribe`/`unsubscribe`** — получен исключением из тройки DTO
   диспетчера, а не прямым фрагментом. Проверять живым запросом.
2. **Что именно возвращается при неудачной подписке** — обработка ответа в `C2122e` в доках не приведена
   (в листинге класса, `VKX-ENDPOINTS.md:1949-1957` / `3168`, только заголовок класса без тел методов).
3. **Единица измерения `time_from_start`** (секунды или миллисекунды) — не найдено в доках.
4. **Семантика `release_date` / `updated_at`** в `AudioBookDto` (unix-time или иное) — не найдено в доках.
5. **Тип элементов `groups`** в `VKResponseWithItems` — адаптер использует нетипизированный `List`.
6. **Пагинация страницы подкаста** — `count`/`offset` захардкожены (`100`/`0`); поддерживает ли
   stored-функция реальную пагинацию, из доков не следует.
7. **Значение `func_v=4`** — версия stored-функции; что меняется в других версиях, не найдено в доках.
8. **Где VK X берёт `audio_book_id`/`chapter_id`** для избранного и прогресса вне экрана аудиокниги —
   в доках видно только `int i16 = c6943e.mopub` (id из `AudioBookDto`); откуда приходит сам `AudioBookDto`
   в UI-потоке, не восстановлено. Кандидат — `catalog.getAudioBooks` (`PRIORITY1-RECOVERY.md:207`),
   но привязка не подтверждена.
9. **Расхождение старого vkapi2-`AudioBook` (19 ключей) и нового `AudioBookDto` (23 ключа)** — какой
   слой реально отвечает на `audioBooks.getAudioBookById` в текущей версии, подтверждено только через
   `PRIORITY1-RECOVERY.md:76` (новый, `C4189e`). Старый слой, вероятно, обслуживает каталог.
10. **`podcasts.getEpisode` / `podcasts.getRandomEpisode`** (`src-deobf/C4600e.java:242-247`,
    `:35-39`) — в моей группе их нет, но они соседи по домену и в LMG-VK уже стоят как `execute<Any>`;
    оба возвращают `AudioTrack`, то есть типизируются бесплатно.

---

## 10. Нужен ли UI

В LMG-VK **нет ни одного экрана подкастов или аудиокниг**: в
`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/ui/` (screens, player, components, navigation)
нет ни одного файла, упоминающего podcast/audiobook.
При этом каталожные обёртки `catalog.getPodcasts` и `catalog.getAudioBooks` уже есть
(`/root/LMG-VK/app/src/main/kotlin/com/lmg/vk/network/methods/VkCatalogApi.kt:98-135`), то есть
источник данных для будущих экранов частично готов.

### Встраиваются в существующий UI (порт имеет смысл прямо сейчас)

- **`podcasts.subscribe` / `podcasts.unsubscribe`** — единственный параметр `owner_id` уже есть у любого
  трека (`AudioTrack.owner_id`), а признак подкаста тоже готов: `AudioTrack.isPodcast`
  (`dto/music/AudioTrack.kt`, `podcast_info != null`). Кнопка «подписаться на подкаст» ложится
  в существующий `TrackActionsSheet.kt` и в экран плеера без нового экрана.
- **`audioBooks.setProgress`** — «встраивается в плеер» *технически*, но фактически бесполезен:
  `chapter_id` берётся только из `AudioBookDto.chapters[].id`, а получить его негде без экрана аудиокниги.
  Полезен ровно в паре с `getAudioBookById`.

### Требуют нового экрана (без него бесполезны)

- **`execute.getPodcastEpisodesWithInfo`** — возвращает целую страницу профиля подкаста
  (описание, обложка, трейлер, друзья-слушатели, «популярное», «свежее»). Нечему это отрисовать:
  нужен новый экран «Подкаст» (шапка + два списка эпизодов). Единственный частично полезный
  побочный эффект без UI — поле `is_subscribed_podcasts` как источник состояния для кнопки подписки.
- **`audioBooks.getAudioBookById`** — нужен экран «Аудиокнига» (обложка, авторы/чтецы, список глав
  с прогрессом). Ответ на 23 поля не имеет куда лечь.
- **`audioBooks.addToFavorites` / `deleteFromFavorites`** — формально встраиваются в «Библиотеку»,
  но в LMG-VK нет ни списка избранных аудиокниг, ни точки, где пользователь видит аудиокнигу
  и может её лайкнуть. Без экрана аудиокниги (или раздела «Аудиокниги» в библиотеке) вызывать нечем.

### Рекомендуемый порядок

1. Сейчас: типизировать `podcasts.subscribe`/`unsubscribe` (`BaseBoolInt`) и
   `audioBooks.deleteFromFavorites` (`BaseResult`) — нулевая стоимость, новых DTO не требуется.
2. Сейчас: починить `PodcastCoverSize` (+`type`, +`url`) и тип `AudioBook.publisher` — это баги, а не фичи.
3. Позже, вместе с экраном подкаста: `PodcastPage` / `PodcastPageInfo`.
4. Позже, вместе с экраном аудиокниги: полный набор `AudioBook*`-DTO + `setProgress` в плеере.
