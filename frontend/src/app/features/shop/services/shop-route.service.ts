import { Injectable } from '@angular/core';
import { LanguageService } from '../../../core/services/language.service';

export interface ShopProductRouteRef {
  id: string | null | undefined;
  name: string | null | undefined;
  slug?: string | null | undefined;
}

export interface ShopProductLookup {
  idPrefix: string | null;
  slugHint: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class ShopRouteService {
  constructor(private readonly languageService: LanguageService) {}

  shopRootCommands(categorySlug?: string | null): string[] {
    const lang = this.languageService.currentLang();
    return categorySlug
      ? ['/', lang, 'shop', categorySlug]
      : ['/', lang, 'shop'];
  }

  productCommands(product: ShopProductRouteRef): string[] {
    const lang = this.languageService.currentLang();
    return ['/', lang, 'shop', 'p', this.productPathSegment(product)];
  }

  productPathSegment(product: ShopProductRouteRef): string {
    const idPrefix = this.productIdPrefix(product.id);
    const tail =
      this.slugify(product.name) || this.slugify(product.slug) || 'product';

    return idPrefix ? `${idPrefix}-${tail}` : tail;
  }

  resolveProductLookup(
    productPathSegment: string | null | undefined,
  ): ShopProductLookup {
    const normalized = String(productPathSegment ?? '')
      .trim()
      .toLowerCase();
    if (!normalized) {
      return {
        idPrefix: null,
        slugHint: null,
      };
    }

    const bareUuidMatch = normalized.match(/^([0-9a-f]{8})$/);
    if (bareUuidMatch) {
      return {
        idPrefix: bareUuidMatch[1],
        slugHint: null,
      };
    }

    const publicSlugMatch = normalized.match(/^([0-9a-f]{8})-(.+)$/);
    if (publicSlugMatch) {
      return {
        idPrefix: publicSlugMatch[1],
        slugHint: this.slugify(publicSlugMatch[2]) || null,
      };
    }

    return {
      idPrefix: null,
      slugHint: normalized,
    };
  }

  isCatalogUrl(url: string | null | undefined): boolean {
    if (!url) {
      return false;
    }

    const normalized = url.split(/[?#]/, 1)[0] || '';
    return /^\/(?:[a-z]{2}\/)?shop(?:\/[^/]+)?$/i.test(normalized);
  }

  slugify(value: string | null | undefined): string {
    return String(value ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .replace(/-{2,}/g, '-');
  }

  private productIdPrefix(productId: string | null | undefined): string {
    const normalized = String(productId ?? '')
      .trim()
      .toLowerCase();
    const canonicalUuidMatch = normalized.match(/^([0-9a-f]{8})-/);
    if (canonicalUuidMatch) {
      return canonicalUuidMatch[1];
    }

    const compactUuidMatch = normalized.match(/^([0-9a-f]{8})/);
    return compactUuidMatch?.[1] ?? '';
  }
}
