import { Routes } from '@angular/router';

export const LEGAL_ROUTES: Routes = [
  {
    path: 'privacy',
    loadComponent: () =>
      import('./privacy/privacy.component').then((m) => m.PrivacyComponent),
    data: {
      seoTitle: 'Privacy Policy | 3D fab',
      seoDescription:
        'Informativa privacy di 3D fab: trattamento dati, finalita e contatti.',
    },
  },
  {
    path: 'terms',
    loadComponent: () =>
      import('./terms/terms.component').then((m) => m.TermsComponent),
    data: {
      seoTitle: 'Termini e condizioni | 3D fab',
      seoDescription:
        'Termini e condizioni del servizio di stampa 3D e del calcolatore preventivi.',
    },
  },
];
