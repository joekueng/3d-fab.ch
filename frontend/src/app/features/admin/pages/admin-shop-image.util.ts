import {
  AdminMediaLanguage,
  AdminMediaTranslation,
} from '../services/admin-media.service';
import {
  AdminPublicMediaUsage,
  AdminShopProduct,
} from '../services/admin-shop.service';
import { ProductImageItem, ShopLanguage } from './admin-shop.types';

export function buildProductImages(
  product: AdminShopProduct,
  activeLanguage: AdminMediaLanguage,
): ProductImageItem[] {
  const publicByAssetId = new Map<string, AdminPublicMediaUsage>();
  for (const image of product.images) {
    publicByAssetId.set(image.mediaAssetId, image);
  }

  return product.mediaUsages
    .filter((usage) => usage.isActive)
    .map((usage) => {
      const publicUsage = publicByAssetId.get(usage.mediaAssetId);
      const translations = normalizeMediaTranslations(usage.translations);
      return {
        usageId: usage.id,
        mediaAssetId: usage.mediaAssetId,
        previewUrl: resolveProductImageUrl(publicUsage),
        sortOrder: usage.sortOrder ?? 0,
        draftSortOrder: usage.sortOrder ?? 0,
        isPrimary: usage.isPrimary,
        createdAt: usage.createdAt,
        translations,
        title: publicUsage?.title ?? translations[activeLanguage].title,
        altText: publicUsage?.altText ?? translations[activeLanguage].altText,
      };
    })
    .sort((left, right) => {
      if (left.sortOrder !== right.sortOrder) {
        return left.sortOrder - right.sortOrder;
      }
      return left.createdAt.localeCompare(right.createdAt);
    });
}

export function resolveProductImageUrl(
  image: AdminPublicMediaUsage | undefined,
): string | null {
  if (!image) {
    return null;
  }
  return image.card?.url ?? image.hero?.url ?? image.thumb?.url ?? null;
}

export function createEmptyMediaTranslations(): Record<
  AdminMediaLanguage,
  AdminMediaTranslation
> {
  return {
    it: { title: '', altText: '' },
    en: { title: '', altText: '' },
    de: { title: '', altText: '' },
    fr: { title: '', altText: '' },
  };
}

export function cloneMediaTranslations(
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
): Record<AdminMediaLanguage, AdminMediaTranslation> {
  return normalizeMediaTranslations(translations);
}

export function normalizeMediaTranslations(
  translations: Partial<
    Record<AdminMediaLanguage, Partial<AdminMediaTranslation>>
  >,
): Record<AdminMediaLanguage, AdminMediaTranslation> {
  return {
    it: {
      title: translations['it']?.title?.trim() ?? '',
      altText: translations['it']?.altText?.trim() ?? '',
    },
    en: {
      title: translations['en']?.title?.trim() ?? '',
      altText: translations['en']?.altText?.trim() ?? '',
    },
    de: {
      title: translations['de']?.title?.trim() ?? '',
      altText: translations['de']?.altText?.trim() ?? '',
    },
    fr: {
      title: translations['fr']?.title?.trim() ?? '',
      altText: translations['fr']?.altText?.trim() ?? '',
    },
  };
}

export function isMediaTranslationComplete(
  translation: AdminMediaTranslation,
): boolean {
  return !!translation.title.trim() && !!translation.altText.trim();
}

export function validateMediaTranslations(
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  mediaLanguages: readonly AdminMediaLanguage[],
  languageLabels: Readonly<Record<ShopLanguage, string>>,
): string | null {
  for (const language of mediaLanguages) {
    if (!isMediaTranslationComplete(translations[language])) {
      return `Titolo e alt text immagine ${languageLabels[language]} sono obbligatori.`;
    }
  }
  return null;
}

export function areAllMediaTitlesBlank(
  translations: Record<AdminMediaLanguage, AdminMediaTranslation>,
  mediaLanguages: readonly AdminMediaLanguage[],
): boolean {
  return mediaLanguages.every((language) => !translations[language].title.trim());
}

export function deriveDefaultMediaTitle(filename: string): string {
  return filename
    .replace(/\.[^.]+$/, '')
    .replace(/[-_]+/g, ' ')
    .trim();
}
