import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { ShopProductDetail, ShopProductVariantOption } from './shop.service';

/** Owned by the product page; uses the same document/origin as SeoService. */
@Injectable()
export class ProductStructuredDataService {
  private readonly document = inject(DOCUMENT);
  private readonly scriptId = 'shop-product-jsonld';

  update(
    product: ShopProductDetail,
    variant: ShopProductVariantOption | null,
    path: string,
    images: string[],
    description: string,
  ): void {
    if (
      product.indexable === false ||
      !variant ||
      !Number.isFinite(variant.priceChf) ||
      variant.priceChf < 0
    ) {
      this.clear();
      return;
    }
    const url = this.absoluteUrl(path);
    const image = [
      ...new Set(
        images.map((value) => this.absoluteUrl(value)).filter(Boolean),
      ),
    ];
    if (!url || !image.length) {
      this.clear();
      return;
    }
    const data = {
      '@context': 'https://schema.org',
      '@type': 'Product',
      '@id': `${url}#product`,
      url,
      name: product.name,
      description,
      image,
      ...(variant.sku ? { sku: variant.sku } : {}),
      ...(variant.colorLabel || variant.colorName
        ? { color: variant.colorLabel || variant.colorName }
        : {}),
      ...(variant.variantLabel ? { material: variant.variantLabel } : {}),
      offers: {
        '@type': 'Offer',
        url,
        price: variant.priceChf.toFixed(2),
        priceCurrency: 'CHF',
        // Public variants are active and orderable; the shop has no stock counter.
        availability: 'https://schema.org/InStock',
        itemCondition: 'https://schema.org/NewCondition',
      },
    };
    let script = this.document.getElementById(this.scriptId);
    if (!script) {
      script = this.document.createElement('script');
      script.id = this.scriptId;
      script.setAttribute('type', 'application/ld+json');
      this.document.head.appendChild(script);
    }
    // Prevent product text from closing the script in the serialized SSR HTML.
    script.textContent = JSON.stringify(data).replace(/</g, '\\u003c');
  }

  clear(): void {
    this.document.getElementById(this.scriptId)?.remove();
  }

  private absoluteUrl(value: string): string | null {
    try {
      const url = new URL(value, this.document.location.origin);
      return ['https:', 'http:'].includes(url.protocol) ? url.href : null;
    } catch {
      return null;
    }
  }
}
