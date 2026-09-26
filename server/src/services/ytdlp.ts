import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { YTDLP_PATH } from '../config.js';
import { ProviderError } from '../errors.js';

let resolvedBin: string | null = null;

export function resolveYtDlp(): string | null {
  if (resolvedBin) return resolvedBin;
  if (existsSync(YTDLP_PATH)) {
    resolvedBin = YTDLP_PATH;
    return resolvedBin;
  }
  // Fall back to yt-dlp on PATH
  const probe = spawnSyncSafe('yt-dlp', ['--version']);
  if (probe.ok) {
    resolvedBin = 'yt-dlp';
    return resolvedBin;
  }
  return null;
}

let versionCache: string | null | undefined;

/** Blocking probe — result is memoized, so at most one spawn ever. */
export function ytDlpVersion(): string | null {
  if (versionCache !== undefined) return versionCache;
  const bin = resolveYtDlp();
  if (!bin) {
    versionCache = null;
    return null;
  }
  const probe = spawnSyncSafe(bin, ['--version']);
  versionCache = probe.ok ? probe.stdout.trim() : null;
  return versionCache;
}

/** Never spawns: returns the cached version (null until warmed). For health checks. */
export function peekYtDlpVersion(): string | null {
  return versionCache ?? null;
}

/**
 * Caches the version asynchronously so /api/health never blocks on a spawn.
 * On small hosts a cold yt-dlp probe can take seconds — that must not happen
 * inside Render's health-check window.
 */
export function warmYtDlpVersion(): void {
  if (versionCache !== undefined) return;
  const bin = resolveYtDlp();
  if (!bin) {
    versionCache = null;
    return;
  }
  try {
    const child = spawn(bin, ['--version'], { windowsHide: true });
    let out = '';
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', (d: string) => {
      if (out.length < 4096) out += d;
    });
    child.on('error', () => {
      versionCache = null;
    });
    child.on('close', (code) => {
      const v = out.trim();
      versionCache = code === 0 && v ? v : null;
    });
  } catch {
    versionCache = null;
  }
}

function spawnSyncSafe(bin: string, args: string[]) {
  try {
    const r = spawnSync(bin, args, { encoding: 'utf8', timeout: 10_000, windowsHide: true });
    if (r.error || r.status !== 0) return { ok: false as const, stdout: '', stderr: String(r.stderr ?? r.error ?? '') };
    return { ok: true as const, stdout: String(r.stdout ?? ''), stderr: '' };
  } catch {
    return { ok: false as const, stdout: '', stderr: 'spawn failed' };
  }
}

export interface YtDlpResult {
  code: number;
  stdout: string;
  stderr: string;
}

export function runYtDlp(args: string[], timeoutMs = 60_000): Promise<YtDlpResult> {
  const bin = resolveYtDlp();
  if (!bin) {
    throw new ProviderError(
      'yt-dlp is not installed on the server. Run `npm run setup` inside server/ to fetch it.',
      503,
    );
  }
  return new Promise((resolve, reject) => {
    const child = spawn(bin, args, { windowsHide: true });
    let stdout = '';
    let stderr = '';
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      child.kill();
      reject(new ProviderError('yt-dlp timed out', 504));
    }, timeoutMs);

    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (d: string) => {
      // Cap stdout so a huge playlist can't exhaust memory (roughly 60k tracks).
      if (stdout.length < 128_000_000) stdout += d;
    });
    child.stderr.on('data', (d: string) => {
      if (stderr.length < 1_000_000) stderr += d;
    });
    child.on('error', (e) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      reject(new ProviderError(`Failed to run yt-dlp: ${e.message}`, 500));
    });
    child.on('close', (code) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve({ code: code ?? -1, stdout, stderr });
    });
  });
}

/** Runs yt-dlp with -J and parses the JSON info output. */
export async function ytDlpJson(args: string[], timeoutMs = 90_000): Promise<any> {
  const { code, stdout, stderr } = await runYtDlp(['-J', '--no-warnings', ...args], timeoutMs);
  if (code !== 0 || !stdout.trim()) {
    const tail = stderr.trim().split('\n').slice(-3).join(' | ');
    throw new ProviderError(tail ? `yt-dlp failed: ${tail}` : 'yt-dlp failed with no output');
  }
  try {
    return JSON.parse(stdout);
  } catch {
    throw new ProviderError('Could not parse yt-dlp output');
  }
}

/** Picks the largest usable thumbnail from a yt-dlp thumbnail list. */
export function pickThumb(thumbnails: any[] | undefined | null): string | null {
  if (!Array.isArray(thumbnails) || thumbnails.length === 0) return null;
  let best: { url: string; area: number } | null = null;
  for (const t of thumbnails) {
    if (!t?.url) continue;
    const area = (Number(t.width) || 0) * (Number(t.height) || 0);
    if (!best || area >= best.area) best = { url: t.url, area };
  }
  return best?.url ?? thumbnails[thumbnails.length - 1]?.url ?? null;
}
