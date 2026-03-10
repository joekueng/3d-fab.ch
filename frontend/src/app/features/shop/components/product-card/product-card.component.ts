import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { finalize } from 'rxjs';
import { ShopProductSummary, ShopService } from '../../services/shop.service';
import { ShopRouteService } from '../../services/shop-route.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
})
export class ProductCardComponent {
  private readonly router = inject(Router);
  private readonly shopService = inject(ShopService);
  private readonly shopRouteService = inject(ShopRouteService);

  readonly product = input.required<ShopProductSummary>();
  readonly cartQuantity = input(0);
  readonly addingToCart = signal(false);

  readonly productLink = computed(() =>
    this.shopRouteService.productCommands(this.product()),
  );

  readonly imageUrl = computed(() => {
    const image = this.product().primaryImage;
    return (
      this.shopService.resolveMediaUrl(image?.card) ??
      this.shopService.resolveMediaUrl(image?.hero) ??
      this.shopService.resolveMediaUrl(image?.thumb)
    );
  });

  readonly hasModelPreview = computed(() => {
    const model = this.product().model3d;
    return !!(model?.url && model.originalFilename);
  });

  priceLabel(): number {
    return this.product().priceFromChf;
  }

  hasPriceRange(): boolean {
    return this.product().priceFromChf !== this.product().priceToChf;
  }

  defaultVariantId(): string | null {
    return this.product().defaultVariant?.id ?? null;
  }

  addToCart(): void {
    const variantId = this.defaultVariantId();
    if (!variantId || this.addingToCart()) {
      return;
    }

    this.addingToCart.set(true);
    this.shopService
      .addToCart(variantId, 1)
      .pipe(finalize(() => this.addingToCart.set(false)))
      .subscribe({
        error: () => {
          // Keep card UX simple: product detail handles error messaging in depth.
        },
      });
  }

  navigationState(): { shopReturnUrl: string } {
    return {
      shopReturnUrl: this.router.url,
    };
  }
}
