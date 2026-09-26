import { createHash } from 'node:crypto';
import type { TrackInput } from '../types.js';

export interface ResolvedStream {
  url: string;
  expiresInSec: number;
  durationMs?: number;
}

export interface ProviderFetchResult {
  name: string;
  provider: string;
  artUrl: string | null;
  tracks: TrackInput[];
}

export interface Provider {
  name: string;
  canHandle(url: string): boolean;
  fetch(url: string): Promise<ProviderFetchResult>;
}

/** Stable short id for URLs that have no platform-native id. */
export function hashId(input: string): string {
  return createHash('sha1').update(input).digest('hex').slice(0, 20);
}
