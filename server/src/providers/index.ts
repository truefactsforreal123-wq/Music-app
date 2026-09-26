import { spotifyProvider } from './spotify.js';
import { directProvider } from './direct.js';
import { ytdlpProvider } from './ytdlp-provider.js';
import type { ResolvedStream } from './types.js';

export function detectProvider(url: string) {
  if (spotifyProvider.canHandle(url)) return spotifyProvider;
  if (directProvider.canHandle(url)) return directProvider;
  return ytdlpProvider;
}

/** Resolve a playable URL for any stored track, whatever its source. */
export function resolveForTrack(track: {
  source: string;
  sourceId: string;
  sourceUrl: string | null;
  title: string;
  artist: string;
}): Promise<ResolvedStream> {
  switch (track.source) {
    case 'spotify':
      return spotifyProvider.resolve(track);
    case 'direct':
      return directProvider.resolve(track);
    default:
      return ytdlpProvider.resolve(track);
  }
}

export { spotifyConfigured } from './spotify.js';
