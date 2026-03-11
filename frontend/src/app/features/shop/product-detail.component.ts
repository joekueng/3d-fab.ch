import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  DestroyRef,
  Injector,
  PLATFORM_ID,
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
import { getColorHex } from '../../core/constants/colors.const';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { StlViewerComponent } from '../../shared/components/stl-viewer/stl-viewer.component';
import {
  ShopProductDetail,
  ShopProductVariantOption,
  ShopService,
} from './services/shop.service';
import { ShopRouteService } from './services/shop-route.service';

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
    StlViewerComponent,
  ],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss',
})
export class ProductDetailComponent {
  private static readonly HEX_COLOR_PATTERN =
    /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);
  private readonly seoService = inject(SeoService);
  private readonly languageService = inject(LanguageService);
  private readonly shopRouteService = inject(ShopRouteService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
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
      toObservable(this.languageService.currentLang, {
        injector: this.injector,
      }),
    ])
      .pipe(
        tap(() => {
          this.loading.set(true);
          this.error.set(null);
          this.addSuccess.set(false);
          this.modelError.set(false);
          this.colorPopupOpen.set(false);
          this.modelModalOpen.set(false);
        }),
        switchMap(([productSlug]) => {
          if (!productSlug) {
            this.error.set('SHOP.NOT_FOUND');
            this.loading.set(false);
            return of(null);
          }

          return this.shopService.getProductByPublicPath(productSlug).pipe(
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
        this.selectedVariantId.set(
          product.defaultVariant?.id ?? product.variants[0]?.id ?? null,
        );
        this.selectedMaterialKey.set(
          this.materialKeyForVariant(
            product.defaultVariant ?? product.variants[0] ?? null,
          ),
        );
        this.selectedImageAssetId.set(
          product.primaryImage?.mediaAssetId ??
            product.images[0]?.mediaAssetId ??
            null,
        );
        this.quantity.set(1);
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

  selectImage(mediaAssetId: string): void {
    this.selectedImageAssetId.set(mediaAssetId);
  }

  showPreviousImage(): void {
    const images = this.galleryImages();
    if (images.length < 2) {
      return;
    }
    const nextIndex =
      (this.selectedImageIndex() - 1 + images.length) % images.length;
    this.selectedImageAssetId.set(images[nextIndex].mediaAssetId);
  }

  showNextImage(): void {
    const images = this.galleryImages();
    if (images.length < 2) {
      return;
    }
    const nextIndex = (this.selectedImageIndex() + 1) % images.length;
    this.selectedImageAssetId.set(images[nextIndex].mediaAssetId);
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
    this.router.navigate(['/checkout'], {
      queryParams: { session: sessionId },
    });
  }

  priceLabel(): number {
    return (
      this.selectedVariant()?.priceChf ?? this.product()?.priceFromChf ?? 0
    );
  }

  colorLabel(variant: ShopProductVariantOption): string {
    return variant.colorName || variant.variantLabel || '-';
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
    const categorySlug = this.product()?.category.slug || this.categorySlug();
    return this.shopRouteService.shopRootCommands(categorySlug);
  }

  goBackToShop(): void {
    const returnUrl =
      this.isBrowser && typeof history.state?.shopReturnUrl === 'string'
        ? history.state.shopReturnUrl
        : null;

    if (returnUrl && this.shopRouteService.isCatalogUrl(returnUrl)) {
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
    const colorName = String(value ?? '').trim();
    if (!colorName) {
      return null;
    }

    const fallback = getColorHex(colorName);
    if (!fallback || fallback === '#facf0a') {
      return null;
    }

    return fallback;
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

    const currentProductSlug = this.productSlug()?.trim().toLowerCase() ?? '';
    const targetProductSlug = this.shopRouteService.productPathSegment(product);
    if (currentProductSlug === targetProductSlug) {
      return;
    }

    const currentTree = this.router.parseUrl(this.router.url);
    const targetTree = this.router.createUrlTree(
      [
        '/',
        this.languageService.selectedLang(),
        'shop',
        'p',
        targetProductSlug,
      ],
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
}
