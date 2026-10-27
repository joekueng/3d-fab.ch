# Print Calculator Frontend

This is a modern Angular standalone application organised around Core, Shared, and Feature areas, with design tokens for easy theming.

## Project Structure

- **Core**: Singleton services, global layout components (Navbar, Footer), guards.
- **Shared**: Reusable dumb UI components (Buttons, Cards, Inputs). No business logic.
- **Features**: Routed product areas (Calculator, Shop, About, Admin, Checkout, and more). Each contains its own pages, components, and services.
- **Styles**: Design tokens and theming layer.

## Getting Started

1. **Install Dependencies**:
   ```bash
   npm install
   ```

2. **Run Development Server**:
   ```bash
   ng serve
   ```
   Navigate to `http://localhost:4200/`.

## Theming

The application uses CSS Variables defined in `src/styles/tokens.scss` and mapped in `src/styles/theme.scss`.

- **Change Colors**: Edit `src/styles/tokens.scss`.
- **Create New Theme**:
  1. Duplicate `src/styles/theme.scss` (e.g., `theme-dark.scss`).
  2. Override the semantic variables (e.g., `--color-bg`, `--color-text`).
  3. Load the new theme file or switch classes on the body tag.

## Preserve the existing UI in every feature

Read [AGENTS.md](AGENTS.md) and the [repository contribution workflow](../README.md#contributing-and-ai-agents-preserve-project-conventions) first. Inspect a comparable existing page and its shared controls before writing markup or SCSS.

- Reuse `app-input`, `app-select`, `app-textarea`, `app-checkbox`, `app-button`, and `app-card` from `src/app/shared/components/` when their semantics match. Inspect their actual inputs/outputs and extend generic capabilities there.
- Reuse `ui-*` primitives in `src/styles/_ui.scss` when only the markup/layout differs. Admin pages also use the existing `section-card` and `section-header` conventions.
- Use semantic tokens from `src/styles/tokens.scss` and `theme.scss`, typography utilities, and existing patterns from `_patterns.scss` and `_admin.scss`. Feature SCSS is for layout or an explained feature-specific treatment, not duplicate controls, hard-coded branding, or a second design system.
- Match the existing responsive layout, spacing, copy, and loading/empty/error/success states. Keep HTML, SCSS, and TypeScript separate; reuse feature services and typed models for API access.
- Add matching translation keys in `it`, `en`, `de`, and `fr`. Check the new feature with long text and narrow screens.

From this directory, run relevant checks:

```bash
npx tsc -p tsconfig.app.json --noEmit
npm run check:i18n
npm run check:ui-reuse
```

Exercise the affected UI and compare it with the existing reference. Add behavior tests when needed; report what was verified and any remaining limitations.

## Asynchronous view updates

Angular 22 uses OnPush by default. Prefer signals or AsyncPipe for data displayed
in templates. Components that retain plain fields must inject ChangeDetectorRef
and call `markForCheck()` when HTTP subscriptions, FileReader callbacks or other
asynchronous callbacks update those fields, including error/loading states.
Follow the existing admin filament/QR pages and shared STL viewer. A click must
never be required to display a completed response. Regression tests should await
the scheduled view update without manually calling `detectChanges()` after the
response. Zone-based change detection alone does not mark an OnPush view dirty.

## Shared feature building blocks

See the [shared component guide](src/app/shared/components/README.md) for modal, color selection, file upload, legal consent, and form-control APIs. Reuse these in both public and admin features. Calculator global/per-file settings share `app-print-settings` within the calculator feature.

`npm run check:ui-reuse` also rejects known copies of color popups, modal shells, consent text, and print-setting styles. It catches established regression patterns; code review still needs to assess new component duplication.

## Adding a New Feature

1. **Create Directory**: `src/app/features/my-feature`.
2. **Create Routes**: Create `my-feature.routes.ts` exporting a `Routes` array.
3. **Register Route**: Add to `src/app/app.routes.ts` using lazy loading:
   ```typescript
   {
     path: 'my-feature',
     loadChildren: () => import('./features/my-feature/my-feature.routes').then(m => m.MY_FEATURE_ROUTES)
   }
   ```

## Public routing and missing pages

Public URLs use `/it`, `/en`, `/de`, or `/fr`. Express normalizes regional prefixes
such as `/de-CH/about` to `/de/about` with HTTP 308, preserving query parameters;
`LanguageService` applies the same language normalization during client navigation.
Unprefixed pages and unsupported languages use Italian. Keep calculator and legacy
product aliases in the same redirect so normalization reaches the final path directly.

Unknown routes render the localized `NotFoundComponent` inside the shared site layout,
preserve the requested path, and set request-local `RESPONSE_INIT.status` to 404.
The page reuses the home hero typography, semantic color tokens and `ui-button`
links. Its simple decorative SVG represents layered 404 digits and a missing
piece under a printhead; keep it hidden from assistive technology. Home, calculator
and contact links must retain the active language, including after switching locales.
Their route data uses `seoNotFound: true` and `seoRobots: 'noindex, follow'` so
`SeoService` removes canonical and hreflang links. Do not redirect missing pages to
the homepage or publish invented language alternates for them. The Express bridge
propagates the status and disables caching, as it does for shop errors.
Verify production HTTP responses with `npm run build && npm run check:seo-ssr`.

## Product structured data

`SeoService` must wait for the first completed router navigation before generating
route metadata. Before that event, `router.url` is `/` even on a deep link;
rewriting metadata then would temporarily replace the SSR canonical with `/it`
during hydration. Preserve the SSR head until the actual route is available.

The Express `CommonEngine` bridge must explicitly provide the request-local
`REQUEST` token. The origin interceptor uses its path and headers to route shop
catalogue reads through `SSR_INTERNAL_API_ORIGIN`, including behind Basic Auth.
Angular SSR host validation remains enabled: deployments must set the comma-separated
`NG_ALLOWED_HOSTS` list. `docker-compose.deploy.yml` defaults it to the three known
public hosts, while the disposable E2E stack explicitly allows loopback hosts.
Verify this bridge with `npm run build && npm run check:shop-ssr`: it runs the real
production SSR bundle against a loopback catalogue fixture without
`SSR_ROUTE_ALL_API_INTERNALLY`, including concurrent locales and API failure status.

Shop HTTP responses are propagated from Angular's request-scoped `RESPONSE_INIT`
through `CommonEngine` to Express in `src/server.ts`: missing products/categories
return 404, temporary API failures return 503 with `Retry-After: 60`, and successful
loads return 200. Error responses are not cached. Do not serve loading placeholders
with HTTP 200 after a failed API request, or add `noindex` for temporary outages;
503 already tells crawlers to retry. Existing localized error messages are shown
instead of an indefinite loading state. Verify actual SSR HTTP responses, not only
the component's response metadata, when changing this behavior.

Product detail pages own a single JSON-LD script through
`ProductStructuredDataService`. It is rendered on the server and updated with
the displayed variant (unit price in CHF, SKU, material and color), localized
product content, canonical path and public gallery images. Loading, error,
non-indexable and incomplete products must not leave stale offers in the document;
the script is removed when leaving the page. Escape `<` when serializing JSON-LD
to prevent product content from closing the script tag in SSR HTML.

Active public variants are currently orderable without inventory limits, so their
availability is `InStock` (including made-to-order products). If inventory or
backorders are introduced, update this mapping alongside cart eligibility.
Do not invent GTINs, reviews, shipping costs or return policies. Verify rendered
HTML and variant changes when modifying this code; after deployment validate a
public product URL with Google's Rich Results Test.

## Calculator batch requests

The calculator sends `itemCount` when creating/reusing a quote session. The backend
reserves the whole batch before resetting the old quote and returns `calculationId`.
`QuoteEstimatorService` sends that value in `X-Quote-Calculation` for each file,
processing files sequentially with `concatMap`. Keep progress, partial failures,
`Retry-After` handling and cancellation of active/queued HTTP requests intact.
The server remains responsible for quota and concurrency enforcement. See the
[backend admission contract](../backend/README.md#calculator-admission-and-slicing-capacity)
for defaults, legacy compatibility and deployment order.

### Model uploads and previews

The calculator accepts STL, 3MF, OBJ and STEP/STP files up to the existing 200 MB upload
limit. STL previews stay local. On selection, other formats request an STL from
`POST /api/model-previews`; show the existing viewer loading/unavailable text
while waiting or on failure. Preview failures must not prevent quoting. Cancel
the pending preview when selection changes, and cache the result by the original
`File` identity and selected unit so files with identical names stay independent.

OBJ cards expose a compact shared `app-select` for file units (`mm`, `cm`, `m`,
`in`). Send `modelUnit` in both preview FormData and per-file quote settings.
Changing units invalidates that item's preview/review message, refreshes its
preview, and requires recalculation before checkout. Preserve the unit in drafts,
session restoration and recalculation signatures. Do not apply global print
settings or guessed unit conversions to this per-file value.

Keep the original file in requests and drafts. Generated preview files belong in
`previewFile`, never in `file`. Restored sessions use the stored converted STL.
Checkout reuses that stored STL through `getLineItemStlPreview` for all five
supported extensions. Do not restrict previews to original `.stl` filenames or
upload models again when opening the summary. Item headers pair the name with
the line total; the next row groups material, print time and weight. The expanded
summary uses two columns on desktop and one on mobile, with square model previews.
Both summary views place stacked quantity/color fields beside the thumbnail;
the expand icon sits beside its heading and opens the same complete summary.
Track print summary cards by their persistent item ID. Updating CAD quantity or
color returns a new session object; retaining cards avoids recreating WebGL
viewers and preserves the scroll position and 3D camera while applying colors.
See the [backend conversion contract](../backend/README.md#model-conversion-and-previews).

## Internationalization (i18n)

Translations are stored in `src/assets/i18n/`.

- `it.json` (Italian, the default and fallback locale)
- `en.json` (English)
- `de.json` (German)
- `fr.json` (French)

To add a language, create the JSON file, update the supported-language types and `LanguageService` in `src/app/core/services/language.service.ts`, and update the static translation loader. Run `npm run check:i18n` before committing.

## Admin content translation

Reuse `AdminTranslationService` and the existing localized-text backend for admin
content. `features/admin/components/admin-translate-action` supplies source language,
optional overwrite, loading/error feedback, and draft-only results for category
content/SEO and product image title/alt text. Identify each draft with its names or
translations map so switching records cancels pending results. Responses preserve
the source and edits made while translating; saving remains the owning form's job.
Populated optional fields participate even when required fields are already complete.

Filament colors use the page-header **Translate** action beside **Refresh** instead:
`POST /api/admin/filaments/translate-colors` always translates saved Italian labels,
overwrites EN/DE/FR, and persists filament and existing shop variant labels together.
Save Italian changes/new colors before running it. Keep internal color names/IDs and
hex values stable; public selectors, shop, cart and orders consume the existing locale
labels. Checkout print summaries use the session's localized labels, with filament
options by ID as a fallback.
Browser scenarios `UI-ADMIN-TRANSLATION-001/002` use simulated APIs and cover the
header action, overwrite results, failure recovery, category/image editors and mobile.

## Customer order tracking

The order page keeps `PAID` (awaiting production) separate from `IN_PRODUCTION`.
Cancelled orders replace the progress timeline with an explicit terminal status.
`OrderEventsService` opens one SSE subscription while the order page is visible.
`order-changed` invalidations reload the existing public order API; native
EventSource reconnection receives an initial invalidation to recover missed changes.
Healthy streams retain a 60-second recovery poll. During disconnection, polling
runs every second while checking TWINT (up to ten minutes), otherwise every ten
seconds initially and every minute after ten minutes or payment. Streams and timers
close on component destruction, hidden pages and completed/cancelled orders.
Returning to the page refreshes the order and reconnects. Backend streaming and
proxy conventions are in [the backend guide](../backend/README.md#live-order-notifications).
Request versions prevent pre-report GET responses from overwriting the mutation;
transient polling failures retain the last displayed order. Page polling never
opens or extends a backend mailbox acquisition window.

## Dev end-to-end checks

Playwright has a read-only `smoke` suite for deployed dev and a `fullstack`
suite for a disposable local stack. Its target guard accepts only
`https://dev.3d-fab.ch` or an HTTP loopback URL. Deployed dev runs select only
`e2e/smoke/`; they never submit forms or mutate server data. The route coverage
decisions and open gaps live in `e2e/coverage-manifest.ts`.
The dev hostname currently requires HTTP Basic Auth. Set `E2E_HTTP_USER` and
`E2E_HTTP_PASSWORD` locally; the Gitea job reads `DEV_BASIC_AUTH_USER` and
`DEV_BASIC_AUTH_PASSWORD` repository secrets.

Install Node 24.21.0, Docker with Compose, and Chromium once:

```bash
cd frontend
npm ci
npx playwright install chromium
```

From the repository root, `bash scripts/e2e/run.sh` builds the real SSR and
Spring images, starts a unique Compose project with PostgreSQL, ClamAV and
local SMTP capture, verifies the disposable database, seeds synthetic records,
runs the Chromium fullstack suite, saves service logs, then removes only that
run's containers and volumes. The initial image build downloads OrcaSlicer and
may take several minutes. Docker must be available to the current user. This
command chooses free loopback ports and prints their URLs in
`frontend/test-results/e2e-run.txt`. Pass `headed` to watch the full suite or
`ui-states` to run the simulated failure suite on an isolated stack.

For browser authoring against an already running isolated stack, set
`E2E_BASE_URL` to its loopback proxy and run `npm run e2e:local` for visible
Chromium, `npm run e2e:full` for headless execution, `npm run e2e:states`
for simulated failure states, or `npm run e2e:ui` for Playwright UI mode.
Mutation specs require `E2E_DISPOSABLE_STACK=1`, which the
harness sets only after checking its database identity. `npm run e2e:dev`
selects only the smoke suite; CI sets its target to the exact dev hostname.
Use `npm run e2e:typecheck`, `npm run check:e2e-routes`, and
`npm run e2e:report` for test compilation, route inventory, and the latest
HTML report. Failure traces, screenshots, video, JSON results and service logs
are ignored by Git under `frontend/test-results/` and `playwright-report/`.
Set `E2E_PROJECTS=firefox`, `webkit`, `mobile-chromium`, or `mobile-webkit`
for a manual alternate-browser run after installing that Playwright browser.
The deploy workflow runs the isolated Chromium suite before image build and
keeps the read-only dev smoke after deployment. On pull requests, the browser job waits for all preliminary checks, starts one
isolated stack, and runs the full Chromium suite followed by reduced Firefox,
WebKit and mobile selections. Set `E2E_BROWSER_MATRIX=true` and
`E2E_PROJECTS=chromium,firefox,webkit,mobile-chromium,mobile-webkit` to use the
same selection locally. The PR workflow can also be started manually.

See [the E2E guide](e2e/README.md) for fixture ownership, adding tests and
current coverage limitations. Browser tests complement backend and Angular
tests; real device TWINT handoff and hardware-specific 3D rendering still need
separate manual review.

## TWINT payment feedback

The order page uses the shared button loading state after a TWINT payment report.
It refreshes the order every second for ten minutes from the local report (or page
opening for an already reported order), then returns to the existing slower refresh.
Reads remain serial, pause in hidden tabs, resume on focus and stop on destruction.
The animation ends on confirmation or expiry; reduced motion keeps the indicator
static. This only reads order status: it does not increase backend mailbox polling.

## Service invoice administration

The existing `admin/cad-invoices` route is labelled **Invoices and services**.
Use the shared input/select/textarea/dialog components for editable free-text
service rows and metadata. Rows can be added or removed without a fixed count,
and support hourly or one-off fixed prices. The calculator session remains the
source for printed-product prices.

Group invoices by the exact customer/company and project labels. **New interim
invoice** carries only those labels into an empty form. It creates a separate
checkout; it is not an automatic deposit or remaining-balance calculation.
Customer/project labels stay internal; descriptions and the invoice name are
customer-facing. Once ordered, only administrative labels can be changed;
issued document snapshots retain their agreed name and service details.

`e2e/ui-states/service-invoices.spec.ts` exercises these interactions using an
explicitly simulated API and checks narrow-screen layout. Backend integration
and pricing tests remain responsible for persistence, amounts and paid access.

Keep the currently edited invoice ID separately from the form's optional
calculator source field. The source field stays editable: clearing it sends
`calculatorSessionId: null`; changing it sends the selected UUID. Always send
content edits to `PUT /cad-invoices/{invoiceId}` so neither action creates a
second checkout. Populate the field from the returned `calculatorSessionId`,
not the invoice ID, and retain the saved service rows. Only explicit
new/interim-invoice actions clear the editor's invoice identity.

Changing the source previews its print rows without replacing services or
attachments. Attachment operations remain bound to the invoice ID even when
its calculator source is empty or different. Re-entering a previous source ID
restores its files and prices without re-uploading them. Use one draft edit
action for services and print pricing; metadata-only editing remains available
for converted invoices. Keep action groups inside the table cell so shared
mobile labels and desktop alignment stay intact. `UI-INVOICE-009` covers
clear/save, reattachment, replacement, persistence in the editor and responsive
layout with an explicitly simulated API.

Draft deletion uses `app-dialog` and a required old-link decision. Cancel/Escape
make no request. `NEW_INVOICE` opens an empty replacement form with the reserved
ID; `REDIRECT` accepts a public site path and clears the deleted draft editor.
Keep errors in the dialog and block duplicate confirmation while saving. Issued
order snapshots remain protected. Checkout and saved calculator links follow a
deleted invoice's public redirect in the selected language with history replacement,
before mapping the response to a quote. Accept only internal paths, rejecting
protocol-relative URLs and backslashes. Browser scenarios UI-INVOICE-004/005 and
ADMIN-CAD-001/002 cover stable identity, confirmation, replacement and redirect.

### Oversized models and split consent

When slicing reports `MODEL_OUT_OF_PRINT_VOLUME`, the calculator offers explicit
consent and recalculation via `onAcceptSplitAndCalculate()`. Reuse the current
files, per-file settings and session, and send `allowSplitForOversized: true` only
after consent. The backend estimates using a virtual build volume and marks the
priced item `requiresSplitPrinting`, which triggers the configured split surcharge.
This estimates printing; it does not generate cut geometry. Failed estimates still
require manual review. Preserve the error code from both upload and session
responses so generic processing failures cannot hide the consent action.

For partial quotes, keep oversized files explicitly excluded from the total until
recalculation succeeds. Use the existing warning banner to explain the two steps:
select the split-consent checkbox, then calculate the quote again. Label these
files as requiring splitting; reserve manual-review copy for failed estimates.
Keep the checkbox label concise; the split-review card explains the surcharge
and manual-review fallback. Stack its full-width action buttons with a consistent gap.
