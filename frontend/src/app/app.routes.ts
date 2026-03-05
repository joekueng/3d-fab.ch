import { Routes } from '@angular/router';

const appChildRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
    data: {
      seoTitle: '3D fab | Stampa 3D su misura',
      seoDescription:
        'Servizio di stampa 3D con preventivo online immediato per prototipi, piccole serie e pezzi personalizzati.',
    },
  },
  {
    path: 'calculator',
    loadChildren: () =>
      import('./features/calculator/calculator.routes').then(
        (m) => m.CALCULATOR_ROUTES,
      ),
    data: {
      seoTitle: 'Calcolatore preventivo stampa 3D | 3D fab',
      seoDescription:
        'Carica il file 3D e ottieni prezzo e tempi in pochi secondi con slicing reale.',
    },
  },
  {
    path: 'shop',
    loadChildren: () =>
      import('./features/shop/shop.routes').then((m) => m.SHOP_ROUTES),
    data: {
      seoTitle: 'Shop 3D fab',
      seoDescription:
        'Catalogo prodotti stampati in 3D e soluzioni tecniche pronte all uso.',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'about',
    loadChildren: () =>
      import('./features/about/about.routes').then((m) => m.ABOUT_ROUTES),
    data: {
      seoTitle: 'Chi siamo | 3D fab',
      seoDescription:
        'Scopri il team 3D fab e il laboratorio di stampa 3D con sedi in Ticino e Bienne.',
    },
  },
  {
    path: 'contact',
    loadChildren: () =>
      import('./features/contact/contact.routes').then((m) => m.CONTACT_ROUTES),
    data: {
      seoTitle: 'Contatti | 3D fab',
      seoDescription:
        'Contatta 3D fab per preventivi, supporto tecnico e richieste personalizzate di stampa 3D.',
    },
  },
  {
    path: 'checkout/cad',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then(
        (m) => m.CheckoutComponent,
      ),
    data: {
      seoTitle: 'Checkout | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'checkout',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then(
        (m) => m.CheckoutComponent,
      ),
    data: {
      seoTitle: 'Checkout | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'order/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
    data: {
      seoTitle: 'Ordine | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'co/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
    data: {
      seoTitle: 'Ordine | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
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
    data: {
      seoTitle: 'Admin | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
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
