# VI_NNET_ARCHITECTURE — независимый разбор `vi-nnet.mil` (Phase 11)

**Дата:** 2026-09-13. **Owner:** Final Closure / Liquid Glass + vi-nnet Analyst (research-only; файлы не изменялись).
**Объект:** MediaPlaybackCore.framework, TaskID `czutbtg4y9` (vocal attenuation / Apple Music Sing).
**Метод:** полный текстовый парсер MIL (1973 op-строки, 1015 const), сверка весов с `vi-nnet.weight.bin`,
сверка параметров запуска с `aufx-nnet-appl.plist`. Отдельный отчёт от AutoMix (в AutoMix ML нет).
**Статусы:** EXACT / PARTIAL / NOT FOUND.

---

## 0. Provenance

| Файл | Размер | sha256 | Роль |
|---|---|---|---|
| `appos/extracted/System/Library/PrivateFrameworks/MediaPlaybackCore.framework/czutbtg4y9/vi-nnet.mil` | 319,196 B | `6a0d6c7e70f5be8a4fb781db28e4d04587a9b0eb1df0fd73b7c10e71bd66c637` | MIL-программа |
| `…/czutbtg4y9/weights/vi-nnet.weight.bin` | 15,551,232 B | `d0bb9ffb724e0138ec54297e48c0d29902de50485dc7a528c353c282a4ff8554` | fp16 веса |
| `…/czutbtg4y9/aufx-nnet-appl.plist` | 1,031 B | `252c5a99ff2d52358b5d13ef3ea6bbcb73e4e3a6b733f26eb8e70802c4b5108a` | параметры AudioUnit-обвязки |

MIL-заголовок: `coremlc-component-MIL 5.33.4`, `coremlc-version 1877.0.10.505.1` (строка 2).
`main<ios15>`; `UserMetadata = {iteration: "1106", taskid: "czutbtg4y9"}` (строка 4).
Файл: 1979 строк; ops — строки 5–1977; return — строка 1978; закрытие — 1979. Байтовые offset’ы ключевых строк:
line 4 @byte 188, line 20 (первый conv) @5560, line 74 (streaming concat), line 1955 (mask head), line 1973 (decoder).

---

## 1. Сводка: подтверждение/опровержение заявленной архитектуры

| Заявленная характеристика | Вердикт | Доказательство |
|---|---|---|
| Вход stereo `[1,2,4096]` | **EXACT** | MIL line 4: `tensor<fp32,[1,2,4096]> audio`; line 1977: `target_1` fp32 `[1,2,4096]` |
| 44.1 kHz | **EXACT** (plist) | plist lines 27–28 `SampleRate=44100`; в MIL sample rate не кодируется |
| Lookahead 16384 (plist) | **EXACT** (plist) | plist lines 13–14 `LookaheadSize=16384`; в MIL задержка 511 кадров = **16352 сэмпла** (см. §6) |
| Encoder kernel 64 / stride 32 | **EXACT** | line 20: weight `[384,1,1,64]`, `strides=[1,32]`; decoder line 1973: `[384,1,1,64]`, stride 32 |
| Latent features | **EXACT** | front-end 384/канал; reshape `[1,768,128]` (line 40); `stem_to_latent` `[448,768,1]` → **448** (line 47) |
| Число TCN-like блоков / dilations | **EXACT** | `stem_sep_module_0..35` = **36 блоков**; dilations `[1,2,4,8,16,32,64,128,256] ×4` |
| State-тензоры и streaming | **EXACT** | 48 state-тензоров; механика `concat` + `slice_by_size`; **рекуррентных op нет** |
| «Рекуррентные state-тензоры» (`APPOS_ML_MODELS.md`) | **PARTIAL/CORRECTED** | state-тензоры — это буферы истории для свёрток, а не RNN/LSTM/GRU; RNN-операторов в MIL нет |
| Mask head | **EXACT** | `x_8_cast = conv [2,1,385,1]`, pad 160 (line 1955) → reshape (line 1957) → `sigmoid` (line 1958) |
| conv_transpose decoder | **EXACT** | ровно 1 `conv_transpose` (line 1973), weight `[384,1,1,64]`, stride 32 |
| BNNS (MIL2BNNS), CPU | **EXACT** (plist) | plist lines 19–20 `NeuralNetImplementationType=MIL2BNNS`, lines 9–10 `ComputeEngineName=CPU` |
| ComputeEngine CPU + StreamingMode | **EXACT** (plist) | plist line 29–30 `StreamingMode=1`, lines 5–8 `BatchSize=1`, `BlockSize=4096` |

