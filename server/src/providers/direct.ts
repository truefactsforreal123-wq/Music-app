import { ProviderError } from '../errors.js';
import { hashId, type ProviderFetchResult, type ResolvedStream } from './types.js';
import type { TrackInput } from '../types.js';

const AUDIO_EXT = /\.(mp3|m4a|aac|flac|wav|ogg|opus|m4b)(\?|#|$)/i;
const PLAYLIST_EXT = /\.m3u8?(\?|#|$)/i;

/**
 * Direct provider: plays exactly what's behind the link — single audio file
 * URLs, or M3U/M3U8 plain-text playlists.
 */

export const directProvider = {
  name: 'direct',

  canHandle(url: string): boolean {
    return AUDIO_EXT.test(url) || PLAYLIST_EXT.test(url);
  },

  async fetch(url: string): Promise<ProviderFetchResult> {
    if (PLAYLIST_EXT.test(url)) return fetchM3u(url);
    const title = decodeURIComponent(url.split('/').pop()!.split('?')[0]!) || 'Audio track';
    return {
      name: title,
      provider: 'direct',
      artUrl: null,
      tracks: [
        {
          source: 'direct',
          sourceId: hashId(url),
          sourceUrl: url,
          title,
          artist: '',
          durationMs: 0,
          artUrl: null,
        },
      ],
    };
  },

  async resolve(track: { sourceUrl: string | null }): Promise<ResolvedStream> {
    if (!track.sourceUrl) throw new ProviderError('Direct track has no URL');
    return { url: track.sourceUrl, expiresInSec: 24 * 3600 };
  },
};

async function fetchM3u(url: string): Promise<ProviderFetchResult> {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new ProviderError(`Could not download M3U playlist (HTTP ${res.status})`);
  const text = await res.text();
  const base = new URL(url);

  const tracks: TrackInput[] = [];
  let pendingDuration = 0;
  let pendingTitle: string | null = null;

  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (line.startsWith('#EXTINF:')) {
      pendingDuration = Math.round(Number(line.slice(8).split(',')[0]) || 0) * 1000;
      pendingTitle = line.slice(line.indexOf(',') + 1).trim() || null;
    } else if (!line.startsWith('#')) {
      let abs: string;
      try {
        abs = new URL(line, base).toString();
      } catch {
        continue;
      }
      tracks.push({
        source: 'direct',
        sourceId: hashId(abs),
        sourceUrl: abs,
        title: pendingTitle ?? (decodeURIComponent(abs.split('/').pop()!.split('?')[0]!) || 'Audio track'),
        artist: '',
        durationMs: pendingDuration,
        artUrl: null,
      });
      pendingDuration = 0;
      pendingTitle = null;
    }
  }

  if (tracks.length === 0) throw new ProviderError('M3U playlist contains no entries.');
  return {
    name: decodeURIComponent(url.split('/').pop()!.split('?')[0]!) || 'M3U Playlist',
    provider: 'direct',
    artUrl: null,
    tracks,
  };
}
