import type { FastifyInstance } from 'fastify';
import fs from 'node:fs';
import { createReadStream } from 'node:fs';
import { z } from 'zod';
import { getDb, getTrackRow, rowToTrackDto, type TrackRow } from '../db.js';
import { NotFoundError } from '../errors.js';
import { baseUrlOf, getTrackDto, upsertTrack } from '../lib.js';
import { isCached, isQueued, prepareTrack, statusOf } from '../services/materialize.js';
import { resolveStream } from '../services/stream.js';

const MIME: Record<string, string> = {
  '.m4a': 'audio/mp4',
  '.mp4': 'audio/mp4',
  '.mp3': 'audio/mpeg',
  '.webm': 'audio/webm',
  '.opus': 'audio/ogg',
  '.ogg': 'audio/ogg',
  '.flac': 'audio/flac',
  '.wav': 'audio/wav',
};

const likeSchema = z.object({ trackId: z.string().min(1), liked: z.boolean() });

/**
 * Track metadata the phone re-sends on first touch. Lets the server live on an
 * ephemeral host (Reset/redeploy = DB wiped): the client's Room DB is the
 * source of truth and re-seeds rows on demand.
 */
const trackMetaSchema = z.object({
  title: z.string().min(1).max(500).optional(),
  artist: z.string().max(500).optional(),
  durationMs: z.coerce.number().int().nonnegative().optional(),
  artUrl: z.string().max(2048).optional(),
  sourceUrl: z.string().max(2048).optional(),
});
type TrackMeta = z.infer<typeof trackMetaSchema>;

function requireTrack(id: string) {
  const row = getTrackRow(id);
  if (!row) throw new NotFoundError('Track not found');
  return row;
}

/** Like requireTrack, but re-creates the row from client metadata if the server DB was reset. */
function ensureTrack(id: string, meta: TrackMeta): TrackRow {
  const existing = getTrackRow(id);
  if (existing) return existing;
  if (!meta.title) throw new NotFoundError('Track not found');
  const sep = id.indexOf(':');
  if (sep <= 0 || sep === id.length - 1) throw new NotFoundError('Track not found');
  // trackIdFor(source + ':' + sourceId) must round-trip to the same id.
  upsertTrack({
    source: id.slice(0, sep),
    sourceId: id.slice(sep + 1),
    sourceUrl: meta.sourceUrl,
    title: meta.title,
    artist: meta.artist,
    durationMs: meta.durationMs,
    artUrl: meta.artUrl,
  });
  return requireTrack(id);
}

export async function trackRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/tracks/:id', async (req) => {
    const { id } = req.params as { id: string };
    const dto = getTrackDto(id);
    if (!dto) throw new NotFoundError('Track not found');
    return { track: dto };
  });

  /** The play endpoint: returns either a direct stream URL or the local file route. */
  app.get('/api/tracks/:id/stream', async (req) => {
    const { id } = req.params as { id: string };
    const meta = trackMetaSchema.parse(req.query ?? {});
    const row = ensureTrack(id, meta);
    return resolveStream(row, baseUrlOf(req));
  });

  /** Step 1 of downloading: ask the server to materialize the audio file. */
  app.post('/api/tracks/:id/prepare', async (req, reply) => {
    const { id } = req.params as { id: string };
    const meta = trackMetaSchema.parse(req.body ?? {});
    const row = ensureTrack(id, meta);
    const ready = isCached(row);
    if (!ready) prepareTrack(row);
    return reply.status(ready ? 200 : 202).send({ ready, queued: isQueued(id) });
  });

  /** Step 2: poll while the server fetches the file. */
  app.get('/api/tracks/:id/status', async (req) => {
    const { id } = req.params as { id: string };
    const row = requireTrack(id);
    return statusOf(row);
  });

  /** Step 3: transfer the materialized file (Content-Length drives the app progress bar). */
  app.get('/api/tracks/:id/download', async (req, reply) => {
    const { id } = req.params as { id: string };
    const row = requireTrack(id);
    if (!isCached(row) || !row.cached_file) {
      return reply.status(409).send({ error: 'Not materialized yet — POST /prepare first' });
    }
    const ext = row.cached_file.slice(row.cached_file.lastIndexOf('.')).toLowerCase();
    // Human-friendly download name ("Artist - Title.ext"); cache files stay keyed by track id.
    const rawName = [row.artist, row.title].filter(Boolean).join(' - ').trim() || row.id;
    const utf8Name = rawName.replace(/[\\/:*?"<>|\x00-\x1f]/g, '').trim() || 'audio';
    const asciiName = utf8Name.replace(/[^\x20-\x7e]/g, '').trim() || 'audio';
    reply
      .header('Content-Type', MIME[ext] ?? 'application/octet-stream')
      .header('Content-Length', row.cached_size ?? fs.statSync(row.cached_file).size)
      .header(
        'Content-Disposition',
        `attachment; filename="${asciiName}"; filename*=UTF-8''${encodeURIComponent(utf8Name)}`,
      )
      .header('Cache-Control', 'private, max-age=31536000, immutable')
      .header('ETag', `"${row.id}"`);
    return reply.send(createReadStream(row.cached_file));
  });

  app.post('/api/tracks/like', async (req) => {
    const { trackId, liked } = likeSchema.parse(req.body);
    requireTrack(trackId);
    getDb().prepare('UPDATE tracks SET is_liked = ? WHERE id = ?').run(liked ? 1 : 0, trackId);
    return { track: getTrackDto(trackId) };
  });
}
