import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ShopService, Product } from './services/shop.service';
import { ProductCardComponent } from './components/product-card/product-card.component';

@Component({
  selector: 'app-shop-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ProductCardComponent],
  templateUrl: './shop-page.component.html',
  styleUrl: './shop-page.component.scss'
})
export class ShopPageComponent {
  products = signal<Product[]>([]);

  constructor(private shopService: ShopService) {
    this.shopService.getProducts().subscribe(data => {
      this.products.set(data);
    });
  }
}
