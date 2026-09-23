# Browser test conventions

The [Playwright coverage plan](../../docs/plans/playwright-e2e.md) is the
target matrix. `coverage-manifest.ts` records current route decisions and gaps;
run `npm run check:e2e-routes` whenever Angular routes change. A `partial`
entry means the named spec covers the route but not every listed action/state.
Use stable scenario IDs in new test titles and update the manifest.

`smoke/` is safe for the deployed dev site. It may read public pages, assets,
catalogue and the login entry point. Keep submissions, QR visits, admin login,
and any server mutation in the fullstack suite. `public/`, `calculator/`,
`shop/`, `checkout/`, `orders/`, `admin/`, and `rendering/` run only against a
loopback stack. Place simulated failure cases in `ui-states/` and label their
doubles in test names and reports.

The root `scripts/e2e/run.sh` owns the stack lifecycle. It generates a Compose
project name, two loopback ports and temporary admin credentials, checks the
database container's project label and database name, and seeds only that run.
The seed in `scripts/e2e/seed.sql` contains synthetic pricing, printer, shop,
CAD and order data. Tests that write data must use unique IDs or names and
must never reset shared state. Pending orders are separate for each browser project and retry so reporting a
payment in one browser cannot alter another browser's starting state.
The harness drops its project volumes on exit.
Do not point `e2e:full` at a non-disposable local database. The QR mutation
spec requires `E2E_DISPOSABLE_STACK=1` from the harness.

Use role and label locators where practical, and locate repeated domain items
within their cards or rows. Wait for visible state or network responses instead
of fixed sleeps. Put sensitive fixture links only in ignored results, never in
source. Tests should fail with a clear message when a required fixture is
absent. Compare money with fixed seed expectations, and use real model slicing
only in the dedicated calculator case.

The current specs establish the first browser layer, including a captured
contact email, an admin-confirmed paid order with PDF and CAD downloads, axe
checks on representative public pages, SSR behavior and a real slicing case.
The remaining workflows in the manifest still require implementation, notably
full admin mutations, private attachments, mixed checkout, deeper document
content checks, failure recovery, keyboard interaction and visual baselines.
The browser matrix runs inside `PR Checks` on pull requests to `main`, `int`
and `dev`; the complete workflow can also be started manually. Formatting runs
first, followed by security, backend, frontend and translation checks. The browser
matrix starts only when all those checks succeed; a failed prerequisite skips it.
One job starts one stack and runs all five Playwright projects sequentially
(`workers: 1`), preserving a full Chromium suite and reduced Firefox, WebKit
and mobile selections via `E2E_BROWSER_MATRIX=true`. The proxy image includes
`nginx.conf` at build time; do not bind-mount checkout paths because the runner
and Docker daemon may have different filesystems;
do not claim those combinations pass until that job has run successfully. The
disposable stack has no external email or payment delivery: SMTP goes to
Mailpit and the TWINT inbox is disabled.

In CI, `E2E_CONTAINER_BROWSER=true` runs Playwright in a disposable container
sharing the proxy network namespace, so its loopback points at the proxy even
when the Gitea job uses a separate container and a host Docker socket. Readiness
is checked inside the proxy. Mailpit is reached at `http://mail:8025` via
`E2E_MAIL_URL`. Browser dependencies are installed from the frontend lockfile
in `scripts/e2e/browser.Dockerfile`; Docker caches that layer across runs.
The harness transfers sources through the build context and copies reports back
with `docker cp`, without host bind mounts. Local headed runs continue to use
the host browser and published loopback ports. Failed readiness prints the
last HTTP error, container state and recent application logs before cleanup.
