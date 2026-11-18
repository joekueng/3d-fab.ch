import { CommonModule, DOCUMENT, isPlatformBrowser } from '@angular/common';
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
import { TranslatePipe } from '@ngx-translate/core';
import { map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { withHomeMediaPreview } from './home-media-preview';
import { AppButtonComponent } from '../../shared/components/app-button/app-button.component';
import { HomeProcessComponent } from './home-process.component';
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
  EMPTY_MEDIA_COLLECTIONS,
  HOME_CAPABILITY_CONFIGS,
  HOME_MEDIA_REQUESTS,
} from './home-page.config';
import {HomeCapabilityCard, HomeCapabilityConfig} from './home-page.types';
import {
  mediaAvifUrl,
  mediaFallbackUrl,
  mediaTransparentFallbackUrl,
  mediaWebpUrl,
} from './home-image.util';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    TranslatePipe,
    AppButtonComponent,
    HomeProcessComponent,
    SwipeCarouselDirective,
  ],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly publicMediaService = inject(PublicMediaService);
  private readonly homeProjectService = inject(HomeProjectService);
  private readonly ngZone = inject(NgZone);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private homeProjectAutoplayId: number | null = null;
  readonly languageService = inject(LanguageService);
  readonly homeProjectAutoplayMs = 12000;
  readonly homeProjectAutoplayDuration = `${this.homeProjectAutoplayMs}ms`;
  readonly homeProjectAutoplayPaused = signal(false);

  readonly homeProjects = toSignal(
    this.isBrowser
      ? this.homeProjectService.getProjects()
      : of([] as readonly HomeProject[]),
    {
      initialValue: [] as readonly HomeProject[],
    },
  );

  private readonly mediaByUsage = toSignal(
    this.isBrowser
      ? this.publicMediaService
          .getUsageCollections(HOME_MEDIA_REQUESTS)
          .pipe(
            map((media) =>
              environment.production
                ? media
                : withHomeMediaPreview(
                    media,
                    this.languageService.currentLang(),
                  ),
            ),
          )
      : of(EMPTY_MEDIA_COLLECTIONS),
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
    HOME_CAPABILITY_CONFIGS.map((config) => {
      const image = this.publicMediaService.pickPrimaryUsage(
        this.mediaByUsage()[
          buildPublicMediaUsageScopeKey('HOME_SECTION', config.usageKey)
        ] ?? [],
      );
      return {
        ...config,
        image: image
          ? this.publicMediaService.toDisplayImage(image, 'card')
          : null,
      };
    }),
  );

  readonly homeProjectIndex = signal(0);
  readonly homeProjectTrackTransform = computed(
    () => `translateX(-${this.homeProjectIndex() * 100}%)`,
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
      const isAutoplayPaused = this.homeProjectAutoplayPaused();
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
      if (projects.length > 1 && !isAutoplayPaused) {
        this.startHomeProjectAutoplay();
      } else {
        this.stopHomeProjectAutoplay();
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
    });
  }

  scrollToProcess(event: MouseEvent): void {
    if (
      !this.isBrowser ||
      event.button !== 0 ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey ||
      event.altKey
    )
      return;
    const target = this.document.getElementById('home-process');
    const view = this.document.defaultView;
    if (!target || !view) return;
    // A plain fragment resolves against <base href="/"> and reloads the app.
    // Keep this action in the current document, including on localized routes.
    event.preventDefault();
    target.scrollIntoView({
      behavior: view.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
      block: 'start',
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

  toggleHomeProjectAutoplay(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const shouldPause = !this.homeProjectAutoplayPaused();
    this.homeProjectAutoplayPaused.set(shouldPause);
    if (shouldPause) {
      this.stopHomeProjectAutoplay();
    } else {
      this.startHomeProjectAutoplay();
    }
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

  homeProjectDescriptionHtml(project: HomeProject): string {
    const description = String(project.description ?? '').trim();
    if (!description) {
      return '';
    }
    if (this.containsHtmlMarkup(description)) {
      return description;
    }
    return description
      .replace(/\r\n?/g, '\n')
      .split(/\n{2,}/)
      .map(
        (paragraph) =>
          `<p>${this.escapeHtml(paragraph).replace(/\n/g, '<br>')}</p>`,
      )
      .join('');
  }

  homeProjectDetailImageFallbackUrl(project: HomeProject): string | null {
    return mediaTransparentFallbackUrl(project.detailImage);
  }

  homeProjectDetailImageAvifUrl(project: HomeProject): string | null {
    return mediaAvifUrl(project.detailImage);
  }

  homeProjectDetailImageWebpUrl(project: HomeProject): string | null {
    return mediaWebpUrl(project.detailImage);
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

  trackCapability(_: number, card: HomeCapabilityConfig): string {
    return card.usageKey;
  }

  readonly trackHomeProject = (_: number, project: HomeProject): string => {
    return this.homeProjectKey(project);
  };

  private homeProjectKey(project: HomeProject): string {
    return [
      project.id,
      project.slug,
      project.image?.mediaAssetId,
      this.homeProjectImageFallbackUrl(project),
    ]
      .filter(Boolean)
      .join('::');
  }

  private containsHtmlMarkup(value: string): boolean {
    return /<\/?[a-z][\s\S]*>/i.test(value);
  }

  private escapeHtml(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
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
      this.homeProjectAutoplayPaused() ||
      this.homeProjects().length <= 1 ||
      this.prefersReducedMotion()
    ) {
      return;
    }

    this.ngZone.runOutsideAngular(() => {
      this.homeProjectAutoplayId = window.setInterval(() => {
        this.ngZone.run(() => {
          if (this.homeProjectAutoplayPaused()) {
            this.stopHomeProjectAutoplay();
            return;
          }
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

  private prefersReducedMotion(): boolean {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  }
}
