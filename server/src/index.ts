import os from 'node:os';
import Fastify from 'fastify';
import cors from '@fastify/cors';
import compress from '@fastify/compress';
import rateLimit from '@fastify/rate-limit';
import { PORT, SERVER_VERSION } from './config.js';
import { initDb } from './db.js';
import { registerRoutes } from './routes/index.js';
import { AppError } from './errors.js';

const app = Fastify({
  logger: { level: 'warn' },
  bodyLimit: 1_048_576,
  connectionTimeout: 30_000,
  keepAliveTimeout: 72_000,
});

await app.register(cors, { origin: true });
await app.register(compress, { threshold: 1024 });
await app.register(rateLimit, { max: 600, timeWindow: '1 minute' });

app.setErrorHandler((err, req, reply) => {
  if (err instanceof AppError) {
    reply.status(err.statusCode).send({ error: err.message });
    return;
  }
  const status = (err as { statusCode?: number }).statusCode;
  if (status && status < 500) {
    reply.status(status).send({ error: err.message });
    return;
  }
  req.log.error(err);
  reply.status(500).send({ error: 'Internal server error' });
});

app.get('/', async () => ({
  name: 'Aura server',
  version: SERVER_VERSION,
  health: '/api/health',
}));

initDb();
await app.register(registerRoutes);

await app.listen({ port: PORT, host: '0.0.0.0' });

const ips = lanIps();
const line = '─'.repeat(56);
console.log(`
┌${line}┐
│  AURA SERVER  v${SERVER_VERSION} — listening on port ${PORT}${' '.repeat(Math.max(0, 15 - String(PORT).length))}│
│${' '.repeat(56)}│
│  On this PC:      http://localhost:${PORT}${' '.repeat(Math.max(0, 23 - String(PORT).length))}│
${ips.map((ip) => `│  On your phone:   http://${ip}:${PORT}${' '.repeat(Math.max(0, 17 - ip.length - String(PORT).length))}│`).join('\n')}
│${' '.repeat(56)}│
│  Paste one of the phone URLs into Aura → Settings → Server${' '.repeat(0)}│
└${line}┘
`);

function lanIps(): string[] {
  const out: string[] = [];
  for (const list of Object.values(os.networkInterfaces())) {
    for (const ni of list ?? []) {
      if (ni.family === 'IPv4' && !ni.internal) out.push(ni.address);
    }
  }
  return out;
}

for (const sig of ['SIGINT', 'SIGTERM'] as const) {
  process.on(sig, async () => {
    await app.close();
    process.exit(0);
  });
}
