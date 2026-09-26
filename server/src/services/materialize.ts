import fs from 'node:fs';
import path from 'node:path';
import { AUDIO_DIR } from '../config.js';
import { getDb, type TrackRow } from '../db.js';
import { ProviderError } from '../errors.js';
import { resolveForTrack } from '../providers/index.js';
import { runYtDlp } from './ytdlp.js';
import type { DownloadStatus } from '../types.js';

/**
 * Materializes tracks to real audio files on the server (one-time cost),
 * deduped by track id and de-duplicated across concurrent requests via an
 * in-flight promise map. The app polls /status and then downloads the file,
 * so its progress bar reflects the actual byte transfer.
 */

const inflight = new Map<string, Promise<void>>();

function sanitizeId(id: string): string {
  return id.replace(/[^A-Za-z0-9_.-]/g, '_');
}

export function isCached(track: TrackRow): boolean {
  return !!track.cached_file && fs.existsSync(track.cached_file);
}

/** Kicks off server-side materialization. Safe to call repeatedly. */
export function prepareTrack(track: TrackRow): void {
  if (isCached(track) || inflight.has(track.id)) return;
  const job = materialize(track).finally(() => inflight.delete(track.id));
  inflight.set(track.id, job);
}

export function isQueued(trackId: string): boolean {
  return inflight.has(trackId);
}

export function statusOf(track: TrackRow): DownloadStatus {
  const ready = isCached(track);
  const err = track.cached_error;
  // A previous failure shouldn't stick once we retry — clear it lazily.
  if (!ready && err && !inflight.has(track.id) && track.stream_url) {
    // leave error; retrying prepare overwrites it
  }
  return {
    ready,
    queued: inflight.has(track.id),
    sizeBytes: ready && track.cached_size ? track.cached_size : null,
    error: err,
  };
}

async function materialize(track: TrackRow): Promise<void> {
  const db = getDb();
  db.prepare('UPDATE tracks SET cached_error = NULL WHERE id = ?').run(track.id);
  try {
    // Resolve through providers: raw source_url can be a page URL (e.g. open.spotify.com)
    // that yt-dlp cannot download — providers return a real media URL
    // (ytsearch match → googlevideo, youtube -g, direct passthrough).
    const resolved = await resolveForTrack({
      source: track.source,
      sourceId: track.source_id,
      sourceUrl: track.source_url,
      title: track.title,
      artist: track.artist,
    });
    // Explicit filename: for raw googlevideo URLs yt-dlp derives a garbage
    // id from the query string (600+ chars → Errno 22 / MAX_PATH on Windows).
    const outTemplate = path.join(AUDIO_DIR, sanitizeId(track.id) + '.%(ext)s');
    const { code, stdout, stderr } = await runYtDlp(
      [
        '--no-playlist',
        '--no-warnings',
        '-f',
        'bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio',
        '-o',
        outTemplate,
        '--no-simulate',
        '--print',
        'after_move:filepath',
        resolved.url,
      ],
      15 * 60_000,
    );
    const produced = stdout.trim().split(/\r?\n/).filter(Boolean).pop();
    if (code !== 0 || !produced || !fs.existsSync(produced)) {
      const tail = stderr.trim().split('\n').slice(-2).join(' | ');
      throw new ProviderError(tail || 'Download failed');
    }
    const ext = path.extname(produced) || '.m4a';
    const canonical = path.join(AUDIO_DIR, sanitizeId(track.id) + ext);
    if (path.resolve(produced) !== path.resolve(canonical)) {
      fs.renameSync(produced, canonical);
    }
    db.prepare('UPDATE tracks SET cached_file = ?, cached_size = ? WHERE id = ?').run(
      canonical,
      fs.statSync(canonical).size,
      track.id,
    );
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    db.prepare('UPDATE tracks SET cached_error = ? WHERE id = ?').run(message, track.id);
  }
}
