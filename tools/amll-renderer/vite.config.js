import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

const upstreamRoot = process.env.AMLL_SOURCE_ROOT;
const useUpstreamSource =
  upstreamRoot && existsSync(resolve(upstreamRoot, "packages/core/src/index.ts"));

const upstreamAliases = useUpstreamSource
  ? [
      {
        find: /^@applemusic-like-lyrics\/core\/style\.css$/,
        replacement: resolve(upstreamRoot, "packages/core/src/styles/index.css"),
      },
      {
        find: /^@applemusic-like-lyrics\/core$/,
        replacement: resolve(upstreamRoot, "packages/core/src/index.ts"),
      },
      {
        find: /^@applemusic-like-lyrics\/lyric$/,
        replacement: resolve(upstreamRoot, "packages/lyric/src/index.ts"),
      },
      {
        find: /^@applemusic-like-lyrics\/ttml$/,
        replacement: resolve(upstreamRoot, "packages/ttml/src/index.ts"),
      },
    ]
  : [];

export default defineConfig({
  base: "./",
  plugins: [viteSingleFile()],
  resolve: {
    alias: upstreamAliases,
  },
  build: {
    outDir: "../../app/src/main/assets/amll",
    emptyOutDir: true,
    target: "chrome100",
    cssMinify: true,
    minify: "esbuild",
  },
});
