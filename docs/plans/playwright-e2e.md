# Playwright coverage plan

Status: implementation baseline completed on 2026-09-23. The disposable Chromium
suite and simulated UI-state suite pass locally; the required deploy gate,
post-deploy dev smoke and scheduled browser matrix are configured. The detailed
matrix remains the target scope. Current implemented/partial/planned decisions
are recorded in `frontend/e2e/coverage-manifest.ts` and must be updated as gaps
are closed.

## 1. Starting point recorded when the plan was written

- `frontend/package.json` already includes `@playwright/test` and `e2e:local` / `e2e:dev` commands. Extend this installation and keep the lockfile authoritative.
- `frontend/e2e/dev-smoke.spec.ts` contains one test: open `/it`, click the shop CTA, and check the destination heading. There is no comprehensive browser coverage yet.
- `frontend/playwright.config.ts` allows loopback HTTP and `https://dev.3d-fab.ch`, uses Chromium and one worker, and starts only Angular locally. Local execution is headed with `slowMo: 700`; automation needs an explicit headless mode without slowing every action.
- `.gitea/workflows/deploy.yaml` runs backend and Angular tests before building, then runs Playwright after successful deployment of `dev`. The current E2E step does not publish report artifacts. Build on Gitea, not a separate GitHub Actions pipeline.
- Local browser URL defaults to `127.0.0.1:4200`, while the development frontend API URL is `localhost:8000`. Standardize the test origin and proxy so browser cookies and origin checks behave consistently. `frontend/src/proxy.conf.json` already proxies `/api`, but the absolute development API URL bypasses it. Angular also references an `environment.local.ts` replacement absent from the inspected checkout; provide an explicit tracked E2E configuration instead of depending on that file.
- Real flows require PostgreSQL, catalogue/pricing/printer/profile data, writable private/public storage, ClamAV for protected attachments, and OrcaSlicer for calculated quotes. Media conversion also requires FFmpeg. Existing `db.sql` mixes schema and seed statements; derive a reviewed minimal fixture rather than importing arbitrary operational data.
- Production uses Angular SSR. Express implements language redirects and `/go/:slug`; product/category responses propagate real 404/503 statuses. Test the built server and its reverse proxy as well as interactive browser behavior.
- Existing backend and Angular tests already cover many detailed calculations, payment races, and validation rules. Add browser integration coverage around these behaviors and retain those focused tests.
- The worktree already contains user changes to Playwright, CI, documentation, and payment scheduling. Implementation must extend those changes without overwriting them. `frontend/playwright-report/` and `frontend/test-results/` currently appear as untracked directories; correct artifact ignore patterns during setup.

## 2. Test environments and boundaries

Use three explicitly selected suites. The deployed-dev command must select only the first suite, even after additional specs are added.

| Suite | Target and dependencies | Purpose |
| --- | --- | --- |
| `smoke` | Existing deployed dev site, or local stack | Public navigation, localized content, assets, catalogue availability, login page. Exclude order/contact/email submissions, admin mutations, and QR scans that change analytics. |
| `fullstack` | Disposable local/CI stack with real Angular SSR, Spring, PostgreSQL, storage, ClamAV and local SMTP capture | Browser-to-database journeys, admin changes, customer access, documents and captured email. A small real OrcaSlicer journey verifies quote creation. |
| `ui-states` | Local stack with narrowly scoped response interception or controlled dependency failures | Deterministic loading, empty, timeout, 429, partial failure, and recovery states. Mark simulated scenarios explicitly in reports. |

Keep the current hostname restriction. Require the fullstack harness to verify its disposable database/storage identity before seeding or resetting. Never reuse the developer's ordinary database or deployment volumes. Use a unique Compose project/run ID, isolated ports and volumes, and teardown only resources owned by that run.

Serve the built frontend and `/api` through one local origin using the existing deployment proxy pattern; configure `SSR_INTERNAL_API_ORIGIN` for server-side requests. Expose public media as the real application expects and confirm private files are inaccessible through that mapping. A development-server option can remain for fast UI authoring, but cannot substitute for SSR checks.

