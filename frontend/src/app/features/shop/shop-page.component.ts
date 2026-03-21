import { CommonModule } from '@angular/common';
import {
  RESPONSE_INIT,
  afterNextRender,
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
import {
  catchError,
  combineLatest,
  finalize,
  forkJoin,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { SeoService } from '../../core/services/seo.service';
import { LanguageService } from '../../core/services/language.service';
import {
  findColorHex,
  resolveLocalizedColorLabel,
} from '../../core/constants/colors.const';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import {
  ShopCategoryDetail,
  ShopCategoryNavNode,
  ShopCategoryTree,
  ShopCartItem,
  ShopProductSummary,
  ShopService,
} from './services/shop.service';
import { ShopRouteService } from './services/shop-route.service';

@Component({
  selector: 'app-shop-page',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    RouterLink,
    AppButtonComponent,
    AppCardComponent,
    ProductCardComponent,
  ],
  templateUrl: './shop-page.component.html',
  styleUrl: './shop-page.component.scss',
})
export class ShopPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly seoService = inject(SeoService);
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });
  readonly languageService = inject(LanguageService);
  private readonly shopRouteService = inject(ShopRouteService);
  readonly shopService = inject(ShopService);

  readonly categorySlug = input<string | undefined>();

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly categories = signal<ShopCategoryTree[]>([]);
  readonly categoryNodes = signal<ShopCategoryNavNode[]>([]);
  readonly selectedCategory = signal<ShopCategoryDetail | null>(null);
  readonly products = signal<ShopProductSummary[]>([]);

  readonly cartMutating = signal(false);
  readonly busyLineItemId = signal<string | null>(null);

  readonly cart = this.shopService.cart;
  readonly cartLoading = this.shopService.cartLoading;
  readonly cartItemCount = this.shopService.cartItemCount;
  readonly currentCategorySlug = computed(
    () => this.selectedCategory()?.slug ?? this.categorySlug() ?? null,
  );
  readonly cartItems = computed(() =>
    (this.cart()?.items ?? []).filter(
      (item) => item.lineItemType === 'SHOP_PRODUCT',
    ),
  );
  readonly cartHasItems = computed(() => this.cartItems().length > 0);

  constructor() {
    afterNextRender(() => {
      this.scheduleCartWarmup();
    });

    combineLatest([
      toObservable(this.categorySlug, { injector: this.injector }),
      toObservable(this.languageService.currentLang, {
        injector: this.injector,
      }),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
        }),
        switchMap(([categorySlug]) =>
          forkJoin({
            categories: this.shopService.getCategories(),
            catalog: this.shopService.getProductCatalog(categorySlug ?? null),
          }).pipe(
            catchError((error) => {
              this.categories.set([]);
              this.categoryNodes.set([]);
              this.selectedCategory.set(null);
              this.products.set([]);
              this.error.set(
                error?.status === 404 ? 'SHOP.NOT_FOUND' : 'SHOP.LOAD_ERROR',
              );
              if (error?.status === 404) {
                this.setResponseStatus(404);
              }
              this.applyErrorSeo();
              return of(null);
            }),
            finalize(() => this.loading.set(false)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (!result) {
          return;
        }

        this.categories.set(result.categories);
        this.categoryNodes.set(
          this.shopService.flattenCategoryTree(
            result.categories,
            result.catalog.category?.slug ?? this.categorySlug() ?? null,
          ),
        );
        this.selectedCategory.set(result.catalog.category ?? null);
        this.products.set(result.catalog.products);
        this.applySeo(result.catalog.category ?? null);
        this.restoreCatalogScrollIfNeeded();
      });
  }

  private scheduleCartWarmup(): void {
    if (typeof window === 'undefined') {
      this.loadCartIfNeeded();
      return;
    }

    const warmup = () => this.loadCartIfNeeded();
    const idleCallback = (
      window as Window & {
        requestIdleCallback?: (
          callback: IdleRequestCallback,
          options?: IdleRequestOptions,
        ) => number;
      }
    ).requestIdleCallback;

    if (typeof idleCallback === 'function') {
      idleCallback(() => warmup(), { timeout: 1500 });
      return;
    }

    window.setTimeout(warmup, 300);
  }

  private loadCartIfNeeded(): void {
    if (this.shopService.cartLoaded() || this.shopService.cartLoading()) {
      return;
    }

    this.shopService
      .loadCart()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          this.shopService.cart.set(null);
        },
      });
  }

  productCartQuantity(productId: string): number {
    return this.shopService.quantityForProduct(productId);
  }

  cartItemName(item: ShopCartItem): string {
    return (
      item.displayName || item.shopProductName || item.originalFilename || '-'
    );
  }

  cartItemVariant(item: ShopCartItem): string | null {
    return item.shopVariantLabel || this.cartItemColor(item);
  }

  cartItemColor(item: ShopCartItem): string | null {
    return (
      resolveLocalizedColorLabel(this.languageService.selectedLang(), {
        fallback: item.shopVariantColorName ?? item.colorCode,
        it: item.shopVariantColorLabelIt,
        en: item.shopVariantColorLabelEn,
        de: item.shopVariantColorLabelDe,
        fr: item.shopVariantColorLabelFr,
      }) ??
      item.shopVariantColorName ??
      item.colorCode
    );
  }

  cartItemColorHex(item: ShopCartItem): string {
    return (
      item.shopVariantColorHex ||
      findColorHex(item.shopVariantColorName) ||
      findColorHex(item.colorCode) ||
      '#c9ced6'
    );
  }

  navigateToCategory(slug?: string | null): void {
    this.router.navigate(this.shopRouteService.shopRootCommands(slug));
  }

  increaseQuantity(item: ShopCartItem): void {
    this.updateItemQuantity(item, (item.quantity ?? 0) + 1);
  }

  decreaseQuantity(item: ShopCartItem): void {
    const nextQuantity = Math.max(1, (item.quantity ?? 1) - 1);
    this.updateItemQuantity(item, nextQuantity);
  }

  removeItem(item: ShopCartItem): void {
    this.cartMutating.set(true);
    this.busyLineItemId.set(item.id);
    this.shopService
      .removeCartItem(item.id)
      .pipe(
        finalize(() => {
          this.cartMutating.set(false);
          this.busyLineItemId.set(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: () => {
          this.error.set('SHOP.CART_UPDATE_ERROR');
        },
      });
  }

  clearCart(): void {
    this.cartMutating.set(true);
    this.busyLineItemId.set(null);
    this.shopService
      .clearCart()
      .pipe(
        finalize(() => {
          this.cartMutating.set(false);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
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
    this.router.navigate(
      ['/', this.languageService.selectedLang(), 'checkout'],
      {
        queryParams: {
          session: sessionId,
        },
      },
    );
  }

  trackByCategory(_index: number, item: ShopCategoryNavNode): string {
    return item.id;
  }

  trackByProduct(_index: number, product: ShopProductSummary): string {
    return product.id;
  }

  trackByCartItem(_index: number, item: ShopCartItem): string {
    return item.id;
  }

  private updateItemQuantity(item: ShopCartItem, quantity: number): void {
    this.cartMutating.set(true);
    this.busyLineItemId.set(item.id);
    this.shopService
      .updateCartItem(item.id, quantity)
      .pipe(
        finalize(() => {
          this.cartMutating.set(false);
          this.busyLineItemId.set(null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        error: () => {
          this.error.set('SHOP.CART_UPDATE_ERROR');
        },
      });
  }

  private applySeo(category: ShopCategoryDetail | null): void {
    if (!category) {
      this.applyDefaultSeo();
      return;
    }

    const title =
      category.seoTitle ||
      `${category.name} | ${this.translate.instant('SHOP.TITLE')} | 3D fab`;
    const description =
      category.seoDescription ||
      category.description ||
      this.translate.instant('SHOP.CATALOG_META_DESCRIPTION');
    const robots =
      category.indexable === false ? 'noindex, nofollow' : 'index, follow';

    this.seoService.applyPageSeo({
      title,
      description,
      robots,
      ogTitle: category.ogTitle || title,
      ogDescription: category.ogDescription || description,
    });
  }

  private applyDefaultSeo(): void {
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

  private applyErrorSeo(): void {
    const title = `${this.translate.instant('SHOP.TITLE')} | 3D fab`;
    const description = this.translate.instant('SHOP.CATALOG_META_DESCRIPTION');

    this.seoService.applyResolvedSeo({
      title,
      description,
      robots: 'noindex, nofollow',
      ogTitle: title,
      ogDescription: description,
      canonicalPath: null,
      alternates: null,
      xDefault: null,
    });
  }

  private setResponseStatus(status: number): void {
    if (this.responseInit) {
      this.responseInit.status = status;
    }
  }

  private restoreCatalogScrollIfNeeded(): void {
    if (typeof window === 'undefined') {
      return;
    }

    const scrollY = Number(history.state?.shopRestoreScrollY);
    if (!Number.isFinite(scrollY) || scrollY < 0) {
      return;
    }

    const { shopRestoreScrollY: _ignored, ...nextState } = history.state ?? {};
    const restore = () => window.scrollTo({ left: 0, top: scrollY });

    history.replaceState(nextState, '');
    window.requestAnimationFrame(() => {
      restore();
      window.setTimeout(restore, 60);
    });
  }
}
