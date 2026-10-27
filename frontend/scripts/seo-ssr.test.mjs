import assert from 'node:assert/strict';
import { once } from 'node:events';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

// Check actual HTTP responses: Angular route redirects and response metadata
// alone do not prove what a crawler receives from Express/CommonEngine.
test('public SSR language redirects and missing pages', async (t) => {
  const previousAllowedHosts = process.env.NG_ALLOWED_HOSTS;
  let frontend;
  try {
    process.env.NG_ALLOWED_HOSTS = '127.0.0.1';
    const { default: app } = await import('../dist/frontend/server/server.mjs');
    frontend = app.listen(0, '127.0.0.1');
    await once(frontend, 'listening');
    const origin = `http://127.0.0.1:${frontend.address().port}`;
    const request = (path, options = {}) => fetch(`${origin}${path}`, {
      redirect: 'manual',
      signal: AbortSignal.timeout(15000),
      ...options,
    });

    await t.test('normalizes language and legacy URLs in a single permanent redirect', async () => {
      for (const [path, target] of [
        ['/about?ref=test', '/it/about?ref=test'],
        ['/es-ES/about', '/it/about'],
        ['/de-CH/about?ref=test', '/de/about?ref=test'],
        ['/FR-ch/contact/', '/fr/contact'],
        ['/en-GB', '/en'],
        ['/it-CH/calculator', '/it/calculator/basic'],
        ['/DE/calculator/', '/de/calculator/basic'],
        ['/de-CH/shop/zubehor/kabelhalter', '/de/shop/p/kabelhalter'],
      ]) {
        const response = await request(path);
        assert.equal(response.status, 308, path);
        assert.equal(response.headers.get('location'), target, path);
        await response.text();
      }
    });

    await t.test('keeps a stable crawler default and temporary browser language selection', async () => {
      for (const [headers, status, location] of [
        [{ 'User-Agent': 'Googlebot/2.1', 'Accept-Language': 'de-CH' }, 308, '/it?ref=test'],
        [{ 'User-Agent': 'Mozilla/5.0', 'Accept-Language': 'fr-CH' }, 302, '/fr?ref=test'],
      ]) {
        const response = await request('/?ref=test', { headers });
        assert.equal(response.status, status);
        assert.equal(response.headers.get('location'), location);
        await response.text();
      }
    });

    await t.test('returns localized, uncached 404 pages without canonical or hreflang links', async () => {
      for (const lang of ['it', 'en', 'de', 'fr']) {
        const translations = JSON.parse(await readFile(
          new URL(`../src/assets/i18n/${lang}.json`, import.meta.url), 'utf8',
        ));
        const path = `/${lang}/missing-page?ref=test`;
        const response = await request(path, { headers: { 'Accept-Language': 'it-CH' } });
        assert.equal(response.status, 404, path);
        assert.equal(response.headers.get('location'), null, path);
        assert.match(response.headers.get('cache-control'), /no-store/);
        const html = await response.text();
        const head = html.slice(0, html.indexOf('</head>'));
        assert.ok(html.includes(translations.NOT_FOUND.TITLE), path);
        assert.match(html, new RegExp(`<html[^>]+lang="${lang}-CH"`));
        const homeLink = html.match(/<a[^>]+class="[^"]*not-found-home[^"]*"[^>]*>[\s\S]*?<\/a>/)?.[0] ?? '';
        assert.match(homeLink, new RegExp(`href="/${lang}"`));
        assert.ok(homeLink.includes(translations.NOT_FOUND.BACK_HOME), path);
        assert.match(head, /<meta name="robots" content="noindex, follow"/);
        assert.doesNotMatch(head, /<link[^>]+rel="canonical"/);
        assert.doesNotMatch(head, /<link[^>]+hreflang=/);
        assert.doesNotMatch(html, /NOT_FOUND\./);
      }
    });

    await t.test('keeps known pages at 200 alongside nested missing routes', async () => {
      const paths = [
        ['/de/privacy', 200],
        ['/fr/terms', 200],
        ['/de/about/missing-page', 404],
        ['/fr/calculator/missing-page', 404],
        ['/en/privacy/missing-page', 404],
      ];
      await Promise.all(paths.map(async ([path, status]) => {
        const response = await request(path);
        assert.equal(response.status, status, path);
        const html = await response.text();
        if (status === 200) {
          assert.ok(html.includes(`rel="canonical" href="${origin}${path}"`), path);
          assert.match(html, /hreflang="de-CH"/);
          assert.match(html, /content="index, follow"/);
        }
      }));
    });
  } finally {
    if (frontend?.listening) {
      frontend.closeAllConnections();
      await new Promise(resolve => frontend.close(resolve));
    }
    if (previousAllowedHosts === undefined) delete process.env.NG_ALLOWED_HOSTS;
    else process.env.NG_ALLOWED_HOSTS = previousAllowedHosts;
  }
});
