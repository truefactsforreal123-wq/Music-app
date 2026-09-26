import type { FastifyInstance } from 'fastify';
import { fetchRoutes } from './fetch.js';
import { playlistRoutes } from './playlists.js';
import { trackRoutes } from './tracks.js';
import { miscRoutes } from './misc.js';

export async function registerRoutes(app: FastifyInstance): Promise<void> {
  await app.register(fetchRoutes);
  await app.register(playlistRoutes);
  await app.register(trackRoutes);
  await app.register(miscRoutes);
}
