import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { map, Observable, tap } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PublicMediaUsageDto,
  PublicMediaVariantDto,
} from '../../../core/services/public-media.service';
import { LanguageService } from '../../../core/services/language.service';

export interface ShopCategoryRef {
  id: string;
  slug: string;
  name: string;
}

export interface ShopCategoryTree {
  id: string;
  parentCategoryId: string | null;
  slug: string;
  name: string;
  description: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  indexable: boolean | null;
  sortOrder: number | null;
  productCount: number;
  primaryImage: PublicMediaUsageDto | null;
  children: ShopCategoryTree[];
}

export interface ShopCategoryDetail {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  indexable: boolean | null;
  sortOrder: number | null;
  productCount: number;
  breadcrumbs: ShopCategoryRef[];
  primaryImage: PublicMediaUsageDto | null;
  images: PublicMediaUsageDto[];
  children: ShopCategoryTree[];
}

export interface ShopProductVariantOption {
  id: string;
  sku: string | null;
  variantLabel: string | null;
  colorName: string | null;
  colorHex: string | null;
  priceChf: number;
  isDefault: boolean;
}

export interface ShopProductModel {
  url: string;
  originalFilename: string;
  mimeType: string | null;
  fileSizeBytes: number | null;
  boundingBoxXMm: number | null;
  boundingBoxYMm: number | null;
  boundingBoxZMm: number | null;
}

export interface ShopProductSummary {
  id: string;
  slug: string;
  name: string;
  excerpt: string | null;
  isFeatured: boolean | null;
  sortOrder: number | null;
  category: ShopCategoryRef;
  priceFromChf: number;
  priceToChf: number;
  defaultVariant: ShopProductVariantOption | null;
  primaryImage: PublicMediaUsageDto | null;
  model3d: ShopProductModel | null;
}

export interface ShopProductDetail {
  id: string;
  slug: string;
  name: string;
  excerpt: string | null;
  description: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  indexable: boolean | null;
  isFeatured: boolean | null;
  sortOrder: number | null;
  category: ShopCategoryRef;
  breadcrumbs: ShopCategoryRef[];
  priceFromChf: number;
  priceToChf: number;
  defaultVariant: ShopProductVariantOption | null;
  variants: ShopProductVariantOption[];
  primaryImage: PublicMediaUsageDto | null;
  images: PublicMediaUsageDto[];
  model3d: ShopProductModel | null;
}

export interface ShopProductCatalogResponse {
  categorySlug: string | null;
  featuredOnly: boolean | null;
  category: ShopCategoryDetail | null;
  products: ShopProductSummary[];
}

export interface ShopCartSession {
  id: string | null;
  status: string | null;
  sessionType: string | null;
}

export interface ShopCartItem {
  id: string;
  lineItemType: string;
  originalFilename: string | null;
  displayName: string | null;
  quantity: number;
  printTimeSeconds: number | null;
  materialGrams: number | null;
  colorCode: string | null;
  filamentVariantId: number | null;
  shopProductId: string | null;
  shopProductVariantId: string | null;
  shopProductSlug: string | null;
  shopProductName: string | null;
  shopVariantLabel: string | null;
  shopVariantColorName: string | null;
  shopVariantColorHex: string | null;
  materialCode: string | null;
  quality: string | null;
  nozzleDiameterMm: number | null;
  layerHeightMm: number | null;
  infillPercent: number | null;
  infillPattern: string | null;
  supportsEnabled: boolean | null;
  status: string | null;
  convertedStoredPath: string | null;
  unitPriceChf: number;
}

export interface ShopCartResponse {
  session: ShopCartSession | null;
  items: ShopCartItem[];
  printItemsTotalChf: number;
  cadTotalChf: number;
  itemsTotalChf: number;
  baseSetupCostChf: number;
  nozzleChangeCostChf: number;
  setupCostChf: number;
  shippingCostChf: number;
  globalMachineCostChf: number;
  grandTotalChf: number;
}

