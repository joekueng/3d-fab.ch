import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ShopService, Product } from './services/shop.service';
import { ProductCardComponent } from './components/product-card/product-card.component';

@Component({
  selector: 'app-shop-page',
  standalone: true,
  imports: [CommonModule, TranslateModule, ProductCardComponent],
  template: `
    <div class="container hero">
      <h1>{{ 'SHOP.TITLE' | translate }}</h1>
      <p class="subtitle">Componenti e materiali selezionati per la tua stampa 3D.</p>
    </div>

    <div class="container">
      <div class="grid">
        @for (product of products(); track product.id) {
          <app-product-card [product]="product"></app-product-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .hero { padding: var(--space-8) 0; text-align: center; }
    .subtitle { color: var(--color-text-muted); margin-bottom: var(--space-8); }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: var(--space-6);
    }
  `]
})
export class ShopPageComponent {
  products = signal<Product[]>([]);

  constructor(private shopService: ShopService) {
    this.shopService.getProducts().subscribe(data => {
      this.products.set(data);
    });
  }
}