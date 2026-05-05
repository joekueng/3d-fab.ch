import { CommonModule, Location, isPlatformBrowser } from '@angular/common';
import {
  RESPONSE_INIT,
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  PLATFORM_ID,
  QueryList,
  ViewChildren,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  catchError,
  combineLatest,
  distinctUntilChanged,
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { SeoService } from '../../core/services/seo.service';
import { LanguageService } from '../../core/services/language.service';
import { findColorHex } from '../../core/constants/colors.const';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { QuickRequestPanelComponent } from '../../shared/components/quick-request-panel/quick-request-panel.component';
import { StlViewerComponent } from '../../shared/components/stl-viewer/stl-viewer.component';
import { SwipeCarouselDirective } from '../../shared/directives/swipe-carousel.directive';
import {
  ShopProductDetail,
  ShopProductVariantOption,
  ShopService,
} from './services/shop.service';
import { ShopRouteService } from './services/shop-route.service';
import { humanizeShopSlug } from './shop-seo-fallback';

interface ShopMaterialOption {
  key: string;
  label: string;
  variants: ShopProductVariantOption[];
  priceFromChf: number;
}

interface ShopMaterialProperty {
  labelKey: string;
  valueKey: string;
  tone: 'neutral' | 'strong' | 'soft';
}

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    AppButtonComponent,
    AppCardComponent,
    QuickRequestPanelComponent,
    StlViewerComponent,
    SwipeCarouselDirective,
  ],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent {
  private static readonly HEX_COLOR_PATTERN =
    /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly location = inject(Location);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly seoService = inject(SeoService);
  private readonly languageService = inject(LanguageService);
  private readonly shopRouteService = inject(ShopRouteService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly responseInit = inject(RESPONSE_INIT, { optional: true });
  @ViewChildren('thumbButton')
  private thumbButtons?: QueryList<ElementRef<HTMLButtonElement>>;
  readonly shopService = inject(ShopService);
  private thumbScrollFrame: number | null = null;

  readonly routeCategorySlug = signal<string | null>(
    this.readRouteParam('categorySlug'),
  );

  readonly loading = signal(true);
  readonly softFallbackActive = signal(false);
  readonly error = signal<string | null>(null);
  readonly product = signal<ShopProductDetail | null>(null);
  readonly selectedVariantId = signal<string | null>(null);
  readonly selectedImageAssetId = signal<string | null>(null);
  readonly quantity = signal(1);
  readonly isAddingToCart = signal(false);
  readonly addSuccess = signal(false);
  readonly selectedMaterialKey = signal<string | null>(null);
  readonly colorPopupOpen = signal(false);
  readonly modelModalOpen = signal(false);

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

  readonly materialOptions = computed<ShopMaterialOption[]>(() => {
    const product = this.product();
    if (!product) {
      return [];
    }

    const groups = new Map<string, ShopMaterialOption>();
    for (const variant of product.variants) {
      const label = this.materialLabelForVariant(variant);
      const key = label.toLowerCase();
      const group = groups.get(key);
      if (group) {
        group.variants.push(variant);
        group.priceFromChf = Math.min(group.priceFromChf, variant.priceChf);
        continue;
      }

      groups.set(key, {
        key,
        label,
        variants: [variant],
        priceFromChf: variant.priceChf,
      });
    }

    return Array.from(groups.values());
  });

  readonly selectedMaterial = computed<ShopMaterialOption | null>(() => {
    const selectedKey = this.selectedMaterialKey();
    const materials = this.materialOptions();
    if (!materials.length) {
      return null;
    }
    return (
      materials.find((material) => material.key === selectedKey) ??
      materials.find((material) =>
        material.variants.some(
          (variant) => variant.id === this.selectedVariant()?.id,
        ),
      ) ??
      materials[0]
    );
  });

  readonly colorOptions = computed<ShopProductVariantOption[]>(
    () => this.selectedMaterial()?.variants ?? [],
  );

  readonly selectedMaterialProperties = computed<ShopMaterialProperty[]>(() =>
    this.materialPropertiesFor(this.selectedMaterial()?.label),
  );

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

  readonly selectedImageIndex = computed(() => {
    const images = this.galleryImages();
    const selectedAssetId = this.selectedImageAssetId();
    const index = images.findIndex(
      (image) => image.mediaAssetId === selectedAssetId,
    );
    return index >= 0 ? index : 0;
  });

  readonly selectedVariantCartQuantity = computed(() =>
    this.shopService.quantityForVariant(this.selectedVariant()?.id),
  );

  constructor() {
    afterNextRender(() => {
      this.scheduleCartWarmup();
    });
    this.destroyRef.onDestroy(() => {
      if (this.isBrowser && this.thumbScrollFrame !== null) {
        window.cancelAnimationFrame(this.thumbScrollFrame);
      }
      this.languageService.clearLocalizedRouteOverrides();
    });

    combineLatest([
      this.route.paramMap.pipe(
        map((params) => ({
          categorySlug: this.normalizeRouteParam(params.get('categorySlug')),
          productSlug: this.normalizeRouteParam(params.get('productSlug')),
        })),
        distinctUntilChanged(
          (previous, current) =>
            previous.categorySlug === current.categorySlug &&
            previous.productSlug === current.productSlug,
        ),
      ),
      toObservable(this.languageService.currentLang, {
        injector: this.injector,
      }).pipe(distinctUntilChanged()),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.softFallbackActive.set(false);
          this.error.set(null);
          this.addSuccess.set(false);
          this.modelError.set(false);
          this.colorPopupOpen.set(false);
          this.modelModalOpen.set(false);
        }),
        switchMap(([routeParams]) => {
          this.routeCategorySlug.set(routeParams.categorySlug);
          if (!routeParams.productSlug) {
            this.languageService.clearLocalizedRouteOverrides();
            this.error.set('SHOP.NOT_FOUND');
            this.setResponseStatus(404);
            this.applyHardFallbackSeo();
            this.loading.set(false);
            return of(null);
          }

          const productSlug = routeParams.productSlug as string;
          return this.shopService.getProductByPublicPath(productSlug).pipe(
            catchError((error) => {
              this.languageService.clearLocalizedRouteOverrides();
              this.product.set(null);
              this.selectedVariantId.set(null);
              this.setSelectedImageAssetId(null);
              this.modelFile.set(null);
              const isNotFound = error?.status === 404;
              if (isNotFound) {
                this.error.set('SHOP.NOT_FOUND');
                this.setResponseStatus(404);
                this.applyHardFallbackSeo();
                return of(null);
              }

              if (this.shouldUseSoftSeoFallback(error)) {
                this.error.set(null);
                this.softFallbackActive.set(true);
                this.setResponseStatus(200);
                this.applySoftFallbackSeo(productSlug);
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
      .subscribe((product) => {
        if (!product) {
          return;
        }

        this.product.set(product);
        this.softFallbackActive.set(false);
        this.selectedVariantId.set(
          product.defaultVariant?.id ?? product.variants[0]?.id ?? null,
        );
        this.selectedMaterialKey.set(
          this.materialKeyForVariant(
            product.defaultVariant ?? product.variants[0] ?? null,
          ),
        );
        this.setSelectedImageAssetId(
          product.primaryImage?.mediaAssetId ??
            product.images[0]?.mediaAssetId ??
            null,
        );
        this.quantity.set(1);
        this.languageService.setLocalizedRouteOverrides(product.localizedPaths);
        this.syncPublicUrl(product);
        this.applySeo(product);
        this.modelFile.set(null);
        this.modelLoading.set(false);
        this.modelError.set(false);
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

  selectImage(mediaAssetId: string): void {
    this.setSelectedImageAssetId(mediaAssetId);
  }

  showPreviousImage(): void {
    const images = this.galleryImages();
    if (images.length < 2) {
      return;
    }
    const nextIndex =
      (this.selectedImageIndex() - 1 + images.length) % images.length;
    this.setSelectedImageAssetId(images[nextIndex].mediaAssetId);
  }

  showNextImage(): void {
    const images = this.galleryImages();
    if (images.length < 2) {
      return;
    }
    const nextIndex = (this.selectedImageIndex() + 1) % images.length;
    this.setSelectedImageAssetId(images[nextIndex].mediaAssetId);
  }

  selectVariant(variant: ShopProductVariantOption): void {
    this.selectedVariantId.set(variant.id);
    this.selectedMaterialKey.set(this.materialKeyForVariant(variant));
    this.colorPopupOpen.set(false);
    this.addSuccess.set(false);
  }

  selectMaterial(materialKey: string): void {
    this.selectedMaterialKey.set(materialKey);
    this.colorPopupOpen.set(false);
    const material = this.materialOptions().find(
      (item) => item.key === materialKey,
    );
    const nextVariant =
      material?.variants.find((variant) => variant.isDefault) ??
      material?.variants[0] ??
      null;
    if (nextVariant) {
      this.selectedVariantId.set(nextVariant.id);
    }
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
    this.router.navigate(
      ['/', this.languageService.selectedLang(), 'checkout'],
      {
        queryParams: { session: sessionId },
      },
    );
  }

  priceLabel(): number {
    return (
      this.selectedVariant()?.priceChf ?? this.product()?.priceFromChf ?? 0
    );
  }

  colorLabel(variant: ShopProductVariantOption): string {
    return (
      variant.colorLabel || variant.colorName || variant.variantLabel || '-'
    );
  }

  colorHex(variant: ShopProductVariantOption | null | undefined): string {
    const normalizedHex = this.normalizeHexColor(variant?.colorHex);
    if (normalizedHex) {
      return normalizedHex;
    }

    const fallbackByName = this.colorHexFromName(variant?.colorName);
    if (fallbackByName) {
      return fallbackByName;
    }

    return '#d5d8de';
  }

  materialPriceLabel(material: ShopMaterialOption): number {
    return material.priceFromChf;
  }

  materialColorCount(material: ShopMaterialOption): number {
    return material.variants.length;
  }

  toggleColorPopup(): void {
    this.colorPopupOpen.update((open) => !open);
  }

  closeColorPopup(): void {
    this.colorPopupOpen.set(false);
  }

  openModelModal(): void {
    const model = this.product()?.model3d;
    if (!model) {
      return;
    }

    this.colorPopupOpen.set(false);
    this.modelModalOpen.set(true);

    if (this.modelFile() || this.modelLoading()) {
      return;
    }

    this.loadModelPreview(model.url, model.originalFilename);
  }

  closeModelModal(): void {
    this.modelModalOpen.set(false);
  }

  shopRootLink(): string[] {
    return this.shopRouteService.shopRootCommands();
  }

  categoryLink(slug: string | null | undefined): string[] {
    return this.shopRouteService.shopRootCommands(slug);
  }

  productLinkRoot(): string[] {
    const categorySlug =
      this.product()?.category.slug || this.routeCategorySlug();
    return this.shopRouteService.shopRootCommands(categorySlug);
  }

  goBackToShop(): void {
    const returnUrl =
      this.isBrowser && typeof history.state?.shopReturnUrl === 'string'
        ? history.state.shopReturnUrl
        : null;

    if (returnUrl && this.shopRouteService.isCatalogUrl(returnUrl)) {
      if (this.isBrowser && window.history.length > 1) {
        this.location.back();
        return;
      }

      void this.router.navigateByUrl(returnUrl);
      return;
    }

    void this.router.navigate(this.productLinkRoot());
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

  private setSelectedImageAssetId(mediaAssetId: string | null): void {
    this.selectedImageAssetId.set(mediaAssetId);
    this.scheduleSelectedThumbScroll();
  }

  private scheduleSelectedThumbScroll(): void {
    if (!this.isBrowser) {
      return;
    }
    if (this.thumbScrollFrame !== null) {
      window.cancelAnimationFrame(this.thumbScrollFrame);
    }
    this.thumbScrollFrame = window.requestAnimationFrame(() => {
      this.thumbScrollFrame = null;
      const activeThumb = this.thumbButtons?.get(
        this.selectedImageIndex(),
      )?.nativeElement;
      activeThumb?.scrollIntoView({
        block: 'nearest',
        inline: 'center',
        behavior: 'smooth',
      });
    });
  }

  private normalizeHexColor(value: string | null | undefined): string | null {
    const raw = String(value ?? '').trim();
    if (!raw) {
      return null;
    }

    const withHash = raw.startsWith('#') ? raw : `#${raw}`;
    if (!ProductDetailComponent.HEX_COLOR_PATTERN.test(withHash)) {
      return null;
    }

    return withHash.toUpperCase();
  }

  private colorHexFromName(value: string | null | undefined): string | null {
    return findColorHex(value);
  }

  private applySeo(product: ShopProductDetail): void {
    const title = product.seoTitle || `${product.name} | 3D fab`;
    const description =
      product.seoDescription ||
      product.excerpt ||
      this.extractTextFromRichContent(product.description) ||
      this.translate.instant('SHOP.CATALOG_META_DESCRIPTION');
    const robots =
      product.indexable === false ? 'noindex, nofollow' : 'index, follow';
    const lang = this.languageService.currentLang();
    const canonicalPath =
      product.localizedPaths?.[lang] ?? product.localizedPaths?.it ?? null;

    this.seoService.applyResolvedSeo({
      title,
      description,
      robots,
      ogTitle: product.ogTitle || title,
      ogDescription: product.ogDescription || description,
      canonicalPath,
      alternates: product.localizedPaths,
      xDefault: product.localizedPaths?.it ?? canonicalPath,
    });
  }

  private applyHardFallbackSeo(): void {
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

  private applySoftFallbackSeo(productSlug: string): void {
    const title = this.buildSoftFallbackTitle(productSlug);
    const description = this.resolveTranslatedText(
      'SEO.ROUTES.SHOP.PRODUCT_DESCRIPTION',
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

  private buildSoftFallbackTitle(productSlug: string): string {
    const humanized = humanizeShopSlug(productSlug, {
      stripProductIdPrefix: true,
    });
    if (humanized) {
      return `${humanized} | 3D fab`;
    }

    return this.resolveTranslatedText(
      'SEO.ROUTES.SHOP.PRODUCT_TITLE',
      `${this.translate.instant('SHOP.TITLE')} | 3D fab`,
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

  private materialLabelForVariant(
    variant: ShopProductVariantOption | null,
  ): string {
    return String(variant?.variantLabel || '').trim() || 'Standard';
  }

  descriptionPlainText(description: string | null | undefined): string {
    return this.extractTextFromRichContent(description) ?? '';
  }

  descriptionRichHtml(description: string | null | undefined): string {
    const normalized = String(description ?? '').trim();
    if (!normalized) {
      return '';
    }
    if (this.containsHtmlMarkup(normalized)) {
      return normalized;
    }
    return normalized
      .replace(/\r\n?/g, '\n')
      .split(/\n{2,}/)
      .map(
        (paragraph) =>
          `<p>${this.escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`,
      )
      .join('');
  }

  private materialKeyForVariant(
    variant: ShopProductVariantOption | null,
  ): string | null {
    if (!variant) {
      return null;
    }
    return this.materialLabelForVariant(variant).toLowerCase();
  }

  private materialPropertiesFor(
    materialLabel: string | null | undefined,
  ): ShopMaterialProperty[] {
    const normalized = String(materialLabel ?? '')
      .trim()
      .toUpperCase();

    if (normalized.includes('ASA')) {
      return [
        {
          labelKey: 'SHOP.PROPERTY_UV',
          valueKey: 'SHOP.PROPERTY_HIGH',
          tone: 'strong',
        },
        {
          labelKey: 'SHOP.PROPERTY_WEATHER',
          valueKey: 'SHOP.PROPERTY_HIGH',
          tone: 'strong',
        },
        {
          labelKey: 'SHOP.PROPERTY_RIGIDITY',
          valueKey: 'SHOP.PROPERTY_RIGID',
          tone: 'neutral',
        },
      ];
    }

    if (normalized.includes('PETG') || normalized.includes('PC')) {
      return [
        {
          labelKey: 'SHOP.PROPERTY_UV',
          valueKey: 'SHOP.PROPERTY_MEDIUM',
          tone: 'neutral',
        },
        {
          labelKey: 'SHOP.PROPERTY_WEATHER',
          valueKey: 'SHOP.PROPERTY_HIGH',
          tone: 'strong',
        },
        {
          labelKey: 'SHOP.PROPERTY_RIGIDITY',
          valueKey: normalized.includes('PC')
            ? 'SHOP.PROPERTY_HIGH'
            : 'SHOP.PROPERTY_RIGID',
          tone: 'neutral',
        },
      ];
    }

    if (normalized.includes('TPU')) {
      return [
        {
          labelKey: 'SHOP.PROPERTY_UV',
          valueKey: 'SHOP.PROPERTY_MEDIUM',
          tone: 'neutral',
        },
        {
          labelKey: 'SHOP.PROPERTY_WEATHER',
          valueKey: 'SHOP.PROPERTY_MEDIUM',
          tone: 'soft',
        },
        {
          labelKey: 'SHOP.PROPERTY_RIGIDITY',
          valueKey: 'SHOP.PROPERTY_FLEXIBLE',
          tone: 'soft',
        },
      ];
    }

    return [
      {
        labelKey: 'SHOP.PROPERTY_UV',
        valueKey: 'SHOP.PROPERTY_LOW',
        tone: 'soft',
      },
      {
        labelKey: 'SHOP.PROPERTY_WEATHER',
        valueKey: 'SHOP.PROPERTY_LOW',
        tone: 'soft',
      },
      {
        labelKey: 'SHOP.PROPERTY_RIGIDITY',
        valueKey: 'SHOP.PROPERTY_RIGID',
        tone: 'neutral',
      },
    ];
  }

  private extractTextFromRichContent(
    value: string | null | undefined,
  ): string | null {
    const normalized = String(value ?? '').trim();
    if (!normalized) {
      return null;
    }
    if (!this.containsHtmlMarkup(normalized)) {
      return normalized;
    }

    if (!this.isBrowser) {
      const text = normalized
        .replace(/<[^>]+>/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      return text || null;
    }

    const parser = new DOMParser();
    const parsed = parser.parseFromString(
      `<body>${normalized}</body>`,
      'text/html',
    );
    const text = (parsed.body.textContent ?? '').replace(/\u00a0/g, ' ').trim();
    return text || null;
  }

  private containsHtmlMarkup(value: string): boolean {
    return /<\/?[a-z][\s\S]*>/i.test(value);
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  private syncPublicUrl(product: ShopProductDetail): void {
    if (!this.isBrowser) {
      return;
    }

    const currentTree = this.router.parseUrl(this.router.url);
    const lang = this.languageService.currentLang();
    const targetPath =
      product.localizedPaths?.[lang] ??
      `/${lang}/shop/p/${this.shopRouteService.productPathSegment(product)}`;
    const normalizedTargetPath = targetPath.startsWith('/')
      ? targetPath
      : `/${targetPath}`;
    const currentPath = this.router
      .serializeUrl(currentTree)
      .split(/[?#]/, 1)[0];
    if (currentPath === normalizedTargetPath) {
      return;
    }

    const targetTree = this.router.createUrlTree(
      ['/', ...normalizedTargetPath.split('/').filter(Boolean)],
      {
        queryParams: currentTree.queryParams,
        fragment: currentTree.fragment ?? undefined,
      },
    );

    if (
      this.router.serializeUrl(targetTree) ===
      this.router.serializeUrl(currentTree)
    ) {
      return;
    }

    void this.router.navigateByUrl(targetTree, {
      replaceUrl: true,
      state: history.state,
    });
  }

  private setResponseStatus(status: number): void {
    if (this.responseInit) {
      this.responseInit.status = status;
    }
  }

  private readRouteParam(name: string): string | null {
    return this.normalizeRouteParam(this.route.snapshot.paramMap.get(name));
  }

  private normalizeRouteParam(value: string | null | undefined): string | null {
    const normalized = String(value ?? '').trim();
    return normalized || null;
  }
}