For email tests, enable the existing mail service against a local SMTP capture service with no external delivery. Set all sender/admin/contact addresses to synthetic test addresses, use local assets, disable live TWINT inbox acquisition, and use deterministic local responses for translation and LinkedIn integrations. Test TWINT link/QR generation and payment reporting without opening a real payment app or moving money. Exercise payment confirmation through the normal admin flow; retain backend tests for IMAP/DKIM reconciliation and concurrency.

For the fullstack calculator path, pin an OrcaSlicer build and compatible repository profiles, and use a tiny known model. Keep additional slicing cases in a slower dedicated job. Backend or browser doubles may support UI-state tests, but do not count those as proof of real slicing, pricing, persistence, or SSR integration. Use real ClamAV for at least the accepted-upload journey and a dedicated scanner rejection/unavailable scenario.

## 3. Implementation order and acceptance gates

### Phase 1 — Harness, isolation, and coverage inventory

1. Extend the existing config with explicit suite selection and browser projects. Keep `e2e:local` as the visible authoring workflow; make CI headless without `slowMo`, and disable accidental server reuse in CI.
2. Add an E2E tsconfig and typecheck command; `tsconfig.app.json` alone does not establish that browser test files compile correctly.
3. Add a disposable Compose/harness setup for the complete stack, health/readiness checks, bounded startup timeouts, log capture, seed and teardown. Confirm runner support for Docker, service networking, Java 21, Node 22, and browser dependencies before wiring the required CI gate.
4. Create baseline fixtures and typed API helpers. Prefer existing authenticated admin/public APIs for setup; use a local seed runner for inaccessible states and pricing/profile reference data. Avoid adding an unauthenticated reset endpoint to the deployable application.
5. Create a coverage manifest mapping each route, action and important state below to a stable scenario ID, spec, suite, fixture, priority and implementation status. Mark conditional or unavailable capabilities explicitly. Add a route-inventory check so new routes require a coverage decision.
6. Ignore reports, results, downloaded files, local mail and authentication state. Upload failure evidence only as CI artifacts with bounded retention.

Acceptance: a clean checkout can start, seed, run the current smoke flow plus an authenticated admin read, and tear down using documented commands. Repeating the run starts from independent data. Missing dependencies fail clearly instead of skipping core scenarios.

### Phase 2 — Public site and authentication

Implement public route coverage, languages, navigation, responsive menus, and admin login/logout/guard behavior. Add minimal shared fixtures for language, browser errors, and UI helpers using the existing shared controls.

Acceptance: every public route template is exercised with representative data; all four locales have navigation/content checks; anonymous users cannot read admin data; successful authentication survives a reload and logout removes access. At least one login test enters the password through the UI.

### Phase 3 — Revenue and customer workflows

Implement shop cart to checkout to order, real model to quote to order, CAD checkout, mixed orders, private information/attachments, session resume, payments, PDFs, CAD downloads and locally captured emails. Prioritize exact totals and state/access boundaries.

Acceptance: a customer-created order is visible in admin; confirming payment changes the customer's view and permitted downloads; order totals match the controlled fixture expectations; email links work in a new browser context; duplicate clicks do not create unintended duplicate actions.

### Phase 4 — Complete admin workflows

Cover every admin route and each supported mutation below, including validation and failure recovery. Verify persisted changes after reload and their public effect where applicable.

Acceptance: each admin area has a successful read/edit workflow, applicable validation checks, and an expired-session case. Every exposed create/update/delete or deactivate action is accounted for in the coverage manifest.

### Phase 5 — Browsers, accessibility, rendering, and failures

Expand the critical paths to Firefox/WebKit and mobile emulation, add keyboard and accessibility checks, selected screenshot baselines, SSR status/metadata checks, and controlled network/dependency failures.

Acceptance: supported browser projects pass; key pages fit narrow screens and long translations; baseline images are reviewed; SSR failures return the intended HTTP status; known failures recover without losing unrelated user input.

### Phase 6 — Required CI gates and maintenance

Introduce an isolated E2E job before deployment, while retaining the deployed-dev smoke check. Start with Chromium critical journeys, then expand once duration and reliability are measured. Add broader scheduled/manual runs and document ownership of failed scenarios.

