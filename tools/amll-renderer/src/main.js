import { LyricPlayer } from "@applemusic-like-lyrics/core";
import "@applemusic-like-lyrics/core/style.css";
import { parseTTML } from "@applemusic-like-lyrics/lyric";
import "./styles.css";

const host = document.getElementById("app");
const player = new LyricPlayer();
const playerElement = player.getElement();

host.appendChild(playerElement);
player.setEnableSpring(true);
player.setEnableScale(true);
player.setEnableBlur(true);
player.setWordFadeWidth(0.5);
player.setAlignAnchor("top");
player.setAlignPosition(0.28);
player.setOverscanPx(Math.max(window.innerHeight, 600));

let sourceLines = [];
let renderedLines = [];
let showTranslations = true;
let showPronunciations = true;
let playing = false;
let anchorPositionMs = 0;
let anchorClockMs = performance.now();
let lastFrameMs = performance.now();

function reportError(error) {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  try {
    window.Android?.onRendererError?.(message);
  } catch (_) {
    // The Android bridge is optional when the page is inspected in a browser.
  }
}

window.addEventListener("error", (event) => reportError(event.error || event.message));
window.addEventListener("unhandledrejection", (event) => reportError(event.reason));

function decodeBase64Utf8(encoded) {
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return new TextDecoder("utf-8").decode(bytes);
}

function nowPositionMs() {
  if (!playing) return anchorPositionMs;
  return anchorPositionMs + Math.max(0, performance.now() - anchorClockMs);
}

function cloneForDisplay(line) {
  return {
    ...line,
    words: (line.words || []).map((word) => ({ ...word })),
    translatedLyric: showTranslations ? line.translatedLyric || "" : "",
    romanLyric: showPronunciations ? line.romanLyric || "" : "",
  };
}

function renderLyrics(initialPositionMs, forceSeek) {
  const safePosition = Number.isFinite(initialPositionMs)
    ? Math.max(0, initialPositionMs)
    : 0;
  anchorPositionMs = safePosition;
  anchorClockMs = performance.now();
  renderedLines = sourceLines.map(cloneForDisplay);
  player.setLyricLines(renderedLines, safePosition);
  player.setCurrentTime(safePosition, forceSeek);
  player.update(0);
}

function setAnchor(positionMs, forceSeek) {
  const safePosition = Number.isFinite(positionMs) ? Math.max(0, positionMs) : 0;
  anchorPositionMs = safePosition;
  anchorClockMs = performance.now();
  player.setCurrentTime(safePosition, forceSeek);
  player.update(0);
}

function setPlaying(nextPlaying) {
  // Keep the WebView's frame clock monotonic across pause/buffering/resume.
  // The native playback snapshot may lag a frame and must not pull AMLL back
  // across a line or interlude boundary. Real seeks use setPosition(..., true).
  anchorPositionMs = Math.max(0, nowPositionMs());
  anchorClockMs = performance.now();
  playing = Boolean(nextPlaying);
  if (playing) player.resume();
  else player.pause();
  player.setCurrentTime(anchorPositionMs, false);
  player.update(0);
}

function lineAt(index) {
  return renderedLines[index] || sourceLines[index];
}

player.addEventListener("line-click", (event) => {
  event.preventDefault();
  const line = lineAt(event.lineIndex);
  const startTime = Number(line?.startTime);
  if (!Number.isFinite(startTime)) return;
  setAnchor(startTime, true);
  window.Android?.onLineClick?.(Math.round(startTime));
});

player.addEventListener("line-contextmenu", (event) => {
  event.preventDefault();
  const line = lineAt(event.lineIndex);
  const text = (line?.words || []).map((word) => word.word || "").join("");
  if (text) window.Android?.onLineLongClick?.(text);
});

window.LMG_AMLL = {
  setTtml(encodedTtml, initialPositionMs) {
    try {
      const parsed = parseTTML(decodeBase64Utf8(encodedTtml));
      sourceLines = Array.isArray(parsed?.lines) ? parsed.lines : [];
      renderLyrics(initialPositionMs, true);
    } catch (error) {
      sourceLines = [];
      renderLyrics(initialPositionMs, true);
      reportError(error);
    }
  },

  setLines(encodedJson, initialPositionMs) {
    try {
      const parsed = JSON.parse(decodeBase64Utf8(encodedJson));
      sourceLines = Array.isArray(parsed) ? parsed : [];
      renderLyrics(initialPositionMs, true);
    } catch (error) {
      sourceLines = [];
      renderLyrics(initialPositionMs, true);
      reportError(error);
    }
  },

  setDisplayOptions(translations, pronunciations) {
    const nextTranslations = Boolean(translations);
    const nextPronunciations = Boolean(pronunciations);
    if (
      nextTranslations === showTranslations &&
      nextPronunciations === showPronunciations
    ) {
      return;
    }
    showTranslations = nextTranslations;
    showPronunciations = nextPronunciations;
    renderLyrics(nowPositionMs(), true);
  },

  setPlaying,
  setPosition(positionMs, forceSeek = false) {
    setAnchor(positionMs, Boolean(forceSeek));
  },
};

function frame(frameTimeMs) {
  const deltaMs = Math.max(0, Math.min(64, frameTimeMs - lastFrameMs));
  lastFrameMs = frameTimeMs;
  if (playing) player.setCurrentTime(nowPositionMs(), false);
  player.update(deltaMs);
  requestAnimationFrame(frame);
}

document.addEventListener("visibilitychange", () => {
  lastFrameMs = performance.now();
});

requestAnimationFrame(frame);
window.Android?.onPageReady?.();
