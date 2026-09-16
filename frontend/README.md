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

## Internationalization (i18n)

Translations are stored in `src/assets/i18n/`.

- `it.json` (Italian, the default and fallback locale)
- `en.json` (English)
- `de.json` (German)
- `fr.json` (French)

To add a language, create the JSON file, update the supported-language types and `LanguageService` in `src/app/core/services/language.service.ts`, and update the static translation loader. Run `npm run check:i18n` before committing.
