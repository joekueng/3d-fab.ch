import { Routes } from '@angular/router';
import { ShopPageComponent } from './shop-page.component';
import { ProductDetailComponent } from './product-detail.component';

export const SHOP_ROUTES: Routes = [
  {
    path: '',
    component: ShopPageComponent,
    data: {
      seoTitle: 'Shop 3D fab',
      seoDescription:
        'Lo shop 3D fab e in allestimento. Intanto puoi usare il calcolatore per ottenere un preventivo.',
      seoRobots: 'noindex, nofollow',
    },
  },
  {
    path: ':id',
    component: ProductDetailComponent,
    data: {
      seoTitle: 'Prodotto | 3D fab',
      seoRobots: 'noindex, nofollow',
    },
  },
];
