import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import { startGuidesFixture } from "./fixtures/guides.mjs";

test("informative pages render all locales, real metadata contracts and error states through production SSR", async () => {
  const fixture = await startGuidesFixture();
  const options = {
    headers: { Host: "public-guides.invalid" },
    signal: AbortSignal.timeout(15000),
  };
  try {
    const sitemap = await readFile(
      new URL("../public/sitemap-static.xml", import.meta.url),
      "utf8",
    );
    for (const lang of ["it", "en", "de", "fr"]) {
      for (const path of ["pricing", "colors-finishes"]) {
        const response = await fetch(`${fixture.origin}/${lang}/${path}`, {
          ...options,
          signal: AbortSignal.timeout(15000),
        });
        assert.equal(response.status, 200);
        const html = await response.text();
        const body = html.slice(html.indexOf("<body"));
        assert.match(html, new RegExp(`<html[^>]+lang="${lang}-CH"`));
        assert.match(
          html,
          new RegExp(
            `<link[^>]+rel="canonical"[^>]+href="[^"]*/${lang}/${path}"`,
          ),
        );
        assert.match(html, /<meta name="description" content="[^"<>]{30,}"/);
        assert.doesNotMatch(
          body,
          /(?:PRICING|COLORS_FINISHES|GUIDE_LINKS|ADMIN_GUIDES)\.[A-Z_]+/,
        );
        for (const other of ["it", "en", "de", "fr"]) {
          assert.match(
            html,
            new RegExp(
              `hreflang="${other}-CH"[^>]+href="[^"]*/${other}/${path}"`,
            ),
          );
          assert.ok(sitemap.includes(`https://3d-fab.ch/${other}/${path}`));
        }
        if (path === "colors-finishes") {
          assert.match(body, /catalog-101/);
          assert.match(body, /catalog-102/); // out-of-stock remains visible, matching calculator catalogue.
          assert.match(body, /catalog-201/);
          assert.match(body, /sample-1.svg/);
        } else {
          assert.match(body, /id="cad-title"/);
          assert.doesNotMatch(body, /id="projects-title"/);
          assert.match(body, /2026-10-01/);
          assert.match(body, /20 × 20 × 10/);
          assert.match(body, /12[.,]50/);
          assert.match(body, /<time/);
        }
      }
      assert.ok(
        fixture.requests.some(
          (request) =>
            request.path === "/api/public/media/usages" &&
            request.lang === lang,
        ),
      );
    }
    fixture.state.hideExamples = true;
    let response = await fetch(`${fixture.origin}/it/pricing`, {
      ...options,
      signal: AbortSignal.timeout(15000),
    });
    let html = await response.text();
    assert.equal(response.status, 200);
    assert.doesNotMatch(html, /id="examples-title"/);
    assert.match(html, /Primo confronto gratuito/);
    fixture.state.failOptions = true;
    response = await fetch(`${fixture.origin}/fr/colors-finishes`, {
      ...options,
      signal: AbortSignal.timeout(15000),
    });
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("retry-after"), "60");
    assert.match(response.headers.get("cache-control"), /no-store/);
    html = await response.text();
    assert.match(html, /Le catalogue est indisponible/);
    assert.doesNotMatch(html, /id="catalog-101"/);
    assert.ok(
      fixture.requests.every(
        (request) => !request.path.includes("quote-sessions"),
      ),
    );
  } finally {
    await fixture.close();
  }
});
