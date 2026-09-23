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
must never reset shared state. The harness drops its project volumes on exit.
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
The browser matrix workflow runs on pull requests to `main`, `int` and `dev`,
and can also be started manually. It defines reduced Firefox, WebKit and mobile selections;
do not claim those combinations pass until that job has run successfully. The
disposable stack has no external email or payment delivery: SMTP goes to
Mailpit and the TWINT inbox is disabled.
