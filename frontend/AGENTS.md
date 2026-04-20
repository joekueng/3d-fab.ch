# AGENTS.md

## Purpose

This frontend already has a shared UI layer. Do not reintroduce parallel form controls, button styles, or admin-only fallback CSS.

When changing UI, prefer extending the existing shared components and `ui-*` primitives instead of creating new variants ad hoc.

## Source Of Truth

Use these first:

- Shared Angular controls in `src/app/shared/components/`
  - `app-input`
  - `app-select`
  - `app-textarea`
  - `app-checkbox`
  - `app-button`
  - `app-card`
- Global UI primitives in `src/styles/_ui.scss`
  - `ui-form-group`, `ui-form-label`, `ui-form-control`, `ui-form-error`
  - `ui-button` and its variants
  - `ui-checkbox`
  - `ui-banner`
  - `ui-subpanel`
  - `ui-data-table`, `ui-table-wrap`
  - `ui-language-toolbar`
  - `ui-file-picker`

For admin pages, the shared page shell is already established:

- `section-card`
- `section-header`
- shared `ui-*` primitives above

Component SCSS in admin pages should handle page-specific layout only, not redefine generic input/button/select/textarea styles.

## Reuse Rules

Apply this order:

1. If behavior and semantics match an existing shared component, reuse that component.
2. If behavior is simple but markup differs, reuse the global `ui-*` primitive classes.
3. Only create a new shared component if the same behavior/API will be reused in multiple places.
4. Only add local component SCSS for layout or truly page-specific visual treatment.

Examples:

- Standard text/number/search fields: use `app-input`.
- Standard selects: use `app-select`.
- Standard textareas: use `app-textarea`.
- Standard boolean toggles/checkboxes: use `app-checkbox`.
- Standard actions: use `app-button`.
- Tables, banners, subpanels, language switchers, file pickers: use existing `ui-*` classes.

## Explicitly Avoid

Do not do these unless the user explicitly asks for a redesign:

- Do not add `::ng-deep`.
- Do not add global fallback selectors that style raw `input`, `select`, `textarea`, or `button` inside admin pages.
- Do not create page-local clones of shared controls.
- Do not create a second admin-only design system on top of `_ui.scss`.
- Do not duplicate classes like `section-card`, `section-header`, generic `error`/`success`, or button variants inside page SCSS when a shared version already exists.

## Allowed Exceptions

Raw native elements are acceptable when they are inherently special-purpose and already intentionally styled locally or with `ui-*` primitives, for example:

- hidden file inputs paired with `ui-file-picker`
- rich-text toolbar buttons
- special navigation triggers such as media context buttons
- highly custom toggles like expand/collapse affordances
- `contenteditable` editors

If a raw control starts being reused as a normal form/action pattern, convert it to a shared component or existing `ui-*` primitive.

## Shared Component Guidance

Before creating a new shared component, check whether one of these should simply be extended:

- `app-input` already supports `label`, `name`, `compact`, `type`, `placeholder`, `required`, `autocomplete`, `autocapitalize`, `min`, `max`, `step`, `inputmode`, `spellcheck`, `readonly`, `disabled`.
- `app-select` already supports `label`, `name`, `compact`, `options`, `required`, `disabled`, and projected `<option>` / `<optgroup>` content.
- `app-textarea` already supports `label`, `name`, `compact`, `placeholder`, `rows`, `required`, `readonly`, `disabled`.
- `app-checkbox` already supports `label`, `name`, `disabled`, and `variant="default|pill"`.
- `app-button` already supports `variant="primary|secondary|outline|text|ghost|ghost-danger|danger"`, `size="md|sm"`, and `fullWidth`.

If a page needs a small missing capability, extend the existing shared component instead of creating `app-admin-input`, `app-inline-button`, or similar duplicates.

## Admin-Specific Notes

- `admin-filament-stock`, `admin-home-media`, `admin-dashboard`, `admin-contact-requests`, `admin-sessions`, `admin-cad-invoices`, and `admin-login` were already normalized toward shared controls.
- `admin-shop` is large and intentionally mixes shared controls with `ui-*` primitives. Preserve that structure unless you are doing a deliberate broader migration.
- `admin-shell` must remain a thin shell. Do not put a large styling override layer back into it.

## Verification Checklist

After UI changes, run:

```bash
npx tsc -p tsconfig.app.json --noEmit
```

When touching styles or admin pages, also check:

```bash
rg -n "::ng-deep" src --glob '*.scss'
rg -n "<(input|select|textarea|button)\\b" src/app/features/admin/pages --glob '*.html'
```

Interpretation:

- `::ng-deep` should stay absent.
- Raw controls in admin pages are acceptable only for the explicit exceptions above.
- If you add repeated raw controls for normal form usage, convert them to shared components before finishing.

## Editing Policy For Future Agents

If you are about to add duplicated UI code, stop and reuse or extend what exists.

If you think a new component is necessary, document why the existing shared component API is insufficient in your final response.
