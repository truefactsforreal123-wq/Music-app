import type { FastifyInstance } from 'fastify';
import fs from 'node:fs';
import path from 'node:path';
import { SERVER_ROOT } from '../config.js';
import { NotFoundError } from '../errors.js';

/**
 * Public APK download page. APKs live in server/public/ and ship with the repo,
 * so "uploading a new version" = gradle pushes the built APK (publishApk task);
 * Render then redeploys automatically and the page lists the newest file first.
 */

const PUBLIC_DIR = path.join(SERVER_ROOT, 'public');

function listApks(): { name: string; size: number; mtime: number }[] {
  try {
    return fs
      .readdirSync(PUBLIC_DIR)
      .filter((f) => /\.apk$/i.test(f))
      .map((f) => {
        const st = fs.statSync(path.join(PUBLIC_DIR, f));
        return { name: f, size: st.size, mtime: st.mtimeMs };
      })
      .sort((a, b) => b.mtime - a.mtime);
  } catch {
    return [];
  }
}

const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);

function renderPage(): string {
  const apks = listApks();
  const items = apks
    .map(
      (a, i) => `
      <a class="card${i === 0 ? ' latest' : ''}" href="/app/${encodeURIComponent(a.name)}" download>
        <div class="row">
          <span class="name">${escapeHtml(a.name)}</span>
          ${i === 0 ? '<span class="badge">latest</span>' : ''}
        </div>
        <div class="meta">${(a.size / (1024 * 1024)).toFixed(1)} MB &middot; ${new Date(a.mtime).toLocaleString()}</div>
        <div class="cta">Download</div>
      </a>`,
    )
    .join('');
  const empty = apks.length ? '' : '<p class="empty">No builds yet. Run gradlew assembleRelease.</p>';
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Aura — Download</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: system-ui, -apple-system, sans-serif; background:#0d1117; color:#e6edf3;
         min-height:100vh; display:flex; flex-direction:column; align-items:center; padding:32px 16px; }
  h1 { font-size:1.7rem; margin:0 0 4px; }
  .sub { color:#8b949e; font-size:.9rem; margin:0 0 28px; text-align:center; }
  .card { display:block; width:100%; max-width:440px; background:#161b22; border:1px solid #30363d;
          border-radius:12px; padding:16px 18px; margin-bottom:14px; text-decoration:none; color:inherit;
          transition: border-color .15s; }
  .card:hover { border-color:#58a6ff; }
  .card.latest { border-color:#3fb950; }
  .row { display:flex; align-items:center; gap:8px; }
  .name { font-weight:600; font-size:1.02rem; word-break:break-all; }
  .badge { background:#238636; color:#fff; font-size:.68rem; padding:2px 8px; border-radius:999px;
           text-transform:uppercase; letter-spacing:.04em; flex-shrink:0; }
  .meta { color:#8b949e; font-size:.82rem; margin-top:6px; }
  .cta { margin-top:12px; background:#238636; color:#fff; text-align:center; padding:10px;
         border-radius:8px; font-weight:600; }
  .empty { color:#8b949e; text-align:center; }
</style>
</head>
<body>
  <h1>Aura</h1>
  <p class="sub">Playlist importer &amp; offline player &middot; grab the newest build below</p>
  ${items || empty}
</body>
</html>`;
}

export async function landingRoutes(app: FastifyInstance): Promise<void> {
  app.get('/', async (_req, reply) => reply.type('text/html').send(renderPage()));

  app.get('/app/:file', async (req, reply) => {
    const { file } = req.params as { file: string };
    const safe = path.basename(file); // strips any traversal
    if (!/\.apk$/i.test(safe)) throw new NotFoundError();
    const full = path.join(PUBLIC_DIR, safe);
    if (!full.startsWith(PUBLIC_DIR + path.sep) || !fs.existsSync(full)) throw new NotFoundError();
    const st = fs.statSync(full);
    reply
      .header('Content-Type', 'application/vnd.android.package-archive')
      .header('Content-Length', st.size)
      .header('Content-Disposition', `attachment; filename="${safe}"`)
      .header('Cache-Control', 'no-cache');
    return reply.send(fs.createReadStream(full));
  });
}
