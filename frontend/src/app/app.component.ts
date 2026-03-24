import {
  afterNextRender,
  Component,
  DestroyRef,
  Inject,
  Optional,
  PLATFORM_ID,
  inject,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { SeoService } from './core/services/seo.service';
import { BrandAnimationLogoComponent } from './shared/components/brand-animation-logo/brand-animation-logo.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, BrandAnimationLogoComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly seoService = inject(SeoService);
  private readonly destroyRef = inject(DestroyRef);
  readonly siteIntroState = signal<'hidden' | 'active' | 'closing'>('hidden');

  constructor(@Optional() @Inject(PLATFORM_ID) platformId?: Object) {
    if (!isPlatformBrowser(platformId ?? 'browser')) {
      return;
    }

    afterNextRender(() => {
      this.siteIntroState.set('active');

      const closeTimeoutId = window.setTimeout(() => {
        this.siteIntroState.set('closing');
      }, 1020);

      const hideTimeoutId = window.setTimeout(() => {
        this.siteIntroState.set('hidden');
      }, 1280);

      this.destroyRef.onDestroy(() => {
        window.clearTimeout(closeTimeoutId);
        window.clearTimeout(hideTimeoutId);
      });
    });
  }
}
