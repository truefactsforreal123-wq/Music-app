import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
export const SERVER_ROOT = path.resolve(here, '..');

export const PORT = Number(process.env.PORT ?? 8787);

export const DATA_DIR = process.env.AURA_DATA_DIR ?? path.join(SERVER_ROOT, 'data');
export const AUDIO_DIR = path.join(DATA_DIR, 'audio');
export const ART_DIR = path.join(DATA_DIR, 'art');

export const YTDLP_PATH =
  process.env.YTDLP_PATH ??
  path.join(SERVER_ROOT, 'bin', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');

export const SERVER_VERSION = '1.0.0';

for (const dir of [DATA_DIR, AUDIO_DIR, ART_DIR]) {
  fs.mkdirSync(dir, { recursive: true });
}