**Формулировка:** это **stateful streaming TCN / Conv-TasNet-подобный сепаратор** (encoder conv → TCN → mask → decoder),
а не RNN/LSTM. Термин «рекуррентный» применять нельзя: в MIL нет ни `matmul`, ни `gru`/`lstm`/`rnn`, ни attention.

---

## 2. Топология (EXACT)

```
audio fp32 [1,2,4096]
  → cast fp16, expand_dims [1,1,2,4096]
  → concat(state[32]) → [1,1,2,4128]
  → stem_front_end_0: conv k=64, s=32, C=384  → [1,384,2,128]         (line 20)
  → relu → stem_front_norm (LayerNorm по C, axes=[1])                 (lines 21–39)
  → reshape [1,768,128]                                               (line 40)
  → stem_to_latent: conv 1x1 768→448 → [1,448,128]                    (line 47)
  → 36 × sep_module: (1x1 conv → LReLU → LN) → (depthwise k=3, dilation d → LReLU → LN) → +residual
  → expand_dims [1,1,448,128]                                         (line 1953)
  → stem_mask_layer: conv k=385, pad 160, C_out=2 → [1,2,384,128]     (line 1955)
  → reshape [1,384,2,128] → sigmoid → mask ∈ (0,1)                    (lines 1957–1958)
  → mul(mask, delayed encoder relu features [1,384,2,128])            (line 1962)
  → concat(decoder state[1]) → [1,384,2,129]                          (line 1969)
  → stem_resynthesizer: conv_transpose k=64, s=32, 384→1              (line 1973)
  → [1,1,2,4096] → squeeze → cast fp32 → target_1 [1,2,4096]          (lines 1977)
```

Плюс ветка задержки: `var_37_cast` (ReLU-выход front-end, `[1,384,2,128]`) сдвигается на **511 кадров** через
state `var_37_cast_elementwise_in_state [1,384,2,511]` и используется как вход маскирующего умножения.

---

## 3. Op histogram (EXACT, 1973 ops)

| Op | Кол-во | Назначение |
|---|---|---|
| `const` | 1015 | скаляры/оси/строки + 296 BLOBFILE-тензоров |
| `add` | 182 | 36 residual + 73 beta + 73 eps |
| `reduce_mean` | 146 | 73 нормы × 2 |
| `mul` | 75 | 73 gamma + 1 std-scale (front norm) + 1 mask |
| `conv` | 75 | 1 front-end + 1 latent + 36×2 (1x1 + depthwise) + 1 mask |
| `sub` | 73 | нормы (x − mean) |
| `square` | 73 | нормы |
| `sqrt` | 73 | нормы |
| `real_div` | 73 | нормы |
| `leaky_relu` | 72 | 36 блоков × 2 (PReLU-slope per-layer, inline fp16) |
| `slice_by_size` | 58 | 48 state-update + 10 delayed-view |
| `concat` | 48 | 36 depthwise-state + 9 identity-delay + 1 encoder + 1 lookahead + 1 decoder |
| `reshape` | 2 | `[1,768,128]`, mask `[1,384,2,128]` |
| `expand_dims` | 2 | encoder input, mask input |
| `cast` | 2 | audio fp32→fp16, target fp16→fp32 |
| `relu` | 1 | front-end |
| `sigmoid` | 1 | маска |
| `conv_transpose` | 1 | декодер |
| `squeeze` | 1 | target |

**Recurrent/attention ops (`matmul`, `softmax`, `lstm`, `gru`, `rnn`, `attention`, `layer_norm`) — 0 вхождений.**
Нормы реализованы вручную через `reduce_mean/sub/square/sqrt/real_div` (per-frame LayerNorm по каналу, axes=`[1]`;
front-norm: `sqrt(var)·1.00390625 + 2^-24`; блочные нормы: `sqrt(var + 2^-24)`).

---

## 4. Layer table

### 4.1. Глобальные слои (EXACT; offset — BLOBFILE в `vi-nnet.weight.bin`)

