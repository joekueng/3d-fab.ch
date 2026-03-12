import { CanMatchFn, Routes } from '@angular/router';

const SUPPORTED_LANGS = new Set(['it', 'en', 'de', 'fr']);

const langPrefixCanMatch: CanMatchFn = (_route, segments) => {
  if (segments.length === 0) {
    return false;
  }
  return SUPPORTED_LANGS.has(segments[0].path.toLowerCase());
};

const appChildRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
    data: {
      seoTitleByLang: {
        it: 'Stampa 3D su misura in Ticino | Prototipi, ricambi e piccole serie - 3D Fab',
        en: 'Custom 3D Printing in Switzerland | Prototypes, Spare Parts & Short Runs - 3D Fab',
        de: '3D-Druck in Zürich | Prototypen, Ersatzteile und Kleinserien - 3D Fab',
        fr: 'Impression 3D à Bienne | Prototypes, pièces et petites séries - 3D Fab',
      },
      seoDescriptionByLang: {
        it: 'Servizio di stampa 3D in Ticino per prototipi, pezzi di ricambio e piccole serie. Shop tecnico e supporto CAD, con preventivo rapido da file STL.',
        en: 'Swiss-based 3D printing service for prototypes, spare parts and short production runs. Technical shop and CAD support, with fast quotes from STL files.',
        de: '3D-Druckservice in Zürich für Prototypen, Ersatzteile und Kleinserien. Technischer Shop und CAD-Service, mit schneller Angebotsanfrage aus STL-Dateien.',
        fr: "Service d'impression 3D à Bienne pour prototypes, pièces de rechange et petites séries. Boutique technique et support CAD, avec devis rapide depuis un fichier STL.",
      },
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
 /* {
    path: 'materials',
    loadComponent: () =>
      import('./features/materials/materials-page.component').then(
        (m) => m.MaterialsPageComponent,
      ),
    data: {
      seoTitle: 'Qualita e Materiali | 3D fab',
      seoDescription:
        'Confronta materiali di stampa 3D con radar chart interattivo, proprieta tecniche e fonti citate.',
    },
  },*/
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
    canMatch: [langPrefixCanMatch],
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
