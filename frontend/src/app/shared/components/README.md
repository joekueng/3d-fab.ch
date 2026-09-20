# Shared frontend components

Read [frontend/AGENTS.md](../../../../AGENTS.md) and [frontend/README.md](../../../../README.md) before implementing UI. Inspect the existing component API and reuse it; feature code owns business rules and adapts domain data to these controls.

| Need | Component and contract |
| --- | --- |
| Status and error banners | `app-alert`: select `info`, `warning`, `error`, or `success`. It owns the semantic status role and the shared `ui-banner` visual language; keep banners typographic and do not add decorative emoji or feature-local alert shells. |
| Form fields | `app-input`, `app-select`, `app-textarea`, `app-checkbox`; use Angular form bindings, semantic tokens, and existing validation. `app-checkbox` supports projected label content, including links. |
| Color selection | `app-color-selector`: calculator/checkout pass `variants` and consume `colorSelected`; shop maps its variants into `groups` and consumes `variantSelected`. Preserve opaque product IDs and numeric filament IDs. `showLabel` and `subtitle` enable a descriptive trigger without a separate popup implementation. |
| Modal | `app-dialog`: create conditionally with `@if`/`*ngIf`, provide `title` and `closeLabel`, and handle `dismissed` by clearing the owning feature's open state. Project the body; use `subtitle`, `size`, and `fullscreenOnMobile` when needed. The native modal owns focus containment, background isolation, Escape, backdrop dismissal, and closing. Opening runs only in the browser. Keep destructive actions explicit in projected content. |
| Legal consent | `app-legal-consent [control]="..."`: pass the existing `FormControl` with `Validators.requiredTrue`. It owns localized terms/privacy links and the touched/invalid error; the parent retains submission and validation policy. |
| File selection/drop | `app-dropzone`: set `accept`, `multiple`, `disabled`, `label`, and `subtext`; handle `filesDropped` in the feature. Use `showFileNames=false` when the feature already owns the preview list. File validation, attachment persistence, preview URLs, and removal belong to the feature. The native `accept` attribute is only a picker hint. |
| Quantity and color for print items | `app-print-item-controls`, already shared by calculator and checkout. |

Calculator-only settings live in [`features/calculator/components/print-settings`](../../features/calculator/components/print-settings/), not in generic shared UI. Both global and selected-file settings pass the existing `FormGroup` and current option lists into that panel. Preserve the per-file nozzle/layer constraints in the owning form.

Extend a component for reusable behavior rather than copying its markup/styles into a feature. Keep HTML, SCSS, and TypeScript separate. Run `npm run check:ui-reuse`, `npm run check:i18n`, compilation, and relevant browser tests from `frontend/`; compare responsive rendering and long translated content. The reuse check blocks known clones, but is not a general duplication detector.
