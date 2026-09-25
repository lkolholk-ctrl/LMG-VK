# Moscow Lyrics API (ru-lyrics.gsgit.org)

Инфраструктура высокопроизводительного сервиса караоке-лирики Apple Music TTML с локальным SQLite-кэшем и авторизацией по API-ключам.

---

### Архитектура сети (100% Автономная в Москве)

```text
[Клиент: LMG-VK / VK X (iTaysonLab)]
                  │
                  ▼ HTTPS (< 15 мс по РФ)
    ┌─────────────────────────────────────────┐
    │       ru-lyrics.gsgit.org (Москва)      │
    │               31.77.173.92              │
    │       Caddy (TLS) -> Python:8780        │
    │       SQLite DB (7 547+ треков)         │
    │                   │                     │
    │      Cache Miss?  │ HTTP (< 1 мс loop)  │
    │                   ▼                     │
    │    am-ttml-proxy (127.0.0.1:8777)       │
    │  (Playwright Chromium / Headless Shell) │
    └───────────────────┬─────────────────────┘
                        │
                        ▼ HTTPS (Direct out of MSK)
             Apple Music (amp-api)
```

---

### Базовый URL
```text
https://ru-lyrics.gsgit.org
```

---

### Эндпоинты

#### 1. Получение лирики (TTML)
```http
GET /v2/lyrics/ttml?artist={artist}&title={title}&duration={seconds}
```

**Заголовки запроса:**
* `X-API-Key: {API_KEY}` (обязательно)
* *Или заголовок:* `Authorization: Bearer {API_KEY}`
* *Или query-параметр:* `?api_key={API_KEY}`

**Параметры запроса:**
* `title` *(string)* — название песни (обязательно).
* `artist` *(string)* — исполнитель (рекомендуется).
* `duration` *(float/int)* — длительность трека в секундах (рекомендуется для исключения ложных версий/ремиксов, допуск $\pm 6$ сек).
* `lang` *(string)* — язык / локализация (по умолчанию `all`).

**Ответ:**
* **Status:** `200 OK`
* **Content-Type:** `text/plain; charset=utf-8` (или XML)
* **Заголовки ответа:**
  * `X-Lyrics-Source`: `local_sqlite` (из базы Москвы) или `upstream_am` (скачано из NY и сохранено в базу).
  * `X-Lyrics-Timing`: `Word` (послоговая синхронизация), `Line` (построчная) или `None`.
  * `X-Track-Id`: ID трека в каталоге Apple Music.
  * `X-Track-Name`: Название трека.
  * `X-Artist-Name`: Исполнитель.
  * `X-Storefront`: Витрина Apple Music (например `us`).

---

#### 2. Motion Artwork (Анимированные обложки)
```http
GET /v2/motion?title={title}&artist={artist}&album={album}
```
* **Заголовки:** `X-API-Key: {API_KEY}`
* Возвращает JSON с ссылками на квадратные/вертикальные HLS (`.m3u8`) и MP4 потоки, а также базовые цвета фона.

---

#### 3. Статистика и проверка здоровья
* **Healthcheck:** `GET /ping` или `GET /health` (без ключа, возвращает `OK`).
* **Статистика ключей:** `GET /v2/keys/stats` (требует `X-API-Key`).

---

### Выпущенные API-ключи

| Владелец | Ключ | Статус |
| :--- | :--- | :---: |
| **Stanislav (Admin / Personal)** | `lmg_admin_f3ddc67f6327416e96d95c1967882be5e1bb3bba` | ACTIVE |
| **LMG-VK Android Client** | `lmg_client_ce42d2cb55f15c215e2da9a776b3c74575e5d599` | ACTIVE |
| **VK-X Client (iTaysonLab)** | `lmg_vkx_82a7f81aea0444818893500dda5285bd7fcd4757` | ACTIVE |

---

### Управление на сервере Москвы (31.77.173.92)

* **Служба systemd:** `systemctl status lyrics`
* **Рестарт службы:** `systemctl restart lyrics`
* **Логи:** `journalctl -u lyrics -f`
* **Caddy:** `/etc/caddy/Caddyfile` (`systemctl reload caddy`)
* **Управление ключами:** `python3 /root/lyrics_service/manage_keys.py [list|add|revoke]`
