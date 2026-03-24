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
      seoTitleKey: 'SEO.ROUTES.HOME.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.HOME.DESCRIPTION',
    },
  },
  {
    path: 'calculator',
    loadChildren: () =>
      import('./features/calculator/calculator.routes').then(
        (m) => m.CALCULATOR_ROUTES,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.DESCRIPTION',
    },
  },
  {
    path: 'shop',
    loadChildren: () =>
      import('./features/shop/shop.routes').then((m) => m.SHOP_ROUTES),
    data: {
      seoTitleKey: 'SEO.ROUTES.SHOP.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.SHOP.DESCRIPTION',
    },
  },
  {
    path: 'about',
    loadChildren: () =>
      import('./features/about/about.routes').then((m) => m.ABOUT_ROUTES),
    data: {
      seoTitleKey: 'SEO.ROUTES.ABOUT.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.ABOUT.DESCRIPTION',
    },
  },
  /* {
    path: 'materials',
    loadComponent: () =>
      import('./features/materials/materials-page.component').then(
        (m) => m.MaterialsPageComponent,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.MATERIALS.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.MATERIALS.DESCRIPTION',
    },
  },*/
  {
    path: 'contact',
    loadChildren: () =>
      import('./features/contact/contact.routes').then((m) => m.CONTACT_ROUTES),
    data: {
      seoTitleKey: 'SEO.ROUTES.CONTACT.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CONTACT.DESCRIPTION',
    },
  },
  {
    path: 'checkout/cad',
    loadComponent: () =>
      import('./features/checkout/checkout.component').then(
        (m) => m.CheckoutComponent,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.CHECKOUT.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CHECKOUT.DESCRIPTION',
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
      seoTitleKey: 'SEO.ROUTES.CHECKOUT.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CHECKOUT.DESCRIPTION',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'order/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
    data: {
      seoTitleKey: 'SEO.ROUTES.ORDER.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.ORDER.DESCRIPTION',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'co/:orderId',
    loadComponent: () =>
      import('./features/order/order.component').then((m) => m.OrderComponent),
    data: {
      seoTitleKey: 'SEO.ROUTES.ORDER.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.ORDER.DESCRIPTION',
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
      seoTitleKey: 'SEO.ROUTES.ADMIN.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.ADMIN.DESCRIPTION',
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
    path: ':lang/calculator/animation-test',
    canMatch: [langPrefixCanMatch],
    loadComponent: () =>
      import('./features/calculator/calculator-animation-test.component').then(
        (m) => m.CalculatorAnimationTestComponent,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.DESCRIPTION',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: 'calculator/animation-test',
    loadComponent: () =>
      import('./features/calculator/calculator-animation-test.component').then(
        (m) => m.CalculatorAnimationTestComponent,
      ),
    data: {
      seoTitleKey: 'SEO.ROUTES.CALCULATOR.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.CALCULATOR.DESCRIPTION',
      seoRobots: 'noindex, nofollow',
    },
  },
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