Acceptance: failed required E2E checks prevent deployment; post-deploy smoke failure is visible; reports and logs survive failure; cleanup runs after failure/cancellation with abandoned run resources recoverable by a scoped cleanup process.

## 4. Complete route and workflow matrix

`/:lang` below means `it`, `en`, `de`, and `fr`. Run all static public route checks in every locale; use the reduced browser matrix in section 7 for expensive mutations. Dynamic routes use fixture IDs/slugs, including invalid and inactive examples.

### Public pages and shared UI

| Surface | Required scenarios |
| --- | --- |
| `/` and unprefixed links | Initial language negotiation; bot/default behavior; redirect status and query preservation; supported and unsupported language prefixes; trailing slash and legacy canonical redirects from `server-routing.ts`. |
| `/:lang` home | Header/footer links, primary CTAs, project/media sections, carousel controls and touch gestures, media empty/failure states, LinkedIn display/fallback, quick-request handoff into contact. |
| `/:lang/about` | Content, media, CTAs, internal navigation and localized headings. |
| `/:lang/materials` | Material cards/details and their available controls, relevant links, missing images and empty data. |
| `/:lang/contact` | Each request type; private/company conditional fields; required/email/consent validation; attachments/previews/removal; submit/busy/success/failure states; quick-request and calculator consultation prefill; persisted admin request and captured notifications. |
| `/:lang/privacy`, `/:lang/terms` | Localized content, footer links, consent links, keyboard access and navigation back to the form. |
| Shared layout/components | Desktop/mobile navigation, language switch preserving route and session parameters, reload/back/forward, cookie/local-storage isolation where used, dialogs closing and restoring focus, dropdowns, color controls, dropzones, alerts and disabled/busy buttons. |
| Unknown routes | Document current wildcard redirect behavior; distinguish it from missing catalogue resources, which require HTTP 404. |

### Calculator, catalogue, checkout, and orders

| Surface | Required scenarios |
| --- | --- |
| `/:lang/calculator` | Redirect to `basic`, including preserved session/query behavior. |
| `/:lang/calculator/basic` | Valid STL and 3MF, multiple files, quantities, material/color/quality, preview, remove/replace, loading/progress, calculate, totals, checkout handoff. Include a genuine small-model slicing journey. |
| `/:lang/calculator/advanced` | Printer/nozzle/layer/infill/settings controls actually offered by the UI; compatible/incompatible selections; global/per-file settings; basic/advanced switching preserves drafts; setting changes require recalculation. |
| Calculator exceptional states | Unsupported/corrupt/oversized/empty files, no models, duplicate names, partial success, outside printer volume, split-printing acceptance, custom-quote handoff, zero quote, slicer failure, 429 and countdown, recover/retry, stale responses during edits, removing the final item. Generate large fixtures at runtime only in dedicated boundary cases. |
| Calculator saved sessions | Copy link and email to local capture; resume `?session=` in a fresh context; quantities/settings/notes/files restored; recalculation before email; failed/disabled mail; send cooldown; expired or ordered session handling; reordering through the intended fork path. |
| `/:lang/calculator/animation-test` and unprefixed equivalent | Explicit diagnostic-route render/noindex check and supported controls; record separately from customer purchase journeys. |
| `/:lang/shop`, `/:lang/shop/:categorySlug` | Categories and nested category behavior, available filtering/navigation controls, cards, empty catalogue/category, inactive products, reload and back/forward. |
| `/:lang/shop/p/:productSlug`, `/:lang/shop/:categorySlug/:productSlug` | Canonical/legacy path behavior; localized names and descriptions; gallery/model previews; variant/material/color choice; CHF price updates; quantity validation; add to cart; missing/inactive product; unavailable API. |
| Cart in its existing UI | Add multiple products/variants, merge behavior, quantity updates, remove/clear, totals, reload persistence, independent browser isolation, unavailable product and failed update recovery. Use the existing cart/session integration rather than inventing a `/cart` route. |
| `/:lang/checkout` | Calculator-only, shop-only and mixed sessions; private/company validation; same/different shipping address; legal consent; notes/files; item edits; shipping available/unavailable; pending totals block submit; session missing/expired/converted; submit and redirect; double-click and network failure recovery. |
| `/:lang/checkout/cad` | Admin-created CAD quote link, CAD hours/charges, editable quantity/color and recalculated totals, locked or prohibited edits, correct route/session handling, successful order. |
| `/:lang/order/:orderId`, `/:lang/co/:orderId` | Alias/canonical behavior and query/fragment preservation; invalid ID; order items/totals; pending, reported, paid, production, shipped, completed and cancelled displays as supported; polling update/focus/visibility; terminal stop; failed polls retain visible data. |
| Payment and documents | TWINT image/QR/link generation and fallback, payment reporting and repeated submission, admin confirmation, paid state distinct from production, confirmation/QR-bill/invoice downloads with correct access and content, CAD downloads unavailable before confirmation and available after it. Validate MIME, filename, nonempty/parseable content and fixture totals; verify ZIP contents for multiple CAD files. |
| Private order information | Draft notes and attachments survive recalculation/checkout; order receives an independent copy; private email fragment is consumed/removed; fresh context with correct link works; missing/wrong/another order's token cannot access private files; later append and admin unread/mark-read behavior. |
| `/go/:slug` | Real SSR bridge to `/api/public/qr/:slug`, intended destination and query handling, missing/inactive link, SVG download and scan statistics. Run in the isolated stack because visits can record analytics. |

