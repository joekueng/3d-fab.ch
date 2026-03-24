import { Routes } from '@angular/router';

export const LEGAL_ROUTES: Routes = [
  {
    path: 'privacy',
    loadComponent: () =>
      import('./privacy/privacy.component').then((m) => m.PrivacyComponent),
    data: {
      seoTitleKey: 'SEO.ROUTES.LEGAL.PRIVACY.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.LEGAL.PRIVACY.DESCRIPTION',
    },
  },
  {
    path: 'terms',
    loadComponent: () =>
      import('./terms/terms.component').then((m) => m.TermsComponent),
    data: {
      seoTitleKey: 'SEO.ROUTES.LEGAL.TERMS.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.LEGAL.TERMS.DESCRIPTION',
    },
  },
];
