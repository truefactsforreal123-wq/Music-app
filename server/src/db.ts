import { DatabaseSync } from 'node:sqlite';
import { DATA_DIR } from './config.js';
import path from 'node:path';
import type { PlaylistFull, PlaylistSummary, TrackDto } from './types.js';

let db: DatabaseSync | null = null;

const SCHEMA = `
CREATE TABLE IF NOT EXISTS playlists (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  source_url  TEXT,
  provider    TEXT,
  art_url     TEXT,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS tracks (
  id                TEXT PRIMARY KEY,
  source            TEXT NOT NULL,
  source_id         TEXT NOT NULL,
  source_url        TEXT,
  title             TEXT NOT NULL,
  artist            TEXT NOT NULL DEFAULT '',
  duration_ms       INTEGER NOT NULL DEFAULT 0,
  art_url           TEXT,
  stream_url        TEXT,
  stream_expires_at INTEGER,
  cached_file       TEXT,
  cached_size       INTEGER,
  cached_error      TEXT,
  is_liked          INTEGER NOT NULL DEFAULT 0,
  play_count        INTEGER NOT NULL DEFAULT 0,
  last_played_at    INTEGER,
  created_at        INTEGER NOT NULL,
  UNIQUE(source, source_id)
);

CREATE TABLE IF NOT EXISTS playlist_tracks (
  playlist_id TEXT NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
  track_id    TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
  position    INTEGER NOT NULL,
  PRIMARY KEY (playlist_id, track_id)
);

CREATE INDEX IF NOT EXISTS idx_pt_position ON playlist_tracks(playlist_id, position);
CREATE INDEX IF NOT EXISTS idx_tracks_liked ON tracks(is_liked) WHERE is_liked = 1;
CREATE INDEX IF NOT EXISTS idx_tracks_played ON tracks(play_count DESC);
CREATE INDEX IF NOT EXISTS idx_history ON tracks(last_played_at DESC);
`;

export function initDb(): void {
  db = new DatabaseSync(path.join(DATA_DIR, 'aura.db'));
  // Performance: WAL for concurrent readers, NORMAL sync is durable enough for
  // a media library, generous page cache, in-memory temp tables.
  db.exec(`
    PRAGMA journal_mode = WAL;
    PRAGMA synchronous = NORMAL;
    PRAGMA foreign_keys = ON;
    PRAGMA temp_store = MEMORY;
    PRAGMA cache_size = -16000;
  `);
  db.exec(SCHEMA);
}

export function getDb(): DatabaseSync {
  if (!db) throw new Error('Database not initialised');
  return db;
}

export function inTransaction<T>(fn: () => T): T {
  const d = getDb();
  d.exec('BEGIN');
  try {
    const out = fn();
    d.exec('COMMIT');
    return out;
  } catch (err) {
    d.exec('ROLLBACK');
    throw err;
  }
}

export type TrackRow = {
  id: string;
  source: string;
  source_id: string;
  source_url: string | null;
  title: string;
  artist: string;
  duration_ms: number;
  art_url: string | null;
  stream_url: string | null;
  stream_expires_at: number | null;
  cached_file: string | null;
  cached_size: number | null;
  cached_error: string | null;
  is_liked: number;
  play_count: number;
  last_played_at: number | null;
};

export function rowToTrackDto(r: TrackRow): TrackDto {
  return {
    id: r.id,
    source: r.source,
    sourceId: r.source_id,
    sourceUrl: r.source_url,
    title: r.title,
    artist: r.artist,
    durationMs: r.duration_ms,
    artUrl: r.art_url,
    isLiked: r.is_liked === 1,
    playCount: r.play_count,
    lastPlayedAt: r.last_played_at ?? undefined,
    cached: !!r.cached_file,
  };
}

export function rowToPlaylistSummary(
  r: Record<string, unknown> & { id: string; name: string; source_url: string | null; provider: string | null; art_url: string | null; updated_at: number; track_count: number },
): PlaylistSummary {
  return {
    id: r.id,
    name: r.name,
    sourceUrl: r.source_url,
    provider: r.provider,
    artUrl: r.art_url,
    updatedAt: r.updated_at,
    trackCount: r.track_count,
  };
}

export function getTrackRow(id: string): TrackRow | undefined {
  return getDb().prepare('SELECT * FROM tracks WHERE id = ?').get(id) as TrackRow | undefined;
}

export function getPlaylistFull(id: string): PlaylistFull | undefined {
  const p = getDb()
    .prepare(
      `SELECT p.*, COUNT(pt.track_id) AS track_count
       FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
       WHERE p.id = ? GROUP BY p.id`,
    )
    .get(id) as Record<string, unknown> | undefined;
  if (!p) return undefined;
  const trackRows = getDb()
    .prepare(
      `SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id
       WHERE pt.playlist_id = ? ORDER BY pt.position ASC`,
    )
    .all(id) as unknown as TrackRow[];
  const summary = rowToPlaylistSummary(p as never);
  return { ...summary, tracks: trackRows.map(rowToTrackDto) };
}
