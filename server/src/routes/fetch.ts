import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { getDb, inTransaction } from '../db.js';
import { detectProvider } from '../providers/index.js';
import { hashId } from '../providers/types.js';
import { playlistIdFor, playlistSummary, upsertTrack } from '../lib.js';
import type { FetchResponse } from '../types.js';

const bodySchema = z.object({ url: z.string().min(4).max(2048) });

// Collapse concurrent identical fetches into one upstream call.
const inflight = new Map<string, Promise<FetchResponse>>();

export async function fetchRoutes(app: FastifyInstance): Promise<void> {
  app.post('/api/fetch', async (req, reply) => {
    const { url } = bodySchema.parse(req.body);
    const normalized = url.trim();

    const existingJob = inflight.get(normalized);
    if (existingJob) return reply.send(await existingJob);

    const job = (async (): Promise<FetchResponse> => {
      const provider = detectProvider(normalized);
      const result = await provider.fetch(normalized);

      const playlistId = playlistIdFor(normalized);
      const now = Date.now();

      inTransaction(() => {
        const db = getDb();
        db.prepare(
          `INSERT INTO playlists (id, name, source_url, provider, art_url, created_at, updated_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET
             name = excluded.name,
             provider = excluded.provider,
             art_url = COALESCE(excluded.art_url, art_url),
             updated_at = excluded.updated_at`,
        ).run(playlistId, result.name, normalized, result.provider, result.artUrl, now, now);

        db.prepare('DELETE FROM playlist_tracks WHERE playlist_id = ?').run(playlistId);

        const ins = db.prepare(
          'INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)',
        );
        result.tracks.forEach((t, i) => {
          const trackId = upsertTrack(t);
          ins.run(playlistId, trackId, i);
        });
      });

      const summary = playlistSummary(playlistId)!;
      return { playlist: summary, added: result.tracks.length, total: result.tracks.length };
    })().finally(() => inflight.delete(normalized));

    inflight.set(normalized, job);
    return reply.send(await job);
  });
}