The saved-session guide `docs/plans/quote-session-email.md` intentionally permits complete quote restoration from a session link. Older wording in `docs/order-information.md` describes stricter draft access. Follow current services and the saved-session contract for quote resume, and keep independent draft/order credentials protected. Reconcile the documentation during implementation rather than writing contradictory tests.

### Admin routes

Every route also needs empty/loading/error states, direct navigation/reload, anonymous access checks, and applicable session-expiry behavior. For destructive actions, verify both cancel and confirm on test-owned records.

| Route below `/:lang/admin` | Required scenarios |
| --- | --- |
| `login`, root, shell | Correct/incorrect password, busy state, throttling/cooldown, return routing as implemented, root redirect to orders, navigation, logout, expired/invalid cookie. Isolate throttle tests so they cannot block other workers. |
| `orders` | List/filter/statistics, details, item previews/downloads, payment method, valid/invalid status transitions, customer-visible status, confirmation/invoice, CAD upload/remove/download gating, email audit/resend into local capture, information unread badge/read acknowledgement and private customer link. |
| `filament-stock` | Materials and variants create/update; variant deletion; field validation, stock values, colors, active state and public calculator option effects. |
| `contact-requests` | List/details, attachments/download, status update, email audit/resend, customer request appearing after form submission. |
| `sessions` | List/statistics/details, quote contents and totals, resume link, deletion confirmation/cancellation and resulting unavailable session. |
| `cad-invoices` | List, form validation, CAD quote/invoice creation, generated checkout link and customer completion. |
| `qr` | Create/edit/enable/disable, destination and slug validation, SVG export, overview/detail statistics, controlled scan and missing geo-data fallback. |
| `media`, alias `home-media` | Alias redirect, collection/section choice, upload/replace, metadata in four languages, translation success/failure, primary image, sort order, deactivate usage, conversion readiness/failure, public visibility and private-original protection. |
| `home-projects` | Create/edit/deactivate, localized text/translation, image upload, primary/sort/deactivate, resulting home-page project. |
| `shop` | Product create/edit/delete, search/category/status controls, category tree create/edit/delete validation, localized rich text/translation, variants and prices, active state, gallery and model management, resulting catalogue/product/structured data. |
| `linkedin` | Status, refresh, busy/error/disabled states; controlled provider responses; public feed update/fallback. |

No standalone printer, pricing-policy, or translation page appears in the current admin route table. Seed printer/pricing data for quotation assertions and test translation controls within their actual pages; add separate page tests only when those pages exist.

## 5. Fixtures, authentication, and assertions

