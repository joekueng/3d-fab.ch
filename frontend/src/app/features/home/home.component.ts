import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import {
  buildPublicMediaUsageScopeKey,
  PublicMediaDisplayImage,
  PublicMediaImage,
  PublicMediaService,
  PublicMediaUsageCollectionMap,
} from '../../core/services/public-media.service';

const EMPTY_MEDIA_COLLECTIONS: PublicMediaUsageCollectionMap = {};

type HomeCapabilityUsageKey =
  | 'capability-prototyping'
  | 'capability-custom-parts'
  | 'capability-small-series'
  | 'capability-cad';

interface HomeCapabilityConfig {
  usageKey: HomeCapabilityUsageKey;
  titleKey: string;
  textKey: string;
}

interface HomeCapabilityCard extends HomeCapabilityConfig {
  image: PublicMediaDisplayImage | null;
}

const HOME_CAPABILITY_CONFIGS: readonly HomeCapabilityConfig[] = [
  {
    usageKey: 'capability-prototyping',
    titleKey: 'HOME.CAP_1_TITLE',
    textKey: 'HOME.CAP_1_TEXT',
  },
  {
    usageKey: 'capability-custom-parts',
    titleKey: 'HOME.CAP_2_TITLE',
    textKey: 'HOME.CAP_2_TEXT',
  },
  {
    usageKey: 'capability-small-series',
    titleKey: 'HOME.CAP_3_TITLE',
    textKey: 'HOME.CAP_3_TEXT',
  },
  {
    usageKey: 'capability-cad',
    titleKey: 'HOME.CAP_4_TITLE',
    textKey: 'HOME.CAP_4_TEXT',
  },
];

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    AppButtonComponent,
    AppCardComponent,
  ],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  private readonly publicMediaService = inject(PublicMediaService);

  private readonly mediaByUsage = toSignal(
    this.publicMediaService.getUsageCollections([
      {
        usageType: 'HOME_SECTION',
        usageKey: 'shop-gallery',
      },
      {
        usageType: 'HOME_SECTION',
        usageKey: 'founders-gallery',
      },
      {
        usageType: 'HOME_SECTION',
        usageKey: 'capability-prototyping',
      },
      {
        usageType: 'HOME_SECTION',
        usageKey: 'capability-custom-parts',
      },
      {
        usageType: 'HOME_SECTION',
        usageKey: 'capability-small-series',
      },
      {
        usageType: 'HOME_SECTION',
        usageKey: 'capability-cad',
      },
    ]),
    { initialValue: EMPTY_MEDIA_COLLECTIONS },
  );

  readonly shopGalleryImages = computed<readonly PublicMediaDisplayImage[]>(
    () =>
      (
        this.mediaByUsage()[
          buildPublicMediaUsageScopeKey('HOME_SECTION', 'shop-gallery')
        ] ?? []
      )
        .map((item: PublicMediaImage) =>
          this.publicMediaService.toDisplayImage(item, 'card'),
        )
        .filter(
          (
            item: PublicMediaDisplayImage | null,
          ): item is PublicMediaDisplayImage => item !== null,
        ),
  );

  readonly founderImages = computed<readonly PublicMediaDisplayImage[]>(
    () =>
      (
        this.mediaByUsage()[
          buildPublicMediaUsageScopeKey('HOME_SECTION', 'founders-gallery')
        ] ?? []
      )
        .map((item: PublicMediaImage) =>
          this.publicMediaService.toDisplayImage(item, 'hero'),
        )
        .filter(
          (
            item: PublicMediaDisplayImage | null,
          ): item is PublicMediaDisplayImage => item !== null,
        ),
  );

  readonly capabilityCards = computed<readonly HomeCapabilityCard[]>(() =>
    HOME_CAPABILITY_CONFIGS.map((config) => this.buildCapabilityCard(config)),
  );

  readonly founderImageIndex = signal(0);
  readonly currentFounderImage = computed<PublicMediaDisplayImage | null>(() => {
    const images = this.founderImages();
    if (images.length === 0) {
      return null;
    }
    return images[this.founderImageIndex()] ?? images[0] ?? null;
  });

  constructor() {
    effect(() => {
      const images = this.founderImages();
      const currentIndex = this.founderImageIndex();
      if (images.length === 0) {
        if (currentIndex !== 0) {
          this.founderImageIndex.set(0);
        }
        return;
      }
      if (currentIndex >= images.length) {
        this.founderImageIndex.set(0);
      }
    });
  }

  prevFounderImage(): void {
    const totalImages = this.founderImages().length;
    if (totalImages <= 1) {
      return;
    }
    this.founderImageIndex.set(
      this.founderImageIndex() === 0
        ? totalImages - 1
        : this.founderImageIndex() - 1,
    );
  }

  nextFounderImage(): void {
    const totalImages = this.founderImages().length;
    if (totalImages <= 1) {
      return;
    }
    this.founderImageIndex.set(
      this.founderImageIndex() === totalImages - 1
        ? 0
        : this.founderImageIndex() + 1,
    );
  }

  trackMediaAsset(_: number, image: PublicMediaDisplayImage): string {
    return image.mediaAssetId;
  }

  trackCapability(_: number, card: HomeCapabilityCard): string {
    return card.usageKey;
  }

  private buildCapabilityCard(
    config: HomeCapabilityConfig,
  ): HomeCapabilityCard {
    const items =
      this.mediaByUsage()[
        buildPublicMediaUsageScopeKey('HOME_SECTION', config.usageKey)
      ] ?? [];
    const primaryImage = this.publicMediaService.pickPrimaryUsage(items);

    return {
      ...config,
      image: primaryImage
        ? this.publicMediaService.toDisplayImage(primaryImage, 'card')
        : null,
    };
  }
}
