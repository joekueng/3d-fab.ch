import { Routes } from '@angular/router';

const appChildRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'calculator',
    loadChildren: () =>
      import('./features/calculator/calculator.routes').then(
        (m) => m.CALCULATOR_ROUTES,
      ),
  },
  {
    path: 'shop',
    loadChildren: () =>
      import('./features/shop/shop.routes').then((m) => m.SHOP_ROUTES),
  },
  {
    path: 'about',
    loadChildren: () =>
      import('./features/about/about.routes').then((m) => m.ABOUT_ROUTES),
  },
  {
    path: 'contact',
    loadChildren: () =>
      import('./features/contact/contact.routes').then((m) => m.CONTACT_ROUTES),
  },
  {
    path: 'checkout/cad',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then(
        (m) => m.CheckoutComponent,
      ),
  },
  {
    path: 'checkout',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then(
        (m) => m.CheckoutComponent,
      ),
  },
  {
    path: 'order/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
  },
  {
    path: 'co/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
  },
  {
    path: '',
    loadChildren: () =>
      import('./features/legal/legal.routes').then((m) => m.LEGAL_ROUTES),
  },
  {
    path: 'admin',
    loadChildren: () =>
      import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '**',
    redirectTo: '',
  },
];

export const routes: Routes = [
  {
    path: ':lang',
    loadComponent: () =>
      import('./core/layout/layout.component').then((m) => m.LayoutComponent),
    children: appChildRoutes,
  },
  {
    path: '',
    loadComponent: () =>
      import('./core/layout/layout.component').then((m) => m.LayoutComponent),
    children: appChildRoutes,
  },
  {
    path: '**',
    redirectTo: '',
  },
];
