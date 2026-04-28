import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine, isMainModule } from '@angular/ssr/node';
import express from 'express';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import bootstrap from './main.server';
import { resolveRequestOrigin } from './core/request-origin';
import {
  parseAcceptLanguage,
  resolveInitialLanguage,
} from './app/core/i18n/language-resolution';
import { resolvePublicRedirectTarget } from './server-routing';

const serverDistFolder = dirname(fileURLToPath(import.meta.url));
const browserDistFolder = resolve(serverDistFolder, '../browser');
const indexHtml = join(serverDistFolder, 'index.server.html');
const require = createRequire(import.meta.url);
const escapeHtml = require('escape-html') as (value: string) => string;

const app = express();
const commonEngine = new CommonEngine();

/**
 * Example Express Rest API endpoints can be defined here.
 * Uncomment and define endpoints as necessary.
 *
 * Example:
 * ```ts
 * app.get('/api/**', (req, res) => {
 *   // Handle API request
 * });
 * ```
 */

/**
 * Serve static files from /browser
 */
app.get(
  '**',
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
  }),
);

app.get('/', (req, res) => {
  const userAgent = req.get('user-agent');
  const preferredLanguages = parseAcceptLanguage(req.get('accept-language'));
  const lang = resolveInitialLanguage({
    preferredLanguages,
  });
  const stableRedirect = shouldUseStableRootRedirect(
    userAgent,
    preferredLanguages,
  );

  res.setHeader('Vary', 'Accept-Language, User-Agent');
  res.setHeader('Cache-Control', 'private, no-store');
  res.redirect(
    stableRedirect ? 308 : 302,
    `/${stableRedirect ? 'it' : lang}${querySuffix(req.originalUrl)}`,
  );
});

app.get('/go/:slug', (req, res) => {
  const slug = String(req.params['slug'] ?? '').trim();
  const target = `/api/public/qr/${encodeURIComponent(slug)}${querySuffix(req.originalUrl)}`;

  res.setHeader('Vary', 'Accept-Language, User-Agent');
  res.setHeader('Cache-Control', 'private, no-store');
  res.type('html').send(renderPublicQrBridgePage(target));
});

app.get('**', (req, res, next) => {
  const targetPath = resolvePublicRedirectTarget(req.path);
  if (!targetPath) {
    next();
    return;
  }

  res.redirect(308, `${targetPath}${querySuffix(req.originalUrl)}`);
});

/**
 * Handle all other requests by rendering the Angular application.
 */
app.get('**', (req, res, next) => {
  const { originalUrl, baseUrl } = req;
  const origin = resolveRequestOrigin(req);

  commonEngine
    .render({
      bootstrap,
      documentFilePath: indexHtml,
      url: `${origin ?? 'http://localhost:4000'}${originalUrl}`,
      publicPath: browserDistFolder,
      providers: [{ provide: APP_BASE_HREF, useValue: baseUrl }],
    })
    .then((html) => res.send(html))
    .catch((err) => next(err));
});

/**
 * Start the server if this module is the main entry point.
 * The server listens on the port defined by the `PORT` environment variable, or defaults to 4000.
 */
if (isMainModule(import.meta.url)) {
  const port = process.env['PORT'] || 4000;
  app.listen(port, () => {
    console.log(`Node Express server listening on http://localhost:${port}`);
  });
}

export default app;

function querySuffix(url: string): string {
  const queryIndex = String(url ?? '').indexOf('?');
  return queryIndex >= 0 ? String(url).slice(queryIndex) : '';
}

function shouldUseStableRootRedirect(
  userAgent: string | undefined,
  preferredLanguages: readonly string[],
): boolean {
  return preferredLanguages.length === 0 || isLikelyCrawler(userAgent);
}

function isLikelyCrawler(userAgent: string | undefined): boolean {
  const normalized = String(userAgent ?? '').toLowerCase();
  if (!normalized) {
    return false;
  }

  return /(bot|crawler|spider|slurp|bingpreview|google-read-aloud)/.test(
    normalized,
  );
}

function renderPublicQrBridgePage(target: string): string {
  const escapedTarget = escapeHtml(target);
  return `<!doctype html>
<html lang="it">
  <head>
    <meta charset="utf-8" />
    <title>Reindirizzamento...</title>
    <meta name="robots" content="noindex,nofollow,noarchive" />
    <meta http-equiv="refresh" content="0;url=${escapedTarget}" />
    <script>
      window.location.replace(${JSON.stringify(target)});
    </script>
  </head>
  <body>
    <p>Reindirizzamento in corso...</p>
    <p><a href="${escapedTarget}">Continua</a></p>
  </body>
</html>`;
}
