import { Routes } from '@angular/router';
import { CalculatorPageComponent } from './calculator-page.component';

export const CALCULATOR_ROUTES: Routes = [
  { path: '', redirectTo: 'basic', pathMatch: 'full' },
  {
    path: 'basic',
    component: CalculatorPageComponent,
    data: {
      mode: 'easy',
      seoTitle: 'Calcolatore stampa 3D base | 3D fab',
      seoDescription:
        'Calcola rapidamente il prezzo della tua stampa 3D in modalita base.',
    },
  },
  {
    path: 'advanced',
    component: CalculatorPageComponent,
    data: {
      mode: 'advanced',
      seoTitle: 'Calcolatore stampa 3D avanzato | 3D fab',
      seoDescription:
        'Configura parametri avanzati e ottieni un preventivo preciso con slicing reale.',
    },
  },
];
