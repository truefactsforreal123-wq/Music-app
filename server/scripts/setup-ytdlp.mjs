#!/usr/bin/env node
// Downloads the yt-dlp standalone binary into server/bin/ so the server can
// resolve playlist/stream info without any global installation.
import { createWriteStream, existsSync, mkdirSync, chmodSync, statSync } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BIN_DIR = path.join(ROOT, 'bin');

const ASSETS = {
  win32: 'yt-dlp.exe',
  linux: 'yt-dlp_linux',
  darwin: 'yt-dlp_macos',
};

const asset = ASSETS[process.platform];
if (!asset) {
  console.error(`Unsupported platform: ${process.platform}. Install yt-dlp manually.`);
  process.exit(1);
}

const target = path.join(BIN_DIR, asset === 'yt-dlp.exe' ? 'yt-dlp.exe' : 'yt-dlp');
mkdirSync(BIN_DIR, { recursive: true });

if (existsSync(target) && statSync(target).size > 1_000_000) {
  console.log(`yt-dlp already present at ${target}`);
} else {
  const url = `https://github.com/yt-dlp/yt-dlp/releases/latest/download/${asset}`;
  console.log(`Downloading ${url} ...`);
  const res = await fetch(url, { redirect: 'follow' });
  if (!res.ok || !res.body) {
    console.error(`Download failed (HTTP ${res.status}). Download manually from https://github.com/yt-dlp/yt-dlp/releases and place it at ${target}`);
    process.exit(1);
  }
  await pipeline(Readable.fromWeb(res.body), createWriteStream(target));
  if (process.platform !== 'win32') chmodSync(target, 0o755);
  console.log(`Saved to ${target} (${(statSync(target).size / 1e6).toFixed(1)} MB)`);
}

const probe = spawnSync(target, ['--version'], { encoding: 'utf8' });
if (probe.status === 0) {
  console.log(`yt-dlp ${String(probe.stdout).trim()} ready.`);
} else {
  console.error('Binary downloaded but failed to run:', probe.stderr || probe.error);
  process.exit(1);
}
