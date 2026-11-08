import { createServer } from "node:http";
import { once } from "node:events";
import { pathToFileURL } from "node:url";

import { options, mediaUsage } from "./guides-data.mjs";

export async function startGuidesFixture() {
  const requests = [];
  const state = { failOptions: false, failMedia: false, hideExamples: false };
  const backend = createServer((req, res) => {
    const url = new URL(req.url, "http://fixture");
    requests.push({
      path: url.pathname,
      lang: url.searchParams.get("lang"),
      key: url.searchParams.get("usageKey"),
    });
    res.setHeader("Content-Type", "application/json");
    if (url.pathname === "/api/calculator/options") {
      res.statusCode = state.failOptions ? 503 : 200;
      res.end(JSON.stringify(state.failOptions ? {} : options));
    } else if (url.pathname === "/api/public/media/usages") {
      res.statusCode = state.failMedia ? 503 : 200;
      res.end(
        JSON.stringify(
          state.failMedia ||
            (state.hideExamples &&
              url.searchParams.get("usageType") === "PRICING_EXAMPLE")
            ? []
            : mediaUsage(
                url.searchParams.get("usageType"),
                url.searchParams.get("usageKey"),
                url.searchParams.get("lang"),
              ),
        ),
      );
    } else {
      res.statusCode = 404;
      res.end("{}");
    }
  });
  backend.listen(0, "127.0.0.1");
  await once(backend, "listening");
  process.env.SSR_INTERNAL_API_ORIGIN = `http://127.0.0.1:${backend.address().port}`;
  delete process.env.SSR_ROUTE_ALL_API_INTERNALLY;
  process.env.NG_ALLOWED_HOSTS = "public-guides.invalid,127.0.0.1,localhost";
  const { default: app } = await import(
    "../../dist/frontend/server/server.mjs"
  );
  const frontend = createServer((req, res) => {
    if (req.url.startsWith("/api/")) {
      fetch(process.env.SSR_INTERNAL_API_ORIGIN + req.url)
        .then(async (upstream) => {
          res.writeHead(upstream.status, {
            "Content-Type": "application/json",
          });
          res.end(await upstream.text());
        })
        .catch(() => {
          res.statusCode = 503;
          res.end("{}");
        });
    } else if (req.url.startsWith("/__fixtures/sample-")) {
      res.setHeader("Content-Type", "image/svg+xml");
      res.end(
        '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#ddd"/><path d="M200 120h200v160H200z" fill="#222"/><text x="300" y="350" text-anchor="middle" font-size="24">Synthetic test sample</text></svg>',
      );
    } else app(req, res);
  });
  frontend.listen(0, "127.0.0.1");
  await once(frontend, "listening");
  return {
    origin: `http://127.0.0.1:${frontend.address().port}`,
    requests,
    state,
    close: async () => {
      for (const server of [frontend, backend]) {
        server.closeAllConnections();
        await new Promise((resolve) => server.close(resolve));
      }
    },
  };
}
if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const fixture = await startGuidesFixture();
  console.log(`GUIDES_FIXTURE_ORIGIN=${fixture.origin}`);
  for (const event of ["SIGTERM", "SIGINT"])
    process.once(event, async () => {
      await fixture.close();
      process.exit(0);
    });
}
