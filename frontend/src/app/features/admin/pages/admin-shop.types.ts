import {
  AdminMediaLanguage,
  AdminMediaTranslation,
} from '../services/admin-media.service';

export type ShopLanguage = 'it' | 'en' | 'de' | 'fr';
export type ProductMode = 'create' | 'edit';
export type ProductStatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE' | 'FEATURED';

export interface CategoryFormState {
  id: string | null;
  parentCategoryId: string | null;
  slug: string;
  names: Record<ShopLanguage, string>;
  descriptions: Record<ShopLanguage, string>;
  seoTitles: Record<ShopLanguage, string>;
  seoDescriptions: Record<ShopLanguage, string>;
  ogTitle: string;
  ogDescription: string;
  indexable: boolean;
  isActive: boolean;
  sortOrder: number;
}

export interface ProductMaterialFormState {
  materialCode: string;
  defaultColorKey: string;
  priceChf: string;
  isDefault: boolean;
  isActive: boolean;
  sortOrder: number;
}

export interface ProductFormState {
  categoryId: string;
  slug: string;
  names: Record<ShopLanguage, string>;
  excerpts: Record<ShopLanguage, string>;
  descriptions: Record<ShopLanguage, string>;
  seoTitles: Record<ShopLanguage, string>;
  seoDescriptions: Record<ShopLanguage, string>;
  indexable: boolean;
  isFeatured: boolean;
  isActive: boolean;
  sortOrder: number;
  materials: ProductMaterialFormState[];
}

export interface ProductImageItem {
  usageId: string;
  mediaAssetId: string;
  previewUrl: string | null;
  sortOrder: number;
  draftSortOrder: number;
  isPrimary: boolean;
  createdAt: string;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  title: string;
  altText: string;
}

export interface ProductImageUploadState {
  file: File | null;
  previewUrl: string | null;
  activeLanguage: AdminMediaLanguage;
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>;
  sortOrder: number;
  isPrimary: boolean;
  saving: boolean;
}
