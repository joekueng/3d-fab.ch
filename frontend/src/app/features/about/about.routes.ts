import { Routes } from '@angular/router';
import { AboutPageComponent } from './about-page.component';

export const ABOUT_ROUTES: Routes = [
  {
    path: '',
    component: AboutPageComponent,
    data: {
      seoTitle: 'Chi siamo | 3D fab',
      seoDescription:
        'Siamo un laboratorio di stampa 3D orientato a prototipi, ricambi e produzioni su misura.',
    },
  },
];
