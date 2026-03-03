import { Routes } from '@angular/router';

export const CONTACT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./contact-page.component').then((m) => m.ContactPageComponent),
  },
];
