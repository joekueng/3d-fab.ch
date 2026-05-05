import { Injectable } from '@angular/core';
import { LanguageService } from '../../../core/services/language.service';
import { SupportedLang } from '../../../core/i18n/language-resolution';

export interface ShopProductRouteRef {
  id: string | null | undefined;
  name: string | null | undefined;
  slug?: string | null | undefined;
  publicPath?: string | null | undefined;
  localizedPaths?: Partial<Record<SupportedLang, string>> | null | undefined;
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
    const localizedPath = this.localizedProductPath(product);
    if (localizedPath) {
      return ['/', ...localizedPath.split('/').filter(Boolean)];
    }

    const lang = this.languageService.currentLang();
    return ['/', lang, 'shop', 'p', this.productPathSegment(product)];
  }

  productPathSegment(product: ShopProductRouteRef): string {
    const publicPath = String(product.publicPath ?? '').trim();
    if (publicPath) {
      return publicPath;
    }

    const idPrefix = this.productIdPrefix(product.id);
    const tail =
      this.slugify(product.name) || this.slugify(product.slug) || 'product';

    return idPrefix ? `${idPrefix}-${tail}` : tail;
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

  private localizedProductPath(product: ShopProductRouteRef): string | null {
    const lang = this.languageService.currentLang();
    const localizedPath = String(product.localizedPaths?.[lang] ?? '').trim();
    return localizedPath.startsWith('/') ? localizedPath : null;
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
