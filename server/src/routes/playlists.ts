import type { FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { z } from 'zod';
import { getDb, getPlaylistFull, inTransaction } from '../db.js';
import { NotFoundError } from '../errors.js';
import { nextPosition, playlistSummary, upsertTrack } from '../lib.js';
import type { TrackInput } from '../types.js';

const createSchema = z.object({ name: z.string().min(1).max(200) });
const renameSchema = z.object({ name: z.string().min(1).max(200) });
const addTracksSchema = z.object({
  tracks: z.array(z.any()).max(5000).optional(),
  trackIds: z.array(z.string()).max(5000).optional(),
});
const reorderSchema = z.object({ trackIds: z.array(z.string()).min(1).max(5000) });

export async function playlistRoutes(app: FastifyInstance): Promise<void> {
  app.get('/api/playlists', async () => {
    const rows = getDb()
      .prepare(
        `SELECT p.*, COUNT(pt.track_id) AS track_count
         FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
         GROUP BY p.id ORDER BY p.updated_at DESC`,
      )
      .all() as unknown as Array<Record<string, unknown>>;
    return {
      playlists: rows.map((r) => ({
        id: r.id,
        name: r.name,
        sourceUrl: r.source_url,
        provider: r.provider,
        artUrl: r.art_url,
        updatedAt: r.updated_at,
        trackCount: r.track_count,
      })),
    };
  });

  app.post('/api/playlists', async (req, reply) => {
    const { name } = createSchema.parse(req.body);
    const id = `pl_${randomUUID().replace(/-/g, '').slice(0, 16)}`;
    const now = Date.now();
    getDb()
      .prepare('INSERT INTO playlists (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)')
      .run(id, name, now, now);
    return reply.status(201).send({ playlist: playlistSummary(id) });
  });

  app.get('/api/playlists/:id', async (req) => {
    const { id } = req.params as { id: string };
    const full = getPlaylistFull(id);
    if (!full) throw new NotFoundError('Playlist not found');
    return { playlist: full };
  });

  app.patch('/api/playlists/:id', async (req) => {
    const { id } = req.params as { id: string };
    const { name } = renameSchema.parse(req.body);
    const res = getDb().prepare('UPDATE playlists SET name = ?, updated_at = ? WHERE id = ?').run(name, Date.now(), id);
    if (res.changes === 0) throw new NotFoundError('Playlist not found');
    return { playlist: playlistSummary(id) };
  });

  app.delete('/api/playlists/:id', async (req) => {
    const { id } = req.params as { id: string };
    const res = getDb().prepare('DELETE FROM playlists WHERE id = ?').run(id);
    if (res.changes === 0) throw new NotFoundError('Playlist not found');
    return { ok: true };
  });

  app.post('/api/playlists/:id/tracks', async (req, reply) => {
    const { id } = req.params as { id: string };
    if (!playlistSummary(id)) throw new NotFoundError('Playlist not found');
    const { tracks, trackIds } = addTracksSchema.parse(req.body);

    const ids: string[] = [];
    for (const trackId of trackIds ?? []) {
      const exists = getDb().prepare('SELECT 1 AS x FROM tracks WHERE id = ?').get(trackId);
      if (exists) ids.push(trackId);
    }
    inTransaction(() => {
      const ins = getDb().prepare(
        'INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)',
      );
      let pos = nextPosition(id);
      for (const t of (tracks ?? []) as TrackInput[]) {
        ins.run(id, upsertTrack(t), pos++);
      }
      for (const trackId of ids) {
        ins.run(id, trackId, pos++);
      }
      getDb().prepare('UPDATE playlists SET updated_at = ? WHERE id = ?').run(Date.now(), id);
    });
    return reply.status(201).send({ added: ((tracks ?? []).length) + ids.length, playlist: playlistSummary(id) });
  });

  app.delete('/api/playlists/:id/tracks/:trackId', async (req) => {
    const { id, trackId } = req.params as { id: string; trackId: string };
    const res = getDb().prepare('DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?').run(id, trackId);
    if (res.changes === 0) throw new NotFoundError('Track not in playlist');
    getDb().prepare('UPDATE playlists SET updated_at = ? WHERE id = ?').run(Date.now(), id);
    return { ok: true };
  });

  app.post('/api/playlists/:id/reorder', async (req) => {
    const { id } = req.params as { id: string };
    const { trackIds } = reorderSchema.parse(req.body);
    inTransaction(() => {
      const current = getDb()
        .prepare('SELECT track_id FROM playlist_tracks WHERE playlist_id = ? ORDER BY position')
        .all(id) as unknown as Array<{ track_id: string }>;
      const currentSet = new Set(current.map((r) => r.track_id));
      const newSet = new Set(trackIds);
      if (currentSet.size !== newSet.size || [...currentSet].some((t) => !newSet.has(t))) {
        throw new NotFoundError('Reorder list does not match playlist contents');
      }
      const del = getDb().prepare('DELETE FROM playlist_tracks WHERE playlist_id = ?');
      del.run(id);
      const ins = getDb().prepare(
        'INSERT OR REPLACE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)',
      );
      trackIds.forEach((trackId, i) => ins.run(id, trackId, i));
      getDb().prepare('UPDATE playlists SET updated_at = ? WHERE id = ?').run(Date.now(), id);
    });
    return { ok: true, playlist: playlistSummary(id) };
  });
}
