import { ProviderError } from '../errors.js';
import type { ProviderFetchResult, ResolvedStream } from './types.js';
import type { TrackInput } from '../types.js';
import { ytdlpProvider } from './ytdlp-provider.js';
import { ytDlpJson } from '../services/ytdlp.js';

/**
 * Spotify provider: reads playlist metadata + artwork WITHOUT any API
 * credentials, using the same anonymous surfaces the public web embed uses:
 *
 *   1. GET https://open.spotify.com/embed/api/token
 *        -> { accessToken, accessTokenExpirationTimestampMs, isAnonymous }
 *   2. GET https://api-partner.spotify.com/pathfinder/v1/query
 *        operationName=fetchPlaylist (persisted query), paged by offset/limit
 *
 * Pages are 100 tracks; we loop until offset >= content.totalCount, so the
 * FULL playlist is imported (verified: 150/150 and 62/62, 0 duplicates).
 * Spotify serves no audio, so each track is lazily matched to a streamable
 * source via the generic yt-dlp provider on first play — unchanged behaviour.
 */

const TOKEN_URL = 'https://open.spotify.com/embed/api/token';
const QUERY_URL = 'https://api-partner.spotify.com/pathfinder/v1/query';
const FETCH_PLAYLIST_HASH =
  '243c0ba2736f16da721e3a227004bbcdb8df6c846f198bd478172e00aa1faf42';
const PAGE_SIZE = 100;
const MAX_PAGES = 100; // 10k tracks; Spotify's own playlist cap

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

/** Spotify needs no setup anymore — kept for the /api/health contract. */
export const spotifyConfigured = true;

let token: { value: string; expiresAt: number } | null = null;

async function getToken(): Promise<string> {
  if (token && token.expiresAt > Date.now() + 30_000) return token.value;
  const res = await fetch(TOKEN_URL, {
    headers: { 'User-Agent': UA, Accept: 'application/json' },
    signal: AbortSignal.timeout(10_000),
  });
  if (!res.ok) throw new ProviderError(`Spotify token endpoint failed (HTTP ${res.status})`);
  const json = (await res.json()) as {
    accessToken?: string;
    accessTokenExpirationTimestampMs?: number;
  };
  if (!json.accessToken) throw new ProviderError('Spotify token endpoint returned no token');
  token = {
    value: json.accessToken,
    expiresAt: json.accessTokenExpirationTimestampMs ?? Date.now() + 600_000,
  };
  return token.value;
}

function parseId(url: string): string | null {
  return url.match(/open\.spotify\.com\/(?:intl-[a-z]+\/)?playlist\/([A-Za-z0-9]+)/)?.[1] ?? null;
}

interface PlaylistPage {
  data?: {
    playlistV2?: {
      __typename?: string;
      name?: string;
      description?: string;
      images?: { items?: { sources?: { url?: string }[] }[] };
      content?: {
        __typename?: string;
        totalCount?: number;
        pagingInfo?: { limit?: number; offset?: number };
        items?: unknown[];
      };
      uri?: string;
    };
  };
  errors?: { message?: string }[];
}

