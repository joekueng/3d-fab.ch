import { APP_BASE_HREF } from '@angular/common';
import { CommonEngine, isMainModule } from '@angular/ssr/node';
import express from 'express';
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
  const acceptLanguage = req.get('accept-language');
  const preferredLanguages = parseAcceptLanguage(acceptLanguage);
  const lang = resolveInitialLanguage({
    preferredLanguages,
  });

  res.setHeader('Vary', 'Accept-Language');
  res.setHeader('Cache-Control', 'private, no-store');
  res.redirect(302, `/${lang}${querySuffix(req.originalUrl)}`);
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
