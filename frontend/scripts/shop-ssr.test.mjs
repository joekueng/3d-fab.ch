import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { test } from 'node:test';

// Exercise the actual production Express/CommonEngine bridge. Interceptor unit
// tests provide REQUEST themselves, which cannot detect a missing server provider.
test('shop SSR reaches the internal API without the E2E routing override', async () => {
  const received = [];
  let failCatalog = false;
  const backend = createServer((req, res) => {
    const url = new URL(req.url, 'http://fixture');
    received.push({ path: url.pathname, lang: url.searchParams.get('lang') });
    res.setHeader('Content-Type', 'application/json');
    if (url.pathname === '/api/shop/categories') {
      res.end('[]');
    } else if (url.pathname === '/api/shop/products') {
      res.statusCode = failCatalog ? 500 : 200;
      res.end(JSON.stringify({ category: null, categorySlug: null, featuredOnly: null, products: [] }));
    } else {
      res.statusCode = 404;
      res.end('{}');
    }
  });
  const previousOrigin = process.env.SSR_INTERNAL_API_ORIGIN;
  const previousOverride = process.env.SSR_ROUTE_ALL_API_INTERNALLY;
  let frontend;
  try {
    backend.listen(0, '127.0.0.1');
    await once(backend, 'listening');
    process.env.SSR_INTERNAL_API_ORIGIN = `http://127.0.0.1:${backend.address().port}`;
    delete process.env.SSR_ROUTE_ALL_API_INTERNALLY;
    const { default: app } = await import('../dist/frontend/server/server.mjs');
    frontend = app.listen(0, '127.0.0.1');
    await once(frontend, 'listening');
    const origin = `http://127.0.0.1:${frontend.address().port}`;
    // The public host must never be contacted for catalogue discovery. No Basic
    // Auth is supplied: this also models a proxy stripping its credentials.
    const options = { headers: { Host: 'public-shop.invalid' }, signal: AbortSignal.timeout(15000) };
    const responses = await Promise.all(['it', 'fr'].map(lang => fetch(`${origin}/${lang}/shop`, options)));
    for (const response of responses) {
      assert.equal(response.status, 200);
      assert.match(await response.text(), /shop-hero/);
    }
    for (const lang of ['it', 'fr']) {
      for (const path of ['/api/shop/categories', '/api/shop/products']) {
        assert.ok(received.some(call => call.path === path && call.lang === lang), `${path} lang=${lang}`);
      }
    }
    failCatalog = true;
    const unavailable = await fetch(`${origin}/it/shop`, { ...options, signal: AbortSignal.timeout(15000) });
    assert.equal(unavailable.status, 503);
    assert.equal(unavailable.headers.get('retry-after'), '60');
    assert.match(unavailable.headers.get('cache-control'), /no-store/);
    await unavailable.text();
  } finally {
    for (const server of [frontend, backend]) {
      if (server?.listening) {
        server.closeAllConnections();
        await new Promise(resolve => server.close(resolve));
      }
    }
    if (previousOrigin === undefined) delete process.env.SSR_INTERNAL_API_ORIGIN;
    else process.env.SSR_INTERNAL_API_ORIGIN = previousOrigin;
    if (previousOverride === undefined) delete process.env.SSR_ROUTE_ALL_API_INTERNALLY;
    else process.env.SSR_ROUTE_ALL_API_INTERNALLY = previousOverride;
  }
});
