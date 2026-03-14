import { Routes } from '@angular/router';
import { AboutPageComponent } from './about-page.component';

export const ABOUT_ROUTES: Routes = [
  {
    path: '',
    component: AboutPageComponent,
    data: {
      seoTitleKey: 'SEO.ROUTES.ABOUT.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.ABOUT.DESCRIPTION',
    },
  },
];
