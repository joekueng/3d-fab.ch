import { Routes } from '@angular/router';

export const CONTACT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./contact-page.component').then((m) => m.ContactPageComponent),
    data: {
      seoTitle: 'Contatti | 3D fab',
      seoDescription:
        'Richiedi informazioni, preventivi personalizzati o supporto per progetti di stampa 3D.',
    },
  },
];
