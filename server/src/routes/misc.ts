import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getDb, rowToTrackDto, type TrackRow } from '../db.js';
import { NotFoundError } from '../errors.js';
import { SERVER_VERSION } from '../config.js';
import { peekYtDlpVersion, resolveYtDlp } from '../services/ytdlp.js';
import { ytdlpProvider } from '../providers/ytdlp-provider.js';
import { spotifyConfigured } from '../providers/spotify.js';
import { networkInterfaces } from 'node:os';

export async function miscRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/health', async () => {
    return {
      ok: true,
      version: SERVER_VERSION,
      ytDlp: { available: resolveYtDlp() !== null, version: peekYtDlpVersion(), path: 'server/bin' },
      spotify: spotifyConfigured,
      lanIps: lanIps(),
    };
  });

  app.get('/api/search', async (req) => {
    const { q } = z.object({ q: z.string().min(2).max(200) }).parse(req.query);
    const result = await ytdlpProvider.fetch(`ytsearch12:${q}`);
    return { tracks: result.tracks.map((t, i) => ({ ...t, id: `${t.source}:${t.sourceId}`, rank: i })) };
  });

  app.post('/api/history', async (req) => {
    const { trackId } = z.object({ trackId: z.string().min(1) }).parse(req.body);
    const row = getDb().prepare('SELECT * FROM tracks WHERE id = ?').get(trackId) as TrackRow | undefined;
    if (!row) throw new NotFoundError('Track not found');
    getDb()
      .prepare('UPDATE tracks SET play_count = play_count + 1, last_played_at = ? WHERE id = ?')
      .run(Date.now(), trackId);
    const fresh = getDb().prepare('SELECT * FROM tracks WHERE id = ?').get(trackId) as TrackRow;
    return { track: rowToTrackDto(fresh) };
  });

  app.get('/api/recent', async (req) => {
    const { limit } = z.object({ limit: z.coerce.number().int().min(1).max(100).default(30) }).parse(req.query);
    const rows = getDb()
      .prepare('SELECT * FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT ?')
      .all(limit) as unknown as TrackRow[];
    return { tracks: rows.map(rowToTrackDto) };
  });

  app.get('/api/most-played', async (req) => {
    const { limit } = z.object({ limit: z.coerce.number().int().min(1).max(100).default(30) }).parse(req.query);
    const rows = getDb()
      .prepare('SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC, last_played_at DESC LIMIT ?')
      .all(limit) as unknown as TrackRow[];
    return { tracks: rows.map(rowToTrackDto) };
  });

  app.get('/api/likes', async () => {
    const rows = getDb()
      .prepare('SELECT * FROM tracks WHERE is_liked = 1 ORDER BY rowid DESC LIMIT 500')
      .all() as unknown as TrackRow[];
    return { tracks: rows.map(rowToTrackDto) };
  });

  app.get('/api/lyrics', async (req) => {
    const { title, artist, durationMs } = z
      .object({ title: z.string().min(1).max(300), artist: z.string().max(300).default(''), durationMs: z.coerce.number().default(0) })
      .parse(req.query);
    return getLyrics(title, artist, durationMs);
  });
}

const lyricsCache = new Map<string, { syncedLyrics: string | null; plainLyrics: string | null }>();

function cleanTitle(t: string): string {
  return t
    .replace(/\((official|video|audio|lyric[s]?|hd|4k|mv|m\/v|visualizer)[^)]*\)/gi, '')
    .replace(/\[(official|video|audio|lyric[s]?|hd|4k)[^\]]*\]/gi, '')
    .replace(/\s*feat\.?\s+.*$/i, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

async function getLyrics(title: string, artist: string, durationMs: number) {
  const cleanT = cleanTitle(title);
  const cacheKey = `${cleanT}|${artist}`;
  const hit = lyricsCache.get(cacheKey);
  if (hit) return hit;

  const headers = { 'User-Agent': 'Aura/1.0 (personal music player)' };
  const seconds = durationMs > 0 ? Math.round(durationMs / 1000) : undefined;
  let out: { syncedLyrics: string | null; plainLyrics: string | null } = { syncedLyrics: null, plainLyrics: null };

  try {
    const get = new URL('https://lrclib.net/api/get');
    get.searchParams.set('track_name', cleanT);
    if (artist) get.searchParams.set('artist_name', artist);
    if (seconds) get.searchParams.set('duration', String(seconds));
    let res = await fetch(get, { headers, signal: AbortSignal.timeout(10_000) });
    if (res.status === 404 && artist) {
      // Retry without artist — covers remixes and odd metadata.
      const retry = new URL('https://lrclib.net/api/get');
      retry.searchParams.set('track_name', cleanT);
      res = await fetch(retry, { headers, signal: AbortSignal.timeout(10_000) });
    }
    if (res.ok) {
      const j: any = await res.json();
      out = { syncedLyrics: j.syncedLyrics ?? null, plainLyrics: j.plainLyrics ?? null };
    } else if (res.status === 404) {
      const search = new URL('https://lrclib.net/api/search');
      search.searchParams.set('track_name', cleanT);
      if (artist) search.searchParams.set('artist_name', artist);
      const sres = await fetch(search, { headers, signal: AbortSignal.timeout(10_000) });
      if (sres.ok) {
        const list = (await sres.json()) as any[];
        const best = list.find((l) => l.syncedLyrics) ?? list[0];
        if (best) out = { syncedLyrics: best.syncedLyrics ?? null, plainLyrics: best.plainLyrics ?? null };
      }
    }
  } catch {
    // Lyrics are a nice-to-have; never fail playback because of them.
  }

  if (lyricsCache.size > 400) lyricsCache.delete(lyricsCache.keys().next().value!);
  lyricsCache.set(cacheKey, out);
  return out;
}

function lanIps(): string[] {
  const out: string[] = [];
  for (const list of Object.values(networkInterfaces())) {
    for (const ni of list ?? []) {
      if (ni.family === 'IPv4' && !ni.internal) out.push(ni.address);
    }
  }
  return out;
}