| Слой | Op | Weight shape | Offset (byte) | Data size | Выход |
|---|---|---|---|---|---|
| `stem_front_end_0` | conv k=64 s=32 | `[384,1,1,64]` | 64 | 49,152 | `[1,384,2,128]` |
| `stem_front_norm` | LN gamma/beta | `[1,384,1,1]` ×2 | 50,112 / 50,944 | 768 ×2 | `[1,384,2,128]` |
| `stem_to_latent` | conv 1×1 | `[448,768,1]` | 51,776 | 688,128 | `[1,448,128]` |
| `stem_mask_layer` | conv k=385 pad160 | `[2,1,385,1]` | 15,500,352 | 1,540 | `[1,2,384,128]` |
| `stem_resynthesizer` | conv_transpose k=64 s=32 | `[384,1,1,64]` | 15,502,016 | 49,152 | `[1,1,2,4096]` |

Inline (не BLOBFILE): `stem_mask_layer_bias [-1.65, -1.978]` fp16; `stem_resynthesizer_bias [-1.1e-6]` fp16.

### 4.2. TCN-блоки: 36 модулей (EXACT)

Правило: модуль `m` (0..35) начинается по адресу `740928 + 409984·m` (шаг блока 409,984 B = 409,472 data + 8×64 header).
Внутри модуля фиксированные относительные offset’ы:

| Weight | Shape | Rel. offset | Data size |
|---|---|---|---|
| `…_tcn_0_weight_to_fp16` (1×1 conv) | `[448,448,1]` | +0 | 401,408 |
| `…_tcn_0_bias_to_fp16` | `[448]` | +401,472 | 896 |
| `…_tcn_2_norm_gamma_to_fp16` | `[1,448,1]` | +402,432 | 896 |
| `…_tcn_2_norm_beta_to_fp16` | `[1,448,1]` | +403,392 | 896 |
| `…_tcn_4_weight_to_fp16` (depthwise k=3, groups=448) | `[448,1,3]` | +404,352 | 2,688 |
| `…_tcn_4_bias_to_fp16` | `[448]` | +407,104 | 896 |
| `…_tcn_6_norm_gamma_to_fp16` | `[1,448,1]` | +408,064 | 896 |
| `…_tcn_6_norm_beta_to_fp16` | `[1,448,1]` | +409,024 | 896 |

Dilation schedule (модуль → dilation), EXACT:

```
m 0..8  : 1, 2, 4, 8, 16, 32, 64, 128, 256     (offset base 740,928      … 4,020,800)
m 9..17 : 1, 2, 4, 8, 16, 32, 64, 128, 256     (base 4,430,784           … 7,710,656)
m 18..26: 1, 2, 4, 8, 16, 32, 64, 128, 256     (base 8,120,640           … 11,400,512)
m 27..35: 1, 2, 4, 8, 16, 32, 64, 128, 256     (base 11,810,496          … 15,090,368)
```

Блок: `x → 1×1 conv(448→448) → LReLU(α_m) → LN(tcn_2) → depthwise conv(k=3, d, groups=448) → LReLU(α_m) → LN(tcn_6) → + residual`.
Для модулей 0–8 residual-ветка дополнительно задерживается на `d` кадров (§6). Порядок conv→норма (LReLU до LN) — EXACT.

### 4.3. Полный список 75 conv / 1 conv_transpose

Структурно (все EXACT из op-строк): 1 front-end (`input0_1_cast`), 1 latent (`input1_1_cast`),
72 блочных (36 `tcn_0` + 36 `tcn_4`), 1 mask (`x_8_cast`), 1 decoder (`x_1_cast`). Полные имена/offset’ы —
§4.1–4.2; каждый `tcn_0` — `groups=1, pad=[0,0], stride=[1]`; каждый `tcn_4` — `groups=448`, `dilations=[d]`.

---

## 5. Tensor shapes (ключевые, EXACT)

| Тензор | Shape | Line |
|---|---|---|
| `audio` / `target_1` | `[1,2,4096]` fp32 | 4 / 1977 |
| encoder state | `[1,1,2,32]` fp16 | 4 |
| `input0_1_cast` (front-end) | `[1,384,2,128]` | 20 |
| `var_37_cast` (ReLU, mask-ветка) | `[1,384,2,128]` | 21 |
| `input0_2_cast` (reshape) | `[1,768,128]` | 40 |
| `input1_1_cast` (latent) | `[1,448,128]` | 47 |
| `x_6_cast` (после 36 блоков) | `[1,448,128]` | — |
| `x_8_cast` (mask conv) | `[1,2,384,128]` | 1955 |
| `input2_1_cast` / `var_2279_cast` | `[1,384,2,128]` (mask) | 1957/1958 |
| `input3_1_cast` (masked) | `[1,384,2,128]` | 1962 |
| `input3_1_cast_padded` (decoder in) | `[1,384,2,129]` | 1969 |
| `x_1_cast` (decoder out) | `[1,1,2,4096]` | 1973 |

