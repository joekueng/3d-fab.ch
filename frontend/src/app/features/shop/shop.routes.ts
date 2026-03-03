import { Routes } from '@angular/router';
import { ShopPageComponent } from './shop-page.component';
import { ProductDetailComponent } from './product-detail.component';

export const SHOP_ROUTES: Routes = [
  { path: '', component: ShopPageComponent },
  { path: ':id', component: ProductDetailComponent },
];
