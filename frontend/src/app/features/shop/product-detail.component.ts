import { CommonModule } from '@angular/common';
import {
  Component,
  DestroyRef,
  Injector,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, combineLatest, finalize, of, switchMap, tap } from 'rxjs';
import { SeoService } from '../../core/services/seo.service';
import { LanguageService } from '../../core/services/language.service';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { StlViewerComponent } from '../../shared/components/stl-viewer/stl-viewer.component';
import {
  ShopProductDetail,
  ShopProductVariantOption,
  ShopService,
} from './services/shop.service';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    AppButtonComponent,
    AppCardComponent,
    StlViewerComponent,
  ],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly seoService = inject(SeoService);
  private readonly languageService = inject(LanguageService);
  readonly shopService = inject(ShopService);

  readonly categorySlug = input<string | undefined>();
  readonly productSlug = input<string | undefined>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly product = signal<ShopProductDetail | null>(null);
  readonly selectedVariantId = signal<string | null>(null);
  readonly selectedImageAssetId = signal<string | null>(null);
  readonly quantity = signal(1);
  readonly isAddingToCart = signal(false);
  readonly addSuccess = signal(false);

  readonly modelLoading = signal(false);
  readonly modelError = signal(false);
  readonly modelFile = signal<File | null>(null);

  readonly selectedVariant = computed(() => {
    const product = this.product();
    const variantId = this.selectedVariantId();
    if (!product) {
      return null;
    }
    return (
      product.variants.find((variant) => variant.id === variantId) ??
      product.defaultVariant ??
      product.variants[0] ??
      null
    );
  });

  readonly galleryImages = computed(() => {
    const product = this.product();
    if (!product) {
      return [];
    }

    const images = [...(product.images ?? [])];
    const primary = product.primaryImage;
    if (
      primary &&
      !images.some((image) => image.mediaAssetId === primary.mediaAssetId)
    ) {
      images.unshift(primary);
    }
    return images;
  });

  readonly selectedImage = computed(() => {
    const images = this.galleryImages();
    const selectedAssetId = this.selectedImageAssetId();
    return (
      images.find((image) => image.mediaAssetId === selectedAssetId) ??
      images[0] ??
      null
    );
  });

  readonly selectedVariantCartQuantity = computed(() =>
    this.shopService.quantityForVariant(this.selectedVariant()?.id),
  );

  constructor() {
    if (!this.shopService.cartLoaded()) {
      this.shopService
        .loadCart()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          error: () => {
            this.shopService.cart.set(null);
          },
        });
    }

    combineLatest([
      toObservable(this.productSlug, { injector: this.injector }),
      toObservable(this.languageService.currentLang, { injector: this.injector }),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
          this.addSuccess.set(false);
          this.modelError.set(false);
        }),
        switchMap(([productSlug]) => {
          if (!productSlug) {
            this.error.set('SHOP.NOT_FOUND');
            this.loading.set(false);
            return of(null);
          }

          return this.shopService.getProduct(productSlug).pipe(
            catchError((error) => {
              this.product.set(null);
              this.selectedVariantId.set(null);
              this.selectedImageAssetId.set(null);
              this.modelFile.set(null);
              this.error.set(
                error?.status === 404 ? 'SHOP.NOT_FOUND' : 'SHOP.LOAD_ERROR',
              );
              this.applyFallbackSeo();
              return of(null);
            }),
            finalize(() => this.loading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((product) => {
        if (!product) {
          return;
        }

        this.product.set(product);
        this.selectedVariantId.set(product.defaultVariant?.id ?? product.variants[0]?.id ?? null);
        this.selectedImageAssetId.set(product.primaryImage?.mediaAssetId ?? product.images[0]?.mediaAssetId ?? null);
        this.quantity.set(1);
        this.applySeo(product);

        if (product.model3d?.url && product.model3d.originalFilename) {
          this.loadModelPreview(product.model3d.url, product.model3d.originalFilename);
        } else {
          this.modelFile.set(null);
          this.modelLoading.set(false);
          this.modelError.set(false);
        }
      });
  }

  imageUrl(image: ShopProductDetail['images'][number] | null): string | null {
    if (!image) {
      return null;
    }
    return (
      this.shopService.resolveMediaUrl(image.hero) ??
      this.shopService.resolveMediaUrl(image.card) ??
      this.shopService.resolveMediaUrl(image.thumb)
    );
  }

  selectImage(mediaAssetId: string): void {
    this.selectedImageAssetId.set(mediaAssetId);
  }

  selectVariant(variant: ShopProductVariantOption): void {
    this.selectedVariantId.set(variant.id);
    this.addSuccess.set(false);
  }

  decreaseQuantity(): void {
    this.quantity.update((value) => Math.max(1, value - 1));
    this.addSuccess.set(false);
  }

  increaseQuantity(): void {
    this.quantity.update((value) => value + 1);
    this.addSuccess.set(false);
  }

  addToCart(): void {
    const variant = this.selectedVariant();
    if (!variant) {
      return;
    }

    this.isAddingToCart.set(true);
    this.shopService
      .addToCart(variant.id, this.quantity())
      .pipe(
        finalize(() => this.isAddingToCart.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.addSuccess.set(true);
        },
        error: () => {
          this.error.set('SHOP.CART_UPDATE_ERROR');
        },
      });
  }

  goToCheckout(): void {
    const sessionId = this.shopService.cartSessionId();
    if (!sessionId) {
      return;
    }
    this.router.navigate(['/checkout'], {
      queryParams: { session: sessionId },
    });
  }

  priceLabel(): number {
    return this.selectedVariant()?.priceChf ?? this.product()?.priceFromChf ?? 0;
  }

  colorLabel(variant: ShopProductVariantOption): string {
    return variant.colorName || variant.variantLabel || '-';
  }

  colorHex(variant: ShopProductVariantOption): string {
    return variant.colorHex || '#d5d8de';
  }

  productLinkRoot(): string[] {
    const categorySlug = this.product()?.category.slug || this.categorySlug();
    return categorySlug ? ['/shop', categorySlug] : ['/shop'];
  }

  private loadModelPreview(urlOrPath: string, filename: string): void {
    this.modelLoading.set(true);
    this.modelError.set(false);

    this.shopService
      .getProductModelFile(urlOrPath, filename)
      .pipe(
        finalize(() => this.modelLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => {
          this.modelFile.set(file);
        },
        error: () => {
          this.modelFile.set(null);
          this.modelError.set(true);
        },
      });
  }

  private applySeo(product: ShopProductDetail): void {
    const title = product.seoTitle || `${product.name} | 3D fab`;
    const description =
      product.seoDescription ||
      product.excerpt ||
      this.translate.instant('SHOP.CATALOG_META_DESCRIPTION');
    const robots = product.indexable === false ? 'noindex, nofollow' : 'index, follow';

    this.seoService.applyPageSeo({
      title,
      description,
      robots,
      ogTitle: product.ogTitle || title,
      ogDescription: product.ogDescription || description,
    });
  }

  private applyFallbackSeo(): void {
    const title = `${this.translate.instant('SHOP.TITLE')} | 3D fab`;
    const description = this.translate.instant('SHOP.CATALOG_META_DESCRIPTION');
    this.seoService.applyPageSeo({
      title,
      description,
      robots: 'index, follow',
      ogTitle: title,
      ogDescription: description,
    });
  }
}
