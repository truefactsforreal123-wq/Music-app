export interface TrackDto {
  id: string;
  source: string;
  sourceId: string;
  sourceUrl: string | null;
  title: string;
  artist: string;
  durationMs: number;
  artUrl: string | null;
  isLiked: boolean;
  playCount: number;
  lastPlayedAt?: number;
  cached: boolean;
}

export interface PlaylistSummary {
  id: string;
  name: string;
  sourceUrl: string | null;
  provider: string | null;
  artUrl: string | null;
  updatedAt: number;
  trackCount: number;
}

export interface PlaylistFull extends PlaylistSummary {
  tracks: TrackDto[];
}

export interface StreamResponse {
  kind: 'url' | 'file';
  url: string;
  expiresIn?: number;
}

export interface DownloadStatus {
  ready: boolean;
  queued: boolean;
  sizeBytes: number | null;
  error: string | null;
}

export interface LyricsResponse {
  syncedLyrics: string | null;
  plainLyrics: string | null;
}

export interface HealthResponse {
  ok: true;
  version: string;
  ytDlp: { available: boolean; version: string | null; path: string };
  spotify: boolean;
  lanIps: string[];
}

export interface FetchResponse {
  playlist: PlaylistSummary;
  added: number;
  total: number;
}

export interface TrackInput {
  source: string;
  sourceId: string;
  sourceUrl?: string | null;
  title: string;
  artist?: string | null;
  durationMs?: number | null;
  artUrl?: string | null;
}
