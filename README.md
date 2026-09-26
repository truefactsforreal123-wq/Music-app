# Aura — paste a link, play it anywhere

Aura is a personal music app for Android: paste a playlist URL, get a beautiful
black & white glass player that works with **any headset** — Bluetooth, wired
3.5 mm, USB-C, lock screen, notification — and a lightweight server on your PC
that does the fetching and downloading.

```
┌─────────────────────┐        ┌──────────────────────────────┐
│  Android app        │  Wi-Fi │  Aura server (your PC)       │
│  Kotlin · Compose   │◄──────►│  Node + Fastify + SQLite     │
│  Media3 · Room      │  HTTP  │  yt-dlp · LRCLIB · Spotify   │
└─────────────────────┘        └──────────────────────────────┘
```

## What it does

- **Import by link** — paste a playlist URL. A provider plugin system figures
  out the platform: YouTube / YouTube Music / SoundCloud / anything yt-dlp
  supports, Spotify playlists (metadata + artwork via the official free API),
  direct audio URLs, and M3U playlists.
- **Full player** — play / pause / next / prev, seek, shuffle, repeat one/all,
  queue with jump-to-track, playback speed, sleep timer with fade-out,
  skip-silence, gapless-friendly streaming through a 512 MB on-device cache.
- **Downloads with real progress bars** — after fetching a playlist, long-press
  to tick checkboxes on individual tracks, hit **Download (n)**, or use
  **Download all**. Per-track progress bars in the list, an overall batch
  banner, and the system notification all show true byte progress. Downloaded
  tracks play with the server switched off.
- **Any headset, guaranteed** — the app registers Android's system media
  session (Media3), so AVRCP buttons on any Bluetooth headset, wired
  3-button remotes, USB-C headsets and keyboard media keys all control
  playback — even with the app in the background. Audio focus pauses for
  calls; unplugging headphones pauses instantly.
- **Synced lyrics** — karaoke-style highlighting from LRCLIB; tap a line to seek.
- **Library** — playlists, liked songs, recently played, most played, search
  across your library and the wider catalog, dark/light/system themes.

## Repository layout

```
server/   Fastify + TypeScript API, SQLite (node:sqlite), yt-dlp wrapper
app/      Android project (Kotlin 2.1, Compose, Media3 1.7, Room, WorkManager)
```

## 1. Run the server (your PC)

Requires Node.js 20+ (tested on Node 26). No native modules.

```bash
cd server
npm install
npm run setup     # downloads the yt-dlp binary into server/bin/
npm run dev       # or: npm start
```

On start it prints the URL to use on your phone, e.g.
`http://192.168.1.8:8787` (your PC's LAN IP). Keep your PC's firewall from
blocking Node, or allow inbound TCP 8787.

Spotify playlist import works out of the box — **no API keys, no signup, no
`.env` entries**. The server reads playlists through the same anonymous
surfaces the public Spotify web player uses (embed token + the web player's
own `fetchPlaylist` query), paged 100 tracks at a time until it reaches the
playlist's real `totalCount`, so full-length playlists import completely.

Useful endpoints: `GET /api/health`, `POST /api/fetch {url}`,
`GET /api/playlists`, `GET /api/tracks/:id/stream`,
`POST /api/tracks/:id/prepare` → `GET .../status` → `GET .../download`.

## 2. Run the app (Android)

Open `app/` in **Android Studio** (it has the SDK + JDK configured via
`local.properties` / `gradle.properties`), or build from a shell:

```bash
cd app
gradle :app:assembleDebug     # APK at app/app/build/outputs/apk/debug/
```

Then on the phone: install the APK → **Settings → Server address** → paste the
URL printed by the server → *Test connection*. The default is already
`http://192.168.1.8:8787` for this setup.

## Performance notes (what's baked in)

**Server** — SQLite in WAL mode with prepared statements and covering indexes;
bulk playlist imports run in one transaction; in-flight request de-duplication
for identical fetches; stream URLs cached with expiry so repeat plays skip
yt-dlp entirely; gzip/deflate via `@fastify/compress`; rate limiting; ETag +
immutable caching on audio downloads; bounded stdout on yt-dlp (no OOM on
giant playlists).

**App** — single shared `MediaController`; LazyColumn with keys + contentType
everywhere; Coil with 25% RAM / 256 MB disk artwork caches; Room reactive
flows queried off the main thread; server sync preserves local-only state
(downloads, play counts) instead of re-writing rows; download progress
updates throttled to ~2.5 Hz; WorkManager with exponential backoff and
Wi-Fi-only constraints; R8 minify + resource shrink on release builds;
zero-reflection DI; streaming through an LRU audio cache so replays are
offline-fast.

## Where audio comes from

Spotify metadata is read through Spotify's official Web API. Stream and file
resolution for YouTube-style links is done by yt-dlp on **your own server, for
your own use** — you are responsible for complying with the terms of whatever
platforms you point it at.
