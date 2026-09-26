import fs from 'node:fs';
import { getDb } from '../db.js';
import { resolveForTrack } from '../providers/index.js';
import { isCached } from './materialize.js';
import type { StreamResponse } from '../types.js';
import type { TrackRow } from '../db.js';

/**
 * Stream resolution with two fast paths:
 *  1. materialized file on the server → served from disk (instant, forever)
 *  2. previously resolved direct URL that hasn't expired → reused
 * Only on a miss do we hit the provider (yt-dlp), and the result is persisted.
 */
export async function resolveStream(track: TrackRow, baseUrl: string): Promise<StreamResponse> {
  if (isCached(track)) {
    return { kind: 'file', url: `${baseUrl}/api/tracks/${encodeURIComponent(track.id)}/download` };
  }

  const now = Date.now();
  if (track.stream_url && track.stream_expires_at && track.stream_expires_at - 60_000 > now) {
    return {
      kind: 'url',
      url: track.stream_url,
      expiresIn: Math.floor((track.stream_expires_at - now) / 1000),
    };
  }

  const resolved = await resolveForTrack({
    source: track.source,
    sourceId: track.source_id,
    sourceUrl: track.source_url,
    title: track.title,
    artist: track.artist,
  });
  getDb()
    .prepare(
      `UPDATE tracks SET stream_url = ?, stream_expires_at = ?,
       duration_ms = CASE WHEN ? > 0 THEN ? ELSE duration_ms END WHERE id = ?`,
    )
    .run(resolved.url, now + resolved.expiresInSec * 1000, resolved.durationMs ?? 0, resolved.durationMs ?? 0, track.id);

  return { kind: 'url', url: resolved.url, expiresIn: resolved.expiresInSec };
}
