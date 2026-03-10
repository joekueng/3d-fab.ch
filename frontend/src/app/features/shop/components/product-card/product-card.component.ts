import { CommonModule } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ShopProductSummary, ShopService } from '../../services/shop.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
})
export class ProductCardComponent {
  private readonly shopService = inject(ShopService);

  readonly product = input.required<ShopProductSummary>();
  readonly cartQuantity = input(0);

  readonly productLink = computed(() => [
    '/shop',
    this.product().category.slug,
    this.product().slug,
  ]);

  readonly imageUrl = computed(() => {
    const image = this.product().primaryImage;
    return (
      this.shopService.resolveMediaUrl(image?.card) ??
      this.shopService.resolveMediaUrl(image?.hero) ??
      this.shopService.resolveMediaUrl(image?.thumb)
    );
  });

  priceLabel(): number {
    return this.product().priceFromChf;
  }

  hasPriceRange(): boolean {
    return this.product().priceFromChf !== this.product().priceToChf;
  }
}
