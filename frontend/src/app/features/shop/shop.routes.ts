import { Routes } from '@angular/router';
import { ShopPageComponent } from './shop-page.component';
import { ProductDetailComponent } from './product-detail.component';

export const SHOP_ROUTES: Routes = [
  {
    path: '',
    component: ShopPageComponent,
    data: {
      seoTitleKey: 'SEO.ROUTES.SHOP.TITLE',
      seoDescriptionKey: 'SEO.ROUTES.SHOP.DESCRIPTION',
    },
  },
  {
    path: 'p/:productSlug',
    component: ProductDetailComponent,
    data: {
      seoTitleKey: 'SEO.ROUTES.SHOP.PRODUCT_TITLE',
      seoDescriptionKey: 'SEO.ROUTES.SHOP.PRODUCT_DESCRIPTION',
    },
  },
  {
    path: ':categorySlug/:productSlug',
    component: ProductDetailComponent,
    data: {
      seoTitleKey: 'SEO.ROUTES.SHOP.PRODUCT_TITLE',
      seoDescriptionKey: 'SEO.ROUTES.SHOP.PRODUCT_DESCRIPTION',
    },
  },
  {
    path: ':categorySlug',
    component: ShopPageComponent,
    data: {
      seoTitleKey: 'SEO.ROUTES.SHOP.CATEGORY_TITLE',
      seoDescriptionKey: 'SEO.ROUTES.SHOP.CATEGORY_DESCRIPTION',
    },
  },
];
