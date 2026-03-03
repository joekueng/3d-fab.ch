import { Routes } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';

export const CALCULATOR_ROUTES: Routes = [
  { path: '', redirectTo: 'basic', pathMatch: 'full' },
  { path: 'basic', component: CalculatorPageComponent, data: { mode: 'easy' } },
  {
    path: 'advanced',
    component: CalculatorPageComponent,
    data: { mode: 'advanced' },
  },
];
