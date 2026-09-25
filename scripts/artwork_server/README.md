# Moscow artwork lookup fix — 2026-09-24

`moscow-artwork.patch` records the deployed delta against the server sources inspected on this date, not a replacement for the server deployment repository. Preserve later server edits when applying it. `artwork_matching.py` is installed beside `/root/am_ttml_proxy.py`. No credentials are included.

Changes: strict title/version/artist matching with optional duration ±6 seconds; accents/case/featured artists normalized; transport failures do not become negative results; duration and schema in cache identity; confirmed misses expire after 15 minutes; cached requests do not launch Chromium; existing catalog token is reused until authorization failure; original encoded query is forwarded exactly once. Android requests `include_mp4=0`, avoiding extra HLS fetches solely for deriving optional MP4 URLs; existing clients retain MP4 behavior.

Server modules were backed up under `/root/artwork-backup-20260924-103958`, then only `am-ttml-proxy` and `lyrics` were restarted. HLS optimization has an additional `/root/am_ttml_proxy.before-hls-optimization.py` backup. Caddy and other services were not changed.

Run isolated selection/cache tests without network or browser:

```sh
ARTWORK_PROXY_SOURCE=/path/to/patched/am_ttml_proxy.py python3 scripts/artwork_server/test_artwork_server.py
```

Live smoke: Bruised Sky / Poppy and SÃO PAULO / The Weeknd resolve to correct metadata, covers, tall and square motion. Without MP4 extraction, cold query responses measured 1.27–5.95 seconds across raw and canonical queries with duration; warm responses 0.47–0.72 seconds on the build host. Both 1200px covers returned HTTP 200 and decoded; HLS media segments returned HTTP 206 for byte ranges. These are not phone UI measurements.

Follow-up: actual phone query Bruised Skу contained Cyrillic U+0443. Mixed-word lookalike repair was added to the helper and Android normalization. The exact request previously returned not_found and now returns cover+motion. The proxy module did not need further changes; only am-ttml-proxy was restarted. Helper backup: /root/artwork_matching.before-homoglyph.py.
