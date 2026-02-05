import { Component, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ShopService, Product } from './services/shop.service';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, AppButtonComponent],
  template: `
    <div class="container wrapper">
      <a routerLink="/shop" class="back-link">← {{ 'SHOP.BACK' | translate }}</a>
      
      @if (product(); as p) {
        <div class="detail-grid">
          <div class="image-box"></div>
          
          <div class="info">
            <span class="category">{{ p.category }}</span>
            <h1>{{ p.name }}</h1>
            <p class="price">{{ p.price | currency:'EUR' }}</p>
            
            <p class="desc">{{ p.description }}</p>
            
            <div class="actions">
              <app-button variant="primary" (click)="addToCart()">
                {{ 'SHOP.ADD_CART' | translate }}
              </app-button>
            </div>
          </div>
        </div>
      } @else {
        <p>Prodotto non trovato.</p>
      }
    </div>
  `,
  styles: [`
    .wrapper { padding-top: var(--space-8); }
    .back-link { display: inline-block; margin-bottom: var(--space-6); color: var(--color-text-muted); }
    
    .detail-grid {
      display: grid;
      gap: var(--space-8);
      @media(min-width: 768px) {
        grid-template-columns: 1fr 1fr;
      }
    }
    
    .image-box {
      background-color: var(--color-neutral-200);
      border-radius: var(--radius-lg);
      aspect-ratio: 1;
    }

    .category { color: var(--color-brand); font-weight: 600; text-transform: uppercase; font-size: 0.875rem; }
    .price { font-size: 1.5rem; font-weight: 700; color: var(--color-text); margin: var(--space-4) 0; }
    .desc { color: var(--color-text-muted); line-height: 1.6; margin-bottom: var(--space-8); }
  `]
})
export class ProductDetailComponent {
  // Input binding from router
  id = input<string>();
  
  product = signal<Product | undefined>(undefined);

  constructor(private shopService: ShopService) {}

  ngOnInit() {
    const productId = this.id();
    if (productId) {
      this.shopService.getProductById(productId).subscribe(p => this.product.set(p));
    }
  }

  addToCart() {
    alert('Aggiunto al carrello (Mock)');
  }
}
