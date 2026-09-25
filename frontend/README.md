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

## Product structured data

`SeoService` must wait for the first completed router navigation before generating
route metadata. Before that event, `router.url` is `/` even on a deep link;
rewriting metadata then would temporarily replace the SSR canonical with `/it`
during hydration. Preserve the SSR head until the actual route is available.

The Express `CommonEngine` bridge must explicitly provide the request-local
`REQUEST` token. The origin interceptor uses its path and headers to route shop
catalogue reads through `SSR_INTERNAL_API_ORIGIN`, including behind Basic Auth.
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

## Internationalization (i18n)

Translations are stored in `src/assets/i18n/`.

- `it.json` (Italian, the default and fallback locale)
- `en.json` (English)
- `de.json` (German)
- `fr.json` (French)

To add a language, create the JSON file, update the supported-language types and `LanguageService` in `src/app/core/services/language.service.ts`, and update the static translation loader. Run `npm run check:i18n` before committing.

## Customer order tracking

The order page keeps `PAID` (awaiting production) separate from `IN_PRODUCTION`.
Cancelled orders replace the progress timeline with an explicit terminal status.
Polling continues after a customer reports payment, runs only while visible, and
refreshes on focus/visibility return. It starts at ten seconds, slows to one minute
after ten minutes or after payment, and stops for completed/cancelled orders.
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

Install Node 22, Docker with Compose, and Chromium once:

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
