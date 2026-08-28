# AMLL renderer provenance

The embedded renderer in `app/src/main/assets/amll/index.html` is generated from
the AMLL repository at commit:

- `9a1bd986c62af005bd9874fff670294a8ac1c145`
- upstream repository: <https://github.com/amll-dev/applemusic-like-lyrics>

The pinned npm packages (`core` 0.5.2 and `lyric` 1.0.2) are retained as a
reproducible fallback when an upstream source checkout is not present.

LMG VK's project owner holds a separate written permission from the relevant
rightsholder for this use. The private permission email is intentionally not
stored in this public repository.

Regenerate the single-file, offline Android asset with:

```shell
cd tools/amll-renderer
npm ci
AMLL_SOURCE_ROOT=/path/to/applemusic-like-lyrics npm run build
```
