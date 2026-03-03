# Print Calculator Frontend

This is a modern Angular application designed with a Clean Architecture approach (Core, Shared, Features) and Design Tokens for easy theming.

## Project Structure

- **Core**: Singleton services, global layout components (Navbar, Footer), guards.
- **Shared**: Reusable dumb UI components (Buttons, Cards, Inputs). No business logic.
- **Features**: Lazy-loaded modules (Calculator, Shop, About). Each contains its own pages, components, and services.
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
- `it.json` (Italian - Default)
- `en.json` (English)

To add a language, create the JSON file and update `LanguageService` in `src/app/core/services/language.service.ts`.