- Baseline data: compatible printer/material/profile mappings, fixed pricing and shipping configuration, active/inactive material variants and categories/products, multiple product variants, synthetic media/projects, QR links, calculator/CAD/mixed sessions, contacts, and orders in supported states.
- Asset fixtures: tiny valid STL and 3MF, corrupt model, representative image/PDF, small media clip, CAD files, and invalid MIME/signature examples. Commit only small synthetic fixtures with documented provenance; generate boundary-size inputs temporarily.
- Use exact independently specified expected amounts for stable seeded shop/CAD totals. For real slicing, fix the slicer/profile versions and assert justified duration/material ranges plus exact pricing relationships. Comparing two fields from the same erroneous response is insufficient.
- Give each test unique record IDs/names and capture returned IDs. Keep baseline reference data immutable. Start with one worker for writes; enable parallelism only for disjoint records or separate backend/database/storage instances. A browser context alone does not isolate server data. Serialize global settings, expiry, throttle and catalogue-baseline mutation cases.
- Seed baseline once per disposable stack. Perform per-test setup/cleanup for owned records, and drop the entire run's database/volumes at teardown. Avoid resetting a shared database while other tests are running. Retries must get new test records.
- Reuse login state only for tests whose server-side data is independent. Keep authentication/logout/expiry cases in separate contexts. Ignore saved state and regenerate it per run. Follow Playwright's [authentication guidance](https://playwright.dev/docs/auth).
- This backend uses a session cookie and `Origin`/`Referer` validation in `AdminCsrfProtectionFilter`, not a conventional CSRF-token header. Setup API requests must use the intended origin and cookie; test missing/foreign origin rejection without weakening the filter.
- Customer private information uses separate credentials; test fresh browser contexts and cross-record denial. Public order responses must not expose private tokens or customer details beyond the established DTO contract.
- Prefer accessible role/label locators from the existing shared controls. Add stable `data-testid` only where semantic locators cannot identify a repeated domain item. Avoid generated CSS selectors, translated-text dependence for generic helpers, fixed sleeps, and brittle canvas pixel assertions.
- Wait for visible results, specific network responses, or bounded `expect.poll` on asynchronous work. Use browser clock control for browser polling/cooldowns only; server expiry, scheduled mail and rate limits need dedicated fixture timestamps or isolated test configuration.
- Fail on unexpected page errors, application console errors and first-party request failures. Negative scenarios declare their expected errors narrowly; background polling and deliberate aborts require explicit handling.

## 6. SSR, accessibility, localization, and visual checks

- Request built-server HTML using Playwright's request fixture, including redirects with automatic following disabled where needed. Verify root language headers/status, canonical and hreflang links, HTML language, title/description, robots, sitemap routes, and noindex on checkout/order/admin/diagnostic pages.
- For product/category success, missing resources and backend outage, assert actual HTTP 200/404/503, `Retry-After` on 503, error cache policy, and useful HTML. Exercise server outages using a controlled backend/proxy; `page.route()` cannot intercept SSR's server-side requests.
- Check the single product JSON-LD block against displayed variant price/currency/SKU/material/color and images; verify it updates on selection and disappears on error or navigation. Check initial SSR HTML and post-hydration metadata to detect unwanted home canonical replacement.
- Cover Italian/English/German/French language switching, untranslated key leakage, currency/date formatting, long labels, validation messages and persisted locale behavior. Keep `check:i18n` as a complementary static check.
- Add automated accessibility scans with a compatible `@axe-core/playwright` version, plus explicit keyboard checks for navigation, dialogs, forms, color selectors, focus restoration, labels and error announcements. Record existing violations with owners; automated scans are not a complete accessibility certification.
- Review desktop and narrow-mobile screenshots for home, calculator, product, checkout, order and representative admin screens. Pin OS/browser/fonts, wait for image/font readiness, reduce animation and mask only genuinely variable fields. Use WebGL readiness/interaction assertions for 3D previews; reserve visual baselines for stable rendering.
- Exercise touch menus/carousels and check clipping/overflow at phone, tablet and desktop widths. Mobile emulation provides browser coverage; real-device TWINT handoff and hardware rendering remain a separate manual check if required for release.

## 7. Browser and CI matrix

| Trigger | Coverage |
| --- | --- |
| Each change before deployment | Existing backend/Angular checks, E2E typecheck, all public route smoke checks in Chromium, isolated Chromium critical shop/calculator/CAD/contact/admin journeys. |
| Successful `dev` deployment | Explicit `smoke` selection against the existing allowed dev hostname, headless Chromium, readiness and public asset/API checks. |
| Scheduled/manual comprehensive job | Entire functional matrix in Chromium; critical purchase/auth/upload paths in Firefox and WebKit; mobile Chromium/WebKit; all locale route checks, representative localized forms, visual/accessibility checks, and additional real slicing/media scenarios. |