export interface ShopCategoryNavNode {
  id: string;
  slug: string;
  name: string;
  depth: number;
  productCount: number;
  current: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class ShopService {
  private readonly http = inject(HttpClient);
  private readonly languageService = inject(LanguageService);
  private readonly apiUrl = `${environment.apiUrl}/api/shop`;

  readonly cart = signal<ShopCartResponse | null>(null);
  readonly cartLoading = signal(false);
  readonly cartLoaded = signal(false);

  readonly cartItemCount = computed(() =>
    (this.cart()?.items ?? []).reduce(
      (total, item) => total + (Number(item.quantity) || 0),
      0,
    ),
  );

  readonly cartSessionId = computed(() => this.cart()?.session?.id ?? null);

  readonly cartQuantityByProductId = computed(() => {
    const quantities = new Map<string, number>();
    for (const item of this.cart()?.items ?? []) {
      const productId = item.shopProductId;
      if (!productId) {
        continue;
      }
      quantities.set(
        productId,
        (quantities.get(productId) ?? 0) + (Number(item.quantity) || 0),
      );
    }
    return quantities;
  });

  readonly cartQuantityByVariantId = computed(() => {
    const quantities = new Map<string, number>();
    for (const item of this.cart()?.items ?? []) {
      const variantId = item.shopProductVariantId;
      if (!variantId) {
        continue;
      }
      quantities.set(
        variantId,
        (quantities.get(variantId) ?? 0) + (Number(item.quantity) || 0),
      );
    }
    return quantities;
  });

  getCategories(): Observable<ShopCategoryTree[]> {
    return this.http.get<ShopCategoryTree[]>(`${this.apiUrl}/categories`, {
      params: this.buildLangParams(),
    });
  }

  getCategory(slug: string): Observable<ShopCategoryDetail> {
    return this.http.get<ShopCategoryDetail>(
      `${this.apiUrl}/categories/${encodeURIComponent(slug)}`,
      {
        params: this.buildLangParams(),
      },
    );
  }

  getProductCatalog(
    categorySlug?: string | null,
    featured?: boolean | null,
  ): Observable<ShopProductCatalogResponse> {
    let params = this.buildLangParams();
    if (categorySlug) {
      params = params.set('categorySlug', categorySlug);
    }
    if (featured !== null && featured !== undefined) {
      params = params.set('featured', String(featured));
    }

    return this.http.get<ShopProductCatalogResponse>(
      `${this.apiUrl}/products`,
      {
        params,
      },
    );
  }

  getProduct(slug: string): Observable<ShopProductDetail> {
    return this.http.get<ShopProductDetail>(
      `${this.apiUrl}/products/${encodeURIComponent(slug)}`,
      {
        params: this.buildLangParams(),
      },
    );
  }

  loadCart(): Observable<ShopCartResponse> {
    this.cartLoading.set(true);
    return this.http
      .get<ShopCartResponse>(`${this.apiUrl}/cart`, {
        withCredentials: true,
      })
      .pipe(
        tap({
          next: (cart) => {
            this.cart.set(cart);
            this.cartLoaded.set(true);
            this.cartLoading.set(false);
          },
          error: () => {
            this.cartLoading.set(false);
          },
        }),
      );
  }

  addToCart(
    shopProductVariantId: string,
    quantity = 1,
  ): Observable<ShopCartResponse> {
    return this.http
      .post<ShopCartResponse>(
        `${this.apiUrl}/cart/items`,
        {
          shopProductVariantId,
          quantity,
        },
        {
          withCredentials: true,
        },
      )
      .pipe(tap((cart) => this.setCart(cart)));
  }

  updateCartItem(
    lineItemId: string,
    quantity: number,
  ): Observable<ShopCartResponse> {
    return this.http
      .patch<ShopCartResponse>(
        `${this.apiUrl}/cart/items/${encodeURIComponent(lineItemId)}`,
        { quantity },
        {
          withCredentials: true,
        },
      )
      .pipe(tap((cart) => this.setCart(cart)));
  }

  removeCartItem(lineItemId: string): Observable<ShopCartResponse> {
    return this.http
      .delete<ShopCartResponse>(
        `${this.apiUrl}/cart/items/${encodeURIComponent(lineItemId)}`,
        {
          withCredentials: true,
        },
      )
      .pipe(tap((cart) => this.setCart(cart)));
  }

  clearCart(): Observable<ShopCartResponse> {
    return this.http
      .delete<ShopCartResponse>(`${this.apiUrl}/cart`, {
        withCredentials: true,
      })
      .pipe(tap((cart) => this.setCart(cart)));
  }

  getProductModelFile(urlOrPath: string, filename: string): Observable<File> {
    return this.http
      .get(this.resolveApiUrl(urlOrPath), {
        responseType: 'blob',
      })
      .pipe(
        map(
          (blob) =>
            new File([blob], filename, {
              type: blob.type || 'model/stl',
            }),
        ),
      );
  }

  quantityForProduct(productId: string | null | undefined): number {
    if (!productId) {
      return 0;
    }
    return this.cartQuantityByProductId().get(productId) ?? 0;
  }

  quantityForVariant(variantId: string | null | undefined): number {
    if (!variantId) {
      return 0;
    }
    return this.cartQuantityByVariantId().get(variantId) ?? 0;
  }

  flattenCategoryTree(
    categories: ShopCategoryTree[],
    activeSlug: string | null,
  ): ShopCategoryNavNode[] {
    const nodes: ShopCategoryNavNode[] = [];

    const walk = (items: ShopCategoryTree[], depth: number) => {
      for (const item of items) {
        nodes.push({
          id: item.id,
          slug: item.slug,
          name: item.name,
          depth,
          productCount: item.productCount,
          current: item.slug === activeSlug,
        });
        walk(item.children ?? [], depth + 1);
      }
    };

    walk(categories, 0);
    return nodes;
  }

  resolveMediaUrl(
    variant: PublicMediaVariantDto | null | undefined,
  ): string | null {
    if (!variant) {
      return null;
    }
    return variant.jpegUrl ?? variant.webpUrl ?? variant.avifUrl ?? null;
  }

  resolveApiUrl(urlOrPath: string | null | undefined): string {
    if (!urlOrPath) {
      return '';
    }
    if (
      urlOrPath.startsWith('http://') ||
      urlOrPath.startsWith('https://') ||
      urlOrPath.startsWith('blob:')
    ) {
      return urlOrPath;
    }
    const base = (environment.apiUrl || '').replace(/\/$/, '');
    const path = urlOrPath.startsWith('/') ? urlOrPath : `/${urlOrPath}`;
    return `${base}${path}`;
  }

  private buildLangParams(): HttpParams {
    return new HttpParams().set('lang', this.languageService.selectedLang());
  }

  private setCart(cart: ShopCartResponse): void {
    this.cart.set(cart);
    this.cartLoaded.set(true);
    this.cartLoading.set(false);
  }
}
