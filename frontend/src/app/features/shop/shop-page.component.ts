import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  PLATFORM_ID,
  RESPONSE_INIT,
  afterNextRender,
  Component,
  DestroyRef,
  Injector,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  finalize,
  forkJoin,
  map,
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
import { QuickRequestPanelComponent } from '../../shared/components/quick-request-panel/quick-request-panel.component';
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
import { humanizeShopSlug } from './shop-seo-fallback';

@Component({
  selector: 'app-shop-page',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AppButtonComponent,
    AppCardComponent,
    QuickRequestPanelComponent,
    ProductCardComponent,
  ],
  templateUrl: './shop-page.component.html',
  styleUrl: './shop-page.component.scss',
})
export class ShopPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly seoService = inject(SeoService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });
  readonly languageService = inject(LanguageService);
  private readonly shopRouteService = inject(ShopRouteService);
  readonly shopService = inject(ShopService);

  readonly routeCategorySlug = signal<string | null>(
    this.readRouteParam('categorySlug'),
  );

  readonly loading = signal(true);
  readonly softFallbackActive = signal(false);
  readonly softFallbackCategoryLabel = signal<string | null>(null);
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
    () => this.selectedCategory()?.slug ?? this.routeCategorySlug() ?? null,
  );
  readonly cartItems = computed(() =>
    (this.cart()?.items ?? []).filter(
      (item) => item.lineItemType === 'SHOP_PRODUCT',
    ),
  );
  readonly cartHasItems = computed(() => this.cartItems().length > 0);
  readonly heroSubtitle = computed(() => {
    this.languageService.currentLang();

    const category = this.selectedCategory();
    if (category) {
      return (
        category.description ||
        this.translate.instant('SHOP.CATEGORY_META', {
          count: category.productCount || 0,
        })
      );
    }

    if (this.softFallbackActive() && this.routeCategorySlug()) {
      return this.resolveTranslatedText(
        'SEO.ROUTES.SHOP.CATEGORY_DESCRIPTION',
        this.translate.instant('SHOP.CATALOG_META_DESCRIPTION'),
      );
    }

    return this.translate.instant('SHOP.SUBTITLE');
  });
  readonly catalogEyebrow = computed(() => {
    this.languageService.currentLang();

    return this.selectedCategory() || this.softFallbackCategoryLabel()
      ? this.translate.instant('SHOP.SELECTED_CATEGORY')
      : this.translate.instant('SHOP.CATALOG_LABEL');
  });
  readonly catalogTitle = computed(() => {
    this.languageService.currentLang();

    return (
      this.selectedCategory()?.name ||
      this.softFallbackCategoryLabel() ||
      this.translate.instant('SHOP.CATALOG_TITLE')
    );
  });

  constructor() {
    afterNextRender(() => {
      this.scheduleCartWarmup();
    });

    combineLatest([
      this.route.paramMap.pipe(
        map((params) => this.normalizeRouteParam(params.get('categorySlug'))),
        distinctUntilChanged(),
      ),
      toObservable(this.languageService.currentLang, {
        injector: this.injector,
      }).pipe(distinctUntilChanged()),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.softFallbackActive.set(false);
          this.softFallbackCategoryLabel.set(null);
          this.error.set(null);
        }),
        switchMap(([categorySlug]) => {
          this.routeCategorySlug.set(categorySlug);
          return forkJoin({
            categories: this.shopService.getCategories(),
            catalog: this.shopService.getProductCatalog(categorySlug ?? null),
          }).pipe(
            catchError((error) => {
              const isNotFound = error?.status === 404;
              this.categories.set([]);
              this.categoryNodes.set([]);
              this.selectedCategory.set(null);
              this.products.set([]);
              if (isNotFound) {
                this.error.set('SHOP.NOT_FOUND');
                this.setResponseStatus(404);
                this.applyHardErrorSeo();
                return of(null);
              }

              if (this.shouldUseSoftSeoFallback(error)) {
                this.error.set(null);
                this.softFallbackActive.set(true);
                this.softFallbackCategoryLabel.set(
                  categorySlug ? humanizeShopSlug(categorySlug) : null,
                );
                this.setResponseStatus(200);
                this.applySoftFallbackSeo(categorySlug);
                return of(null);
              }

              this.error.set('SHOP.LOAD_ERROR');
              this.setResponseStatus(503);
              return of(null);
            }),
            finalize(() => this.loading.set(false)),
          );
        }),
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
            result.catalog.category?.slug ?? this.routeCategorySlug() ?? null,
          ),
        );
        this.selectedCategory.set(result.catalog.category ?? null);
        this.products.set(result.catalog.products);
        this.softFallbackActive.set(false);
        this.softFallbackCategoryLabel.set(null);
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

  private applyHardErrorSeo(): void {
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

  private applySoftFallbackSeo(categorySlug: string | null): void {
    if (!categorySlug) {
      this.applyDefaultSeo();
      return;
    }

    const title = this.buildSoftFallbackCategoryTitle(categorySlug);
    const description = this.resolveTranslatedText(
      'SEO.ROUTES.SHOP.CATEGORY_DESCRIPTION',
      this.translate.instant('SHOP.CATALOG_META_DESCRIPTION'),
    );

    this.seoService.applyResolvedSeo({
      title,
      description,
      robots: 'index, follow',
      ogTitle: title,
      ogDescription: description,
      canonicalPath: this.currentPath(),
      alternates: null,
      xDefault: null,
    });
  }

  private shouldUseSoftSeoFallback(error: { status?: number } | null): boolean {
    return !this.isBrowser && error?.status !== 404;
  }

  private buildSoftFallbackCategoryTitle(categorySlug: string): string {
    const shopTitle = this.translate.instant('SHOP.TITLE');
    const humanized = humanizeShopSlug(categorySlug);
    if (humanized) {
      return `${humanized} | ${shopTitle} | 3D fab`;
    }

    return this.resolveTranslatedText(
      'SEO.ROUTES.SHOP.CATEGORY_TITLE',
      `${shopTitle} | 3D fab`,
    );
  }

  private resolveTranslatedText(key: string, fallback: string): string {
    const translated = this.translate.instant(key);
    return typeof translated === 'string' && translated !== key
      ? translated
      : fallback;
  }

  private currentPath(): string {
    const path = String(this.router.url ?? '/').split(/[?#]/, 1)[0] || '/';
    return path.startsWith('/') ? path : `/${path}`;
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

  private readRouteParam(name: string): string | null {
    return this.normalizeRouteParam(this.route.snapshot.paramMap.get(name));
  }

  private normalizeRouteParam(value: string | null | undefined): string | null {
    const normalized = String(value ?? '').trim();
    return normalized || null;
  }
}
