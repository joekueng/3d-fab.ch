import { Routes } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';

export const CALCULATOR_ROUTES: Routes = [
  { path: '', redirectTo: 'basic', pathMatch: 'full' },
  {
    path: 'animation-test',
    loadComponent: () =>
      import('./calculator-animation-test.component').then(
        (m) => m.CalculatorAnimationTestComponent,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.DESCRIPTION',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'basic',
    component: CalculatorPageComponent,
    data: {
      mode: 'easy',
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.BASIC.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.BASIC.DESCRIPTION',
    },
  },
  {
    path: 'advanced',
    component: CalculatorPageComponent,
    data: {
      mode: 'advanced',
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.ADVANCED.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.ADVANCED.DESCRIPTION',
    },
  },
];
