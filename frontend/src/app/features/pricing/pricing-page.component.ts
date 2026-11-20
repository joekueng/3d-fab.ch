import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LanguageService } from '../../core/services/language.service';
import {
  PublicMediaService,
  PublicMediaDisplayImage,
  PricingExample,
  PublicMediaUsageCollectionMap,
  buildPublicMediaUsageScopeKey,
} from '../../core/services/public-media.service';
import { ImagePreviewComponent } from '../../shared/components/image-preview/image-preview.component';

@Component({
  selector: 'app-pricing-page',
  imports: [CommonModule, RouterLink, TranslatePipe, ImagePreviewComponent],
  templateUrl: './pricing-page.component.html',
  styleUrl: './pricing-page.component.scss',
})
export class PricingPageComponent {
  readonly languageService = inject(LanguageService);
  private readonly media = inject(PublicMediaService);
  readonly factors = [
    'material',
    'geometry',
    'quantity',
    'quality',
    'supports',
    'time',
  ] as const;
  readonly cases = ['functional', 'decorative', 'series'] as const;
  readonly failedImages = signal<ReadonlySet<string>>(new Set());
  private readonly collections = toSignal(
    this.media.getUsageCollections(
      this.cases.map((key) => ({
        usageType: 'PRICING_EXAMPLE',
        usageKey: key,
      })),
    ),
    { initialValue: {} as PublicMediaUsageCollectionMap },
  );
  readonly examples = computed(() =>
    this.cases.flatMap((key) => {
      const items =
        this.collections()[
          buildPublicMediaUsageScopeKey('PRICING_EXAMPLE', key)
        ] ?? [];
      const image = [...items]
        .sort(
          (a, b) =>
            Number(b.isPrimary) - Number(a.isPrimary) ||
            a.sortOrder - b.sortOrder,
        )
        .find((item) => item.pricingExample?.verified);
      const display = image ? this.media.toDisplayImage(image, 'hero') : null;
      return display && image?.pricingExample
        ? [{ key, image: display, details: image.pricingExample }]
        : [];
    }),
  );
  money(value: number): string {
    return new Intl.NumberFormat(this.languageService.currentLang() + '-CH', {
      style: 'currency',
      currency: 'CHF',
    }).format(value);
  }
  localized(values: PricingExample['settings']): string {
    return values[this.languageService.currentLang()];
  }
  imageFailed(image: PublicMediaDisplayImage): void {
    this.failedImages.update((ids) => new Set([...ids, image.mediaAssetId]));
  }
}
