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
        'Catalogo prodotti stampati in 3D, accessori tecnici e soluzioni pratiche pronte all uso.',
    },
  },
  {
    path: 'p/:productSlug',
    component: ProductDetailComponent,
    data: {
      seoTitle: 'Prodotto | 3D fab',
    },
  },
  {
    path: ':categorySlug/:productSlug',
    component: ProductDetailComponent,
    data: {
      seoTitle: 'Prodotto | 3D fab',
    },
  },
  {
    path: ':categorySlug',
    component: ShopPageComponent,
    data: {
      seoTitle: 'Categoria Shop | 3D fab',
    },
  },
];
