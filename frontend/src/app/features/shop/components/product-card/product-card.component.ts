import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Product } from '../../services/shop.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="product-card">
      <div class="image-placeholder"></div>
      <div class="content">
        <span class="category">{{ product().category }}</span>
        <h3 class="name">
            <a [routerLink]="['/shop', product().id]">{{ product().name }}</a>
        </h3>
        <div class="footer">
          <span class="price">{{ product().price | currency:'EUR' }}</span>
          <a [routerLink]="['/shop', product().id]" class="view-btn">Dettagli</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .product-card {
      background: var(--color-bg-card);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      overflow: hidden;
      transition: box-shadow 0.2s;
      &:hover { box-shadow: var(--shadow-md); }
    }
    .image-placeholder {
      height: 200px;
      background-color: var(--color-neutral-200);
    }
    .content { padding: var(--space-4); }
    .category { font-size: 0.75rem; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
    .name { font-size: 1.125rem; margin: var(--space-2) 0; a { color: var(--color-text); text-decoration: none; &:hover { color: var(--color-brand); } } }
    .footer { display: flex; justify-content: space-between; align-items: center; margin-top: var(--space-4); }
    .price { font-weight: 700; color: var(--color-brand); }
    .view-btn { font-size: 0.875rem; font-weight: 500; }
  `]
})
export class ProductCardComponent {
  product = input.required<Product>();
}