Avoid multiplying every expensive test by every browser, locale and viewport. Each manifest scenario declares its required combinations; pairwise coverage supplements a fully exercised primary Chromium/Italian configuration. Validate Gitea support for schedules, artifacts and any container runner requirements before choosing workflow syntax. Playwright [projects](https://playwright.dev/docs/test-projects) provide browser and configuration grouping.

Start with one worker in constrained CI. Measure startup, browser runtime, slicing time and memory before increasing workers or sharding. Set runtime budgets after the first stable baseline rather than promising an unmeasured duration. Install browsers with the locked Playwright CLI and match any browser container image version to that dependency; follow the official [CI guidance](https://playwright.dev/docs/ci).

Publish HTML and machine-readable reports, failure screenshots/traces, relevant video, backend/frontend/service logs, fixture/run identifiers and dependency versions on failure. Use at most a small bounded retry allowance and make flaky passes visible; retries must not hide a recurring failure. Restricted artifact retention is appropriate because traces can contain test authentication cookies and private fixture links.

## 8. Proposed files and commands

Extend the current files instead of creating competing configurations:

```text
frontend/playwright.config.ts
frontend/tsconfig.e2e.json
frontend/e2e/
  smoke/                 public deployed-dev checks
  public/                navigation, contact, locales
  calculator/            uploads, settings, slicing, saved sessions
  shop/                  catalogue, variants, cart
  checkout/              calculator/shop/mixed/CAD checkout
  orders/                tracking, payment, information, downloads
  admin/                 every admin route
  rendering/             SSR, SEO, visual, accessibility
  fixtures/              typed test fixtures, synthetic assets/data
  support/               API/auth/mail helpers, diagnostics
  coverage-manifest.ts   route/action/scenario mappings
docker-compose.e2e.yml
scripts/e2e/             startup, seed, readiness, teardown
backend/src/...          isolated seed support only where APIs cannot prepare data
.gitea/workflows/...     extend existing deploy gates and broader test trigger
```

Proposed command interface, to implement and document in `frontend/README.md` with its existing link from `frontend/AGENTS.md`:

- `npm run e2e:local`: visible local authoring against the chosen isolated stack.
- `npm run e2e:dev`: strictly the public smoke selection.
- `npm run e2e:full`: complete isolated primary-browser suite.
- `npm run e2e:ui`: Playwright UI mode for local debugging.
- `npm run e2e:report`: open the latest HTML report.
- `npm run e2e:typecheck`: check test TypeScript separately.
- A repository harness command owns start/seed/run/cleanup; package scripts must clearly distinguish managing a stack from targeting an already running one.

Follow Playwright's [best practices](https://playwright.dev/docs/best-practices) for isolated tests, user-facing locators, controlled data and retrying assertions. Keep helpers small and feature-specific rather than building a second generic UI framework.

## 9. Definition of complete

1. Every current route, admin action, and critical state in the manifest has implemented coverage or an explicit reviewed exclusion with its alternative verification.
2. Shop, real calculator, CAD, contact, payment confirmation and private-information journeys pass against the disposable full stack. At least one journey spans customer UI, persisted backend state, admin UI and locally captured email.
3. Failure scenarios assert useful user feedback and recovery, including uploads, quote rate limits, shipping/checkout, admin authentication, provider failures and SSR errors.
4. Critical journeys pass in Chromium, Firefox and WebKit; all locales and representative mobile layouts are covered according to the recorded matrix.
5. A clean run and repeat run pass independently; tests do not depend on execution order, shared developer data or external provider availability. Run repetitions during stabilization to detect leaks and flakes.
6. Required CI failures block deployment, reports explain failures, and disposable resources are cleaned up. The deployed-dev smoke job remains restricted to its approved public scope.
7. Documentation records setup, fixture ownership, adding coverage, updating reviewed visual baselines, debugging, limitations and the separate manual/device checks. Browser coverage complements the existing backend/unit suites; it cannot prove every possible input or external-service behavior.
