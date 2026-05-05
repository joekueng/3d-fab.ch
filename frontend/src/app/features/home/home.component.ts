import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  Component,
  DestroyRef,
  NgZone,
  PLATFORM_ID,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { AppCardComponent } from '../../shared/components/app-card/app-card.component';
import { SwipeCarouselDirective } from '../../shared/directives/swipe-carousel.directive';
import { LanguageService } from '../../core/services/language.service';
import {
  HomeProject,
  HomeProjectService,
} from '../../core/services/home-project.service';
import {
  buildPublicMediaUsageScopeKey,
  PublicMediaDisplayImage,
  PublicMediaImage,
  PublicMediaService,
} from '../../core/services/public-media.service';
import {
  DEFAULT_HOME_PROJECT_GLOW,
  EMPTY_MEDIA_COLLECTIONS,
  HOME_CAPABILITY_CONFIGS,
  HOME_MEDIA_REQUESTS,
} from './home-page.config';
import {
  HomeCapabilityCard,
  HomeCapabilityConfig,
  HomeProjectGlow,
} from './home-page.types';
import {
  extractHomeProjectGlow,
  mediaAvifUrl,
  mediaFallbackUrl,
  mediaWebpUrl,
} from './home-image.util';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslateModule,
    AppButtonComponent,
    AppCardComponent,
    SwipeCarouselDirective,
  ],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly publicMediaService = inject(PublicMediaService);
  private readonly homeProjectService = inject(HomeProjectService);
  private readonly ngZone = inject(NgZone);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private homeProjectAutoplayId: number | null = null;
  private shopGalleryAutoplayId: number | null = null;
  readonly languageService = inject(LanguageService);
  readonly homeProjectAutoplayMs = 6000;
  readonly homeProjectAutoplayDuration = `${this.homeProjectAutoplayMs}ms`;
  readonly shopGalleryAutoplayMs = 5000;
  readonly shopGalleryAutoplayDuration = `${this.shopGalleryAutoplayMs}ms`;

  readonly homeProjects = toSignal(this.homeProjectService.getProjects(), {
    initialValue: [] as readonly HomeProject[],
  });

  private readonly mediaByUsage = toSignal(
    this.publicMediaService.getUsageCollections([...HOME_MEDIA_REQUESTS]),
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

  readonly founderImages = computed<readonly PublicMediaDisplayImage[]>(() =>
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

  readonly homeProjectIndex = signal(0);
  readonly homeProjectGlowColors = signal<ReadonlyMap<string, HomeProjectGlow>>(
    new Map(),
  );
  readonly homeProjectTrackTransform = computed(
    () => `translateX(-${this.homeProjectIndex() * 100}%)`,
  );
  readonly shopGalleryIndex = signal(0);
  readonly shopGalleryTrackTransform = computed(
    () => `translateX(-${this.shopGalleryIndex() * 100}%)`,
  );
  readonly founderImageIndex = signal(0);
  readonly currentFounderImage = computed<PublicMediaDisplayImage | null>(
    () => {
      const images = this.founderImages();
      if (images.length === 0) {
        return null;
      }
      return images[this.founderImageIndex()] ?? images[0] ?? null;
    },
  );

  constructor() {
    effect(() => {
      const projects = this.homeProjects();
      const currentIndex = this.homeProjectIndex();
      if (projects.length === 0) {
        this.stopHomeProjectAutoplay();
        if (currentIndex !== 0) {
          this.homeProjectIndex.set(0);
        }
        return;
      }
      if (currentIndex >= projects.length) {
        this.homeProjectIndex.set(0);
      }
      if (projects.length > 1) {
        this.startHomeProjectAutoplay();
      } else {
        this.stopHomeProjectAutoplay();
      }
    });

    effect(() => {
      const images = this.shopGalleryImages();
      const currentIndex = this.shopGalleryIndex();
      if (images.length === 0) {
        this.stopShopGalleryAutoplay();
        if (currentIndex !== 0) {
          this.shopGalleryIndex.set(0);
        }
        return;
      }
      if (currentIndex >= images.length) {
        this.shopGalleryIndex.set(0);
      }
      if (images.length > 1) {
        this.startShopGalleryAutoplay();
      } else {
        this.stopShopGalleryAutoplay();
      }
    });

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

    this.destroyRef.onDestroy(() => {
      this.stopHomeProjectAutoplay();
      this.stopShopGalleryAutoplay();
    });
  }

  selectHomeProject(index: number): void {
    const totalProjects = this.homeProjects().length;
    if (index < 0 || index >= totalProjects) {
      return;
    }
    this.homeProjectIndex.set(index);
    this.restartHomeProjectAutoplay();
  }

  showPreviousHomeProject(): void {
    const totalProjects = this.homeProjects().length;
    if (totalProjects <= 1) {
      return;
    }
    this.homeProjectIndex.set(
      this.homeProjectIndex() === 0
        ? totalProjects - 1
        : this.homeProjectIndex() - 1,
    );
    this.restartHomeProjectAutoplay();
  }

  showNextHomeProject(): void {
    this.advanceHomeProject();
    this.restartHomeProjectAutoplay();
  }

  homeProjectImageFallbackUrl(project: HomeProject): string | null {
    return mediaFallbackUrl(project.image);
  }

  homeProjectImageAvifUrl(project: HomeProject): string | null {
    return mediaAvifUrl(project.image);
  }

  homeProjectImageWebpUrl(project: HomeProject): string | null {
    return mediaWebpUrl(project.image);
  }

  homeProjectAmbilightImage(project: HomeProject): string | null {
    const imageUrl = this.homeProjectImageFallbackUrl(project);
    return imageUrl ? `url("${this.escapeCssUrl(imageUrl)}")` : null;
  }

  homeProjectDetailImageFallbackUrl(project: HomeProject): string | null {
    return mediaFallbackUrl(project.detailImage);
  }

  homeProjectDetailImageAvifUrl(project: HomeProject): string | null {
    return mediaAvifUrl(project.detailImage);
  }

  homeProjectDetailImageWebpUrl(project: HomeProject): string | null {
    return mediaWebpUrl(project.detailImage);
  }

  homeProjectGlow(project: HomeProject): HomeProjectGlow {
    return (
      this.homeProjectGlowColors().get(this.homeProjectGlowKey(project)) ??
      DEFAULT_HOME_PROJECT_GLOW
    );
  }

  updateHomeProjectGlow(project: HomeProject, event: Event): void {
    if (!this.isBrowser || !(event.target instanceof HTMLImageElement)) {
      return;
    }

    const glow = extractHomeProjectGlow(event.target);
    if (!glow) {
      return;
    }

    const projectKey = this.homeProjectGlowKey(project);
    this.homeProjectGlowColors.update((colors) => {
      if (colors.get(projectKey) === glow) {
        return colors;
      }
      const nextColors = new Map(colors);
      nextColors.set(projectKey, glow);
      return nextColors;
    });
  }

  selectShopGalleryImage(index: number): void {
    const totalImages = this.shopGalleryImages().length;
    if (index < 0 || index >= totalImages) {
      return;
    }
    this.shopGalleryIndex.set(index);
    this.restartShopGalleryAutoplay();
  }

  showPreviousShopGalleryImage(): void {
    const totalImages = this.shopGalleryImages().length;
    if (totalImages <= 1) {
      return;
    }
    this.shopGalleryIndex.set(
      this.shopGalleryIndex() === 0
        ? totalImages - 1
        : this.shopGalleryIndex() - 1,
    );
    this.restartShopGalleryAutoplay();
  }

  showNextShopGalleryImage(): void {
    this.advanceShopGalleryImage();
    this.restartShopGalleryAutoplay();
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

  readonly trackHomeProject = (_: number, project: HomeProject): string => {
    return this.homeProjectGlowKey(project);
  };

  private homeProjectGlowKey(project: HomeProject): string {
    return [
      project.id,
      project.slug,
      project.image?.mediaAssetId,
      this.homeProjectImageFallbackUrl(project),
    ]
      .filter(Boolean)
      .join('::');
  }

  private escapeCssUrl(url: string): string {
    return url.replace(/["\\\n\r\f]/g, (match) => {
      switch (match) {
        case '"':
          return '\\"';
        case '\\':
          return '\\\\';
        default:
          return '';
      }
    });
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

  private advanceHomeProject(): void {
    const totalProjects = this.homeProjects().length;
    if (totalProjects <= 1) {
      return;
    }
    this.homeProjectIndex.set(
      this.homeProjectIndex() === totalProjects - 1
        ? 0
        : this.homeProjectIndex() + 1,
    );
  }

  private restartHomeProjectAutoplay(): void {
    this.stopHomeProjectAutoplay();
    this.startHomeProjectAutoplay();
  }

  private startHomeProjectAutoplay(): void {
    if (
      !this.isBrowser ||
      this.homeProjectAutoplayId !== null ||
      this.homeProjects().length <= 1 ||
      this.prefersReducedMotion()
    ) {
      return;
    }

    this.ngZone.runOutsideAngular(() => {
      this.homeProjectAutoplayId = window.setInterval(() => {
        this.ngZone.run(() => {
          this.advanceHomeProject();
        });
      }, this.homeProjectAutoplayMs);
    });
  }

  private stopHomeProjectAutoplay(): void {
    if (!this.isBrowser || this.homeProjectAutoplayId === null) {
      return;
    }
    window.clearInterval(this.homeProjectAutoplayId);
    this.homeProjectAutoplayId = null;
  }

  private advanceShopGalleryImage(): void {
    const totalImages = this.shopGalleryImages().length;
    if (totalImages <= 1) {
      return;
    }
    this.shopGalleryIndex.set(
      this.shopGalleryIndex() === totalImages - 1
        ? 0
        : this.shopGalleryIndex() + 1,
    );
  }

  private restartShopGalleryAutoplay(): void {
    this.stopShopGalleryAutoplay();
    this.startShopGalleryAutoplay();
  }

  private startShopGalleryAutoplay(): void {
    if (
      !this.isBrowser ||
      this.shopGalleryAutoplayId !== null ||
      this.shopGalleryImages().length <= 1 ||
      this.prefersReducedMotion()
    ) {
      return;
    }

    this.ngZone.runOutsideAngular(() => {
      this.shopGalleryAutoplayId = window.setInterval(() => {
        this.ngZone.run(() => {
          this.advanceShopGalleryImage();
        });
      }, this.shopGalleryAutoplayMs);
    });
  }

  private stopShopGalleryAutoplay(): void {
    if (!this.isBrowser || this.shopGalleryAutoplayId === null) {
      return;
    }
    window.clearInterval(this.shopGalleryAutoplayId);
    this.shopGalleryAutoplayId = null;
  }

  private prefersReducedMotion(): boolean {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  }
}