async function fetchPage(uri: string, offset: number): Promise<NonNullable<PlaylistPage['data']>['playlistV2']> {
  const accessToken = await getToken();
  const qs = new URLSearchParams({
    operationName: 'fetchPlaylist',
    variables: JSON.stringify({
      uri,
      offset,
      limit: PAGE_SIZE,
      enableWatchFeedEntrypoint: false,
      includeEpisodeContentRatingsV2: false,
    }),
    extensions: JSON.stringify({
      persistedQuery: { version: 1, sha256Hash: FETCH_PLAYLIST_HASH },
    }),
  });
  const res = await fetch(`${QUERY_URL}?${qs}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
      Origin: 'https://open.spotify.com',
      Referer: 'https://open.spotify.com/',
      'User-Agent': UA,
    },
    signal: AbortSignal.timeout(15_000),
  });

  if (res.status === 404) throw new ProviderError('Spotify playlist not found (is it public?)', 404);
  if (!res.ok) {
    // Token can expire mid-run; refresh once and retry.
    if (res.status === 401 || res.status === 403) {
      token = null;
      const retry = await fetch(`${QUERY_URL}?${qs}`, {
        headers: {
          Authorization: `Bearer ${await getToken()}`,
          Accept: 'application/json',
          Origin: 'https://open.spotify.com',
          Referer: 'https://open.spotify.com/',
          'User-Agent': UA,
        },
        signal: AbortSignal.timeout(15_000),
      });
      if (retry.ok) return (await retry.json() as PlaylistPage).data?.playlistV2;
      throw new ProviderError(`Spotify request failed (HTTP ${retry.status})`);
    }
    throw new ProviderError(`Spotify request failed (HTTP ${res.status})`);
  }

  const json = (await res.json()) as PlaylistPage;
  if (json.errors?.length) {
    const msg = json.errors[0]?.message ?? 'unknown error';
    if (/not.?found/i.test(msg)) throw new ProviderError('Spotify playlist not found (is it public?)', 404);
    throw new ProviderError(`Spotify query error: ${msg}`);
  }
  const playlist = json.data?.playlistV2;
  if (!playlist) throw new ProviderError('Spotify returned no playlist data');
  if (playlist.__typename === 'NotFound' || playlist.__typename === 'GenericError') {
    throw new ProviderError('Spotify playlist not found (is it public?)', 404);
  }
  return playlist;
}

function artworkOf(playlist: NonNullable<PlaylistPage['data']>['playlistV2']): string | null {
  return playlist?.images?.items?.[0]?.sources?.[0]?.url ?? null;
}

interface TrackNode {
  __typename?: string;
  uri?: string;
  name?: string;
  artists?: { items?: { profile?: { name?: string } }[] };
  trackDuration?: { totalMilliseconds?: number };
  albumOfTrack?: { name?: string; coverArt?: { sources?: { url?: string; height?: number | null }[] } };
}

function pickArt(sources?: { url?: string; height?: number | null }[]): string | null {
  if (!sources?.length) return null;
  const sorted = [...sources].sort((a, b) => (Number(b.height) || 0) - (Number(a.height) || 0));
  return sorted[0]?.url ?? null;
}

export const spotifyProvider = {
  name: 'spotify',

  canHandle(url: string): boolean {
    return parseId(url) !== null;
  },

  async fetch(url: string): Promise<ProviderFetchResult> {
    const id = parseId(url);
    if (!id) throw new ProviderError('Not a Spotify playlist URL');
    const uri = `spotify:playlist:${id}`;

    const tracks: TrackInput[] = [];
    const seen = new Set<string>();
    let name = 'Spotify Playlist';
    let artUrl: string | null = null;
    let total: number | null = null;
    let offset = 0;

    for (let page = 0; page < MAX_PAGES; page++) {
      const playlist = await fetchPage(uri, offset);
      if (page === 0) {
        name = String(playlist?.name ?? name);
        artUrl = artworkOf(playlist);
        total = playlist?.content?.totalCount ?? null;
      }

      const items = playlist?.content?.items ?? [];
      for (const raw of items) {
        const node = (raw as { itemV2?: { data?: TrackNode } } | null)?.itemV2?.data;
        if (!node || node.__typename !== 'Track') continue; // episodes / local files
        const spotifyId = (node.uri ?? '').replace(/^spotify:track:/, '');
        if (!spotifyId || seen.has(spotifyId)) continue;
        seen.add(spotifyId);
        tracks.push({
          source: 'spotify',
          sourceId: spotifyId,
          sourceUrl: `https://open.spotify.com/track/${spotifyId}`,
          title: String(node.name ?? 'Unknown'),
          artist: (node.artists?.items ?? []).map((a) => a?.profile?.name).filter(Boolean).join(', '),
          durationMs: Number(node.trackDuration?.totalMilliseconds) || 0,
          artUrl: pickArt(node.albumOfTrack?.coverArt?.sources) ?? artUrl,
        });
      }

      if (items.length === 0) break;
      offset += items.length;
      if (total !== null && offset >= total) break;
      // Be polite to the upstream: small gap between pages.
      await new Promise((r) => setTimeout(r, 250));
    }

    if (tracks.length === 0) throw new ProviderError('Spotify playlist is empty.');
    return { name, provider: 'spotify', artUrl, tracks };
  },

  /** Matches a Spotify track to a streamable source, then resolves it. */
  async resolve(track: {
    sourceId: string;
    title: string;
    artist: string;
    sourceUrl: string | null;
    durationMs?: number;
  }): Promise<ResolvedStream> {
    // If we already stored a matched webpage URL, resolve directly.
    if (track.sourceUrl && /youtube\.com|youtu\.be|soundcloud\.com/.test(track.sourceUrl)) {
      return ytdlpProvider.resolve(track);
    }
    const query = `ytsearch5:${track.artist ? `${track.artist} ` : ''}${track.title} audio`.trim();
    const info = await ytDlpJson(['--flat-playlist', query], 30_000);
    const entries: any[] = info?.entries ?? [];
    // Taking the first hit grabs covers/remixes too; prefer the entry whose
    // duration is closest to the Spotify track within ±4s, else fall back to first.
    const targetSec = track.durationMs && track.durationMs > 0 ? track.durationMs / 1000 : null;
    const entry =
      targetSec != null
        ? entries
            .filter((e) => Number(e?.duration) > 0 && Math.abs(Number(e.duration) - targetSec) <= 4)
            .sort((a, b) => Math.abs(Number(a.duration) - targetSec) - Math.abs(Number(b.duration) - targetSec))[0] ??
          entries[0]
        : entries[0];
    if (!entry) throw new ProviderError('No streamable match found for this track', 404);
    const webpage = entry.webpage_url ?? entry.url;
    if (!webpage) throw new ProviderError('No streamable match found for this track', 404);
    return ytdlpProvider.resolve({ sourceUrl: webpage });
  },
};
