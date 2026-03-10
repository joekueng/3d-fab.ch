import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  AdminCreateMediaUsagePayload,
  AdminMediaLanguage,
  AdminMediaService,
  AdminMediaTranslation,
  AdminMediaUsage,
  AdminMediaUploadPayload,
  AdminMediaAsset,
  AdminUpdateMediaUsagePayload,
} from './admin-media.service';

export interface AdminMediaTextTranslation {
  title: string;
  altText: string;
}

export interface AdminShopCategoryRef {
  id: string;
  slug: string;
  name: string;
}

export interface AdminShopCategory {
  id: string;
  parentCategoryId: string | null;
  parentCategoryName: string | null;
  slug: string;
  name: string;
  description: string | null;
  seoTitle: string | null;
  seoDescription: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  indexable: boolean;
  isActive: boolean;
  sortOrder: number;
  depth: number;
  childCount: number;
  directProductCount: number;
  descendantProductCount: number;
  mediaUsageType: string;
  mediaUsageKey: string;
  breadcrumbs: AdminShopCategoryRef[];
  children: AdminShopCategory[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminUpsertShopCategoryPayload {
  parentCategoryId?: string | null;
  slug: string;
  name: string;
  description?: string;
  seoTitle?: string;
  seoDescription?: string;
  ogTitle?: string;
  ogDescription?: string;
  indexable: boolean;
  isActive: boolean;
  sortOrder: number;
}

export interface AdminShopProductVariant {
  id: string;
  sku: string | null;
  variantLabel: string;
  colorName: string;
  colorHex: string | null;
  internalMaterialCode: string;
  priceChf: number;
  isDefault: boolean;
  isActive: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminShopProductModel {
  url: string;
  originalFilename: string;
  mimeType: string;
  fileSizeBytes: number;
  boundingBoxXMm: number | null;
  boundingBoxYMm: number | null;
  boundingBoxZMm: number | null;
}

export interface AdminPublicMediaVariant {
  url: string;
  widthPx: number | null;
  heightPx: number | null;
  mimeType: string | null;
}

export interface AdminPublicMediaUsage {
  mediaAssetId: string;
  title: string | null;
  altText: string | null;
  usageType: string;
  usageKey: string;
  sortOrder: number;
  isPrimary: boolean;
  thumb: AdminPublicMediaVariant | null;
  card: AdminPublicMediaVariant | null;
  hero: AdminPublicMediaVariant | null;
}

export interface AdminShopProduct {
  id: string;
  categoryId: string;
  categoryName: string;
  categorySlug: string;
  slug: string;
  name: string;
  nameIt: string;
  nameEn: string;
  nameDe: string;
  nameFr: string;
  excerpt: string | null;
  excerptIt: string | null;
  excerptEn: string | null;
  excerptDe: string | null;
  excerptFr: string | null;
  description: string | null;
  descriptionIt: string | null;
  descriptionEn: string | null;
  descriptionDe: string | null;
  descriptionFr: string | null;
  seoTitle: string | null;
  seoTitleIt: string | null;
  seoTitleEn: string | null;
  seoTitleDe: string | null;
  seoTitleFr: string | null;
  seoDescription: string | null;
  seoDescriptionIt: string | null;
  seoDescriptionEn: string | null;
  seoDescriptionDe: string | null;
  seoDescriptionFr: string | null;
  ogTitle: string | null;
  ogDescription: string | null;
  indexable: boolean;
  isFeatured: boolean;
  isActive: boolean;
  sortOrder: number;
  variantCount: number;
  activeVariantCount: number;
  priceFromChf: number;
  priceToChf: number;
  mediaUsageType: string;
  mediaUsageKey: string;
  mediaUsages: AdminShopMediaUsage[];
  images: AdminPublicMediaUsage[];
  model3d: AdminShopProductModel | null;
  variants: AdminShopProductVariant[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminShopMediaUsage
  extends Omit<AdminMediaUsage, 'translations'> {
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
}

export interface AdminUpsertShopProductVariantPayload {
  id?: string;
  sku?: string;
  variantLabel?: string;
  colorName: string;
  colorHex?: string;
  internalMaterialCode: string;
  priceChf: number;
  isDefault: boolean;
  isActive: boolean;
  sortOrder: number;
}

export interface AdminUpsertShopProductPayload {
  categoryId: string;
  slug: string;
  name: string;
  nameIt: string;
  nameEn: string;
  nameDe: string;
  nameFr: string;
  excerpt?: string;
  excerptIt?: string;
  excerptEn?: string;
  excerptDe?: string;
  excerptFr?: string;
  description?: string;
  descriptionIt?: string;
  descriptionEn?: string;
  descriptionDe?: string;
  descriptionFr?: string;
  seoTitle?: string;
  seoTitleIt?: string;
  seoTitleEn?: string;
  seoTitleDe?: string;
  seoTitleFr?: string;
  seoDescription?: string;
  seoDescriptionIt?: string;
  seoDescriptionEn?: string;
  seoDescriptionDe?: string;
  seoDescriptionFr?: string;
  ogTitle?: string;
  ogDescription?: string;
  indexable: boolean;
  isFeatured: boolean;
  isActive: boolean;
  sortOrder: number;
  variants: AdminUpsertShopProductVariantPayload[];
}

@Injectable({
  providedIn: 'root',
})
export class AdminShopService {
  private readonly http = inject(HttpClient);
  private readonly adminMediaService = inject(AdminMediaService);
  private readonly productsBaseUrl = `${environment.apiUrl}/api/admin/shop/products`;
  private readonly categoriesBaseUrl = `${environment.apiUrl}/api/admin/shop/categories`;

  getCategories(): Observable<AdminShopCategory[]> {
    return this.http.get<AdminShopCategory[]>(this.categoriesBaseUrl, {
      withCredentials: true,
    });
  }

  getCategoryTree(): Observable<AdminShopCategory[]> {
    return this.http.get<AdminShopCategory[]>(
      `${this.categoriesBaseUrl}/tree`,
      {
        withCredentials: true,
      },
    );
  }

  getCategory(categoryId: string): Observable<AdminShopCategory> {
    return this.http.get<AdminShopCategory>(
      `${this.categoriesBaseUrl}/${categoryId}`,
      { withCredentials: true },
    );
  }

  createCategory(
    payload: AdminUpsertShopCategoryPayload,
  ): Observable<AdminShopCategory> {
    return this.http.post<AdminShopCategory>(this.categoriesBaseUrl, payload, {
      withCredentials: true,
    });
  }

  updateCategory(
    categoryId: string,
    payload: AdminUpsertShopCategoryPayload,
  ): Observable<AdminShopCategory> {
    return this.http.put<AdminShopCategory>(
      `${this.categoriesBaseUrl}/${categoryId}`,
      payload,
      { withCredentials: true },
    );
  }

  deleteCategory(categoryId: string): Observable<void> {
    return this.http.delete<void>(`${this.categoriesBaseUrl}/${categoryId}`, {
      withCredentials: true,
    });
  }

  getProducts(): Observable<AdminShopProduct[]> {
    return this.http.get<AdminShopProduct[]>(this.productsBaseUrl, {
      withCredentials: true,
    });
  }

  getProduct(productId: string): Observable<AdminShopProduct> {
    return this.http.get<AdminShopProduct>(
      `${this.productsBaseUrl}/${productId}`,
      {
        withCredentials: true,
      },
    );
  }

  createProduct(
    payload: AdminUpsertShopProductPayload,
  ): Observable<AdminShopProduct> {
    return this.http.post<AdminShopProduct>(this.productsBaseUrl, payload, {
      withCredentials: true,
    });
  }

  updateProduct(
    productId: string,
    payload: AdminUpsertShopProductPayload,
  ): Observable<AdminShopProduct> {
    return this.http.put<AdminShopProduct>(
      `${this.productsBaseUrl}/${productId}`,
      payload,
      { withCredentials: true },
    );
  }

  deleteProduct(productId: string): Observable<void> {
    return this.http.delete<void>(`${this.productsBaseUrl}/${productId}`, {
      withCredentials: true,
    });
  }

  uploadProductModel(
    productId: string,
    file: File,
  ): Observable<AdminShopProduct> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<AdminShopProduct>(
      `${this.productsBaseUrl}/${productId}/model`,
      formData,
      { withCredentials: true },
    );
  }

  deleteProductModel(productId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.productsBaseUrl}/${productId}/model`,
      {
        withCredentials: true,
      },
    );
  }

  listMediaAssets(): Observable<AdminMediaAsset[]> {
    return this.adminMediaService.listAssets();
  }

  uploadMediaAsset(
    file: File,
    payload: AdminMediaUploadPayload,
  ): Observable<AdminMediaAsset> {
    return this.adminMediaService.uploadAsset(file, payload);
  }

  createMediaUsage(
    payload: AdminCreateMediaUsagePayload,
  ): Observable<AdminMediaUsage> {
    return this.adminMediaService.createUsage(payload);
  }

  updateMediaUsage(
    usageId: string,
    payload: AdminUpdateMediaUsagePayload,
  ): Observable<AdminMediaUsage> {
    return this.adminMediaService.updateUsage(usageId, payload);
  }

  deleteMediaUsage(usageId: string): Observable<void> {
    return this.adminMediaService.deleteUsage(usageId);
  }
}
