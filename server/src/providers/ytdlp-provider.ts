import { ytDlpJson, pickThumb } from '../services/ytdlp.js';
import { ProviderError } from '../errors.js';
import type { ProviderFetchResult, ResolvedStream } from './types.js';
import type { TrackInput } from '../types.js';

/**
 * Generic yt-dlp provider. Handles YouTube, YouTube Music, SoundCloud,
 * Bandcamp and every other source yt-dlp supports — it is the fallback for
 * any link the dedicated providers don't claim.
 */

function normalizeEntry(e: any): TrackInput | null {
  if (!e || (!e.id && !e.url)) return null;
  if (e.title === '[Private video]' || e.title === '[Deleted video]') return null;
  const sourceUrl: string | null = e.webpage_url ?? e.original_url ?? e.url ?? null;
  return {
    source: 'ytdlp',
    sourceId: String(e.id),
    sourceUrl,
    title: String(e.title ?? 'Unknown title'),
    artist: String(e.uploader ?? e.channel ?? ''),
    durationMs: e.duration ? Math.round(Number(e.duration) * 1000) : 0,
    artUrl: pickThumb(e.thumbnails),
  };
}

export const ytdlpProvider = {
  name: 'ytdlp',

  async fetch(url: string): Promise<ProviderFetchResult> {
    const info = await ytDlpJson(['--flat-playlist', url], 120_000);

    if (info?._type === 'playlist' && Array.isArray(info.entries)) {
      const tracks = info.entries
        .map(normalizeEntry)
        .filter((t: TrackInput | null): t is TrackInput => t !== null);
      if (tracks.length === 0) {
        throw new ProviderError('The playlist exists but contains no playable tracks.');
      }
      return {
        name: String(info.title ?? 'Playlist'),
        provider: String(info.extractor_key ?? 'ytdlp').split(':')[0]!.toLowerCase(),
        artUrl: pickThumb(info.thumbnails) ?? tracks[0]?.artUrl ?? null,
        tracks,
      };
    }

    // Single track URL
    const track = normalizeEntry(info);
    if (!track) throw new ProviderError('Could not read any track info from that link.');
    return {
      name: track.title,
      provider: String(info.extractor_key ?? 'ytdlp').split(':')[0]!.toLowerCase(),
      artUrl: track.artUrl ?? null,
      tracks: [track],
    };
  },

  /** Resolves a fresh direct audio URL for a stored track. */
  async resolve(track: { sourceUrl: string | null; sourceId?: string }): Promise<ResolvedStream> {
    // Fallback: an 11-char sourceId is a YouTube video id — rebuild the watch URL.
    const url =
      track.sourceUrl ??
      (track.sourceId && /^[A-Za-z0-9_-]{11}$/.test(track.sourceId)
        ? `https://www.youtube.com/watch?v=${track.sourceId}`
        : null);
    if (!url) throw new ProviderError('Track has no source URL to resolve.');
    const info = await ytDlpJson(
      ['-f', 'bestaudio/best', '--no-playlist', url],
      60_000,
    );
    const direct = pickBestAudioUrl(info);
    if (!direct) throw new ProviderError('No audio stream found for this track.');
    return {
      url: direct,
      // Platforms like YouTube typically invalidate these URLs after ~6h.
      expiresInSec: 5 * 3600,
      durationMs: info.duration ? Math.round(Number(info.duration) * 1000) : undefined,
    };
  },
};

/** Chooses a progressive https audio URL (ExoPlayer-friendly), best bitrate first. */
function pickBestAudioUrl(info: any): string | null {
  if (typeof info?.url === 'string' && info.url.startsWith('http')) return info.url;
  const formats: any[] = Array.isArray(info?.formats) ? info.formats : [];
  const audioOnly = formats.filter(
    (f) =>
      f?.url &&
      typeof f.url === 'string' &&
      f.url.startsWith('http') &&
      (f.vcodec === 'none' || f.vcodec == null) &&
      f.acodec !== 'none' &&
      (f.protocol === 'https' || f.protocol == null),
  );
  if (audioOnly.length === 0) {
    const any = formats.find((f) => typeof f?.url === 'string' && f.url.startsWith('http'));
    return any?.url ?? null;
  }
  audioOnly.sort((a, b) => (Number(b.abr) || Number(b.tbr) || 0) - (Number(a.abr) || Number(a.tbr) || 0));
  return audioOnly[0]!.url;
}
