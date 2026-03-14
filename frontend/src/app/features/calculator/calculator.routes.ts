import { Routes } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';

export const CALCULATOR_ROUTES: Routes = [
  { path: '', redirectTo: 'basic', pathMatch: 'full' },
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
