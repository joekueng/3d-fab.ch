import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./core/layout/layout.component').then(m => m.LayoutComponent),
    children: [
      { 
        path: '', 
        loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent)
      },
      { 
        path: 'calculator', 
        loadChildren: () => import('./features/calculator/calculator.routes').then(m => m.CALCULATOR_ROUTES) 
      },
      { 
        path: 'shop', 
        loadChildren: () => import('./features/shop/shop.routes').then(m => m.SHOP_ROUTES) 
      },
      { 
        path: 'about', 
        loadChildren: () => import('./features/about/about.routes').then(m => m.ABOUT_ROUTES) 
      },
      { 
        path: 'contact', 
        loadChildren: () => import('./features/contact/contact.routes').then(m => m.CONTACT_ROUTES) 
      },
      {
        path: '',
        loadChildren: () => import('./features/legal/legal.routes').then(m => m.LEGAL_ROUTES)
      }
    ]
  }
];