`FlexibleShapeInformation`: `DefaultShapes {audio:[1,2,4096]}`, `RangeDims {audio:[[1,1],[2,2],[4096,4096]]}` — блок
фиксирован 4096, динамических форм нет.

---

## 6. State-тензоры и streaming behavior (EXACT)

Всего **48 входных и 48 выходных state-тензоров** (fp16); return-строка 1978 перечисляет 49 выходов
(`target_1` + 48 `*_out_state`). Механика одинакова: `concat(state, current)` → `slice_by_size` (текущий вид + новый state);
никаких рекуррентных связей.

| Роль | Кол-во | Имена/шаблон | Shapes |
|---|---|---|---|
| Encoder overlap history | 1 | `input_8_cast_in_state` | `[1,1,2,32]` (32 = k−s) |
| Depthwise conv history | 36 | cycle1 `input_{7,17,27,37,47,57,67,77,87}`; cycle2 `input_{97,107,…,177}`; cycle3 `input_{187,…,267}`; cycle4 `input_{277,…,347}` + `input_4` | `[1,448,2d]`, d = 1..256 (2,4,8,…,512) |
| Identity-delay (первые 9 модулей) | 9 | `input{1_1,13,23,33,43,53,63,73,83}_cast_elementwise_in_state` | `[1,448,d]` = 1,2,4,…,256 |
| Lookahead delay (mask-ветка) | 1 | `var_37_cast_elementwise_in_state` | `[1,384,2,511]` |
| Decoder overlap state | 1 | `input3_1_cast_in_state` | `[1,384,2,1]` |

- Depthwise conv: контекст слева `2d`; `concat([1,448,2d], [1,448,128])` → conv(k=3, dilation=d) → ровно 128 кадров;
  out_state = последние `2d` (напр. line 74–80 для d=1: concat 130 → conv → slice 2).
- Lookahead: `var_37` (ReLU front-end) конкатенируется с 511-кадровым state и «delayed» берётся как первые 128 кадров
  (line 1960-61); маска, посчитанная по текущему латенту, применяется к признакам, задержанным на 511 кадров
  (511 × hop 32 = **16,352 сэмпла** ≈ plist `LookaheadSize=16384` = 512 кадров; `512−1 = 511`).
  Связь 16384↔511 — `[STRONG_INFERENCE]` (числа EXACT).
- Блок = 4096 сэмплов → 128 латентных кадров (hop 32), frame rate 1378.125 Гц, lookahead ≈ 0.371 с.

---

## 7. Mask head и decoder (EXACT)

- **Mask head:** `x_6_cast [1,448,128]` → `expand_dims` → `[1,1,448,128]` → conv `weight=[2,1,385,1]`,
  `pad=[160,160,0,0]` → `[1,2,384,128]` → `reshape [1,384,2,128]` → `sigmoid` (мягкая маска, без масштабирования)
  → `mul` с задержанными `var_37` (`[1,384,2,128]`).
- **Decoder:** один `conv_transpose`, weight `[384,1,1,64]` (in=384, out=1, kernel 1×64), `strides=[1,32]`,
  `pad=[0,0,32,32]`, вход `[1,384,2,129]` → выход `[1,1,2,4096]` → `squeeze` → `cast fp32`.
  Совпадение k=64/s=32 с encoder → синтез-фильтр, обратный анализу (overlap-add 50 %).
- `x_1_crop_width_0=[32,16]` — константа в MIL, ни одним op не используется (мёртвая после конверсии).

---

## 8. Веса и BNNS

- BLOBFILE-тензоров **296** (2 front-end, 2 front-norm, 2 latent, 288 = 36×8 модулей, 1 mask weight, 1 resynth weight);
  ещё 2 bias (mask, resynth) — inline fp16.
- Параметров всего **7,766,085** (fp16 → 15,532,170 B данных). Раскладка файла: первый BLOBFILE @64;
  каждый следующий = prev offset + prev size + 64 (64-байтовый заголовок на тензор); после последнего тензора
  ещё 64 B → 15,551,232 B ровно. Все 296 offset’ов проверены на эту закономерность — расхождений нет.
