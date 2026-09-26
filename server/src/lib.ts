import type { FastifyRequest } from 'fastify';
import type { DatabaseSync } from 'node:sqlite';
import { getDb } from './db.js';
import { hashId } from './providers/types.js';
import type { PlaylistSummary, TrackInput, TrackDto } from './types.js';
import { rowToTrackDto, type TrackRow } from './db.js';

export function baseUrlOf(req: FastifyRequest): string {
  const host = req.headers.host ?? `localhost`;
  const proto = req.protocol ?? 'http';
  return `${proto}://${host}`;
}

/** Deterministic playlist id so re-fetching the same URL updates, never duplicates. */
export function playlistIdFor(url: string): string {
  return `pl_${hashId(url.trim().toLowerCase())}`;
}

export function trackIdFor(t: Pick<TrackInput, 'source' | 'sourceId'>): string {
  return `${t.source}:${t.sourceId}`;
}

export function getTrackDto(id: string): TrackDto | undefined {
  const row = getDb().prepare('SELECT * FROM tracks WHERE id = ?').get(id) as TrackRow | undefined;
  return row ? rowToTrackDto(row) : undefined;
}

let upsertStmt: ReturnType<DatabaseSync['prepare']> | null = null;

export function upsertTrack(t: TrackInput): string {
  const id = trackIdFor(t);
  // Prepared once, reused across bulk inserts (500-track playlists stay fast).
  upsertStmt ??= getDb().prepare(
    `INSERT INTO tracks (id, source, source_id, source_url, title, artist, duration_ms, art_url, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(source, source_id) DO UPDATE SET
       title = excluded.title,
       artist = COALESCE(NULLIF(excluded.artist, ''), artist),
       duration_ms = CASE WHEN excluded.duration_ms > 0 THEN excluded.duration_ms ELSE duration_ms END,
       art_url = COALESCE(excluded.art_url, art_url),
       source_url = COALESCE(excluded.source_url, source_url)`,
  );
  upsertStmt.run(
    id,
    t.source,
    t.sourceId,
    t.sourceUrl ?? null,
    t.title,
    t.artist ?? '',
    t.durationMs ?? 0,
    t.artUrl ?? null,
    Date.now(),
  );
  return id;
}

export function playlistSummary(id: string): PlaylistSummary | undefined {
  const row = getDb()
    .prepare(
      `SELECT p.*, COUNT(pt.track_id) AS track_count
       FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
       WHERE p.id = ? GROUP BY p.id`,
    )
    .get(id) as Record<string, unknown> | undefined;
  if (!row) return undefined;
  return {
    id: row.id as string,
    name: row.name as string,
    sourceUrl: row.source_url as string | null,
    provider: row.provider as string | null,
    artUrl: row.art_url as string | null,
    updatedAt: row.updated_at as number,
    trackCount: row.track_count as number,
  };
}

export function nextPosition(playlistId: string): number {
  const row = getDb()
    .prepare('SELECT COALESCE(MAX(position), -1) AS max FROM playlist_tracks WHERE playlist_id = ?')
    .get(playlistId) as { max: number };
  return row.max + 1;
}