- **BNNS:** plist явно задаёт `MIL2BNNS` + `CPU` + `StreamingMode=1`. Набор MIL-операторов
  (`conv`, `conv_transpose`, `leaky_relu`, `sigmoid`, `reduce_mean`, `slice_by_size`, `concat`, elementwise)
  транслируем в BNNS-фильтры; конкретное отображение op→BNNS-функция определяется coremlc-транслятором и в MIL
  не записано → `[STRONG_INFERENCE]` (факт MIL2BNNS — EXACT).
- Любопытно: 72 `leaky_relu` имеют **разные inline alpha** (не 0.01): напр. `0x1.284p-2`≈0.289,
  `-0x1.368p-1`≈-0.607, три значения `1.0` и три `≈0.9995`; это per-layer обучаемые PReLU-наклоны, зашитые константами.

---

## 9. Прошлые утверждения: подтверждено / исправлено

| Утверждение | Новое evidence | Статус |
|---|---|---|
| `APPOS_ML_MODELS.md`: «streaming-модель с рекуррентными state-тензорами и fp16 состояниями» | MIL: 48 state-тензоров, fp16 — верно; но state-тензоры обслуживаются `concat`+`slice_by_size`, RNN-операторов нет | **fp16 states EXACT; «рекуррентные» — CORRECTED на «stateful streaming TCN»** |
| `APPOS_ML_MODELS.md`: MIL2BNNS/CPU/Batch1/Block4096/Lookahead16384/44.1k/2ch/StreamingMode1 | plist lines 5–30 | EXACT (подтверждено) |
| «внутренняя архитектура сети не исследована» | 36 TCN-блоков, dilations ×4, mask head, conv_transpose decoder | закрыто (EXACT), кроме эффективных BNNS-план и runtime-поведения |
| kernel/stride/latent «encoder 64/32» | weight `[384,1,1,64]`, strides `[1,32]`, latent 448 | EXACT |

## 10. NOT FOUND / остаётся

- Точное соответствие MIL-op → BNNS-функции (зависит от coremlc; в MIL не записано).
- Runtime-поведение буферов (упаковка fp16, реальная задержка в сэмплах на стороне AudioUnit) — только трассировкой.
- Обучение/происхождение чекпойнта (iteration 1106) — только `TaskIteration`.
- Назначение `1.00390625` (`0x1.004p+0`) в front-norm (вероятно, поправка на округление fp16) — `[UNKNOWN]`.

## 11. Appendix — Apple Music Sing / vocal attenuation

`vi-nnet` — локальная модель подавления вокала (Apple Music Sing). Обвязка (EXACT, символы MediaPlaybackCore/DSC из
`09_appos/APPOS_ML_MODELS.md`): `MPCVocalAttenuationModel`, `MPCPlaybackEngine isVocalAttenuationAvailable/Enabled`,
`MPCPlayerItemConfigurator`, `MPCPolicyEvaluation setDisableVocalAttenuation:`, `MSVDeviceSupportsVocalAttenuation`,
MediaAPI-атрибут `isVocalAttenuationAllowed`. Реализация — AudioUnit-плагин `aufx-nnet-appl` (TaskID `czutbtg4y9`),
2-канальный поток 44.1 кГц, блок 4096, lookahead 16384, CPU/BNNS. AutoMix ML не использует (см. отчёты AutoMix).

## 12. Provenance (строки/байты)

```
vi-nnet.mil: line 4 @byte 188 (signature, FlexibleShapeInformation, UserMetadata)
             line 20 @byte 5560 (front-end conv), line 21 @byte 5868 (relu)
             line 40 @byte 8453 (reshape 768), line 47 @byte 9630 (latent conv)
             line 54 @byte 11013 (module 0 tcn_0), line 74 @byte 13960 (streaming concat), line 82 @byte 15433 (tcn_4)
             1901 @305581 (module 35 tcn_0), 1929 @310055 (module 35 tcn_4)
             1955 @313919 (mask conv), 1957 @314314 (reshape mask), 1958 @314415 (sigmoid)
             1962 @315153 (mask mul), 1969 @316322 (decoder concat), 1973 @316992 (conv_transpose), 1977 @317715 (target cast)
             1978 @317829 (return, 49 outputs), 1979 @319194 (EOF)
aufx-nnet-appl.plist: lines 5–34 (см. §0/§1)
weights/vi-nnet.weight.bin: BLOBFILE offsets — §4.1/§4.2
Evidence type: mil_text | plist | constant (bytes); Status: EXACT, если не указано иное
```
