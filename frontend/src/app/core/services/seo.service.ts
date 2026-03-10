import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

export interface PageSeoOverride {
  title?: string | null;
  description?: string | null;
  robots?: string | null;
  ogTitle?: string | null;
  ogDescription?: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly defaultTitle = '3D fab | Stampa 3D su misura';
  private readonly defaultDescription =
    'Stampa 3D su misura con preventivo online immediato. Carica il file, scegli materiale e qualità, ricevi prezzo e tempi in pochi secondi.';
  private readonly supportedLangs = new Set(['it', 'en', 'de', 'fr']);

  constructor(
    private router: Router,
    private titleService: Title,
    private metaService: Meta,
    @Inject(DOCUMENT) private document: Document,
  ) {
    this.applyRouteSeo(this.router.routerState.snapshot.root);
    this.router.events
      .pipe(
        filter(
          (event): event is NavigationEnd => event instanceof NavigationEnd,
        ),
      )
      .subscribe(() => {
        this.applyRouteSeo(this.router.routerState.snapshot.root);
      });
  }

  applyPageSeo(override: PageSeoOverride): void {
    const title = this.asString(override.title) ?? this.defaultTitle;
    const description =
      this.asString(override.description) ?? this.defaultDescription;
    const robots = this.asString(override.robots) ?? 'index, follow';
    const ogTitle = this.asString(override.ogTitle) ?? title;
    const ogDescription = this.asString(override.ogDescription) ?? description;

    this.applySeoValues(title, description, robots, ogTitle, ogDescription);
  }

  private applyRouteSeo(rootSnapshot: ActivatedRouteSnapshot): void {
    const mergedData = this.getMergedRouteData(rootSnapshot);
    const title = this.asString(mergedData['seoTitle']) ?? this.defaultTitle;
    const description =
      this.asString(mergedData['seoDescription']) ?? this.defaultDescription;
    const robots = this.asString(mergedData['seoRobots']) ?? 'index, follow';
    const ogTitle = this.asString(mergedData['ogTitle']) ?? title;
    const ogDescription =
      this.asString(mergedData['ogDescription']) ?? description;

    this.applySeoValues(title, description, robots, ogTitle, ogDescription);
  }

  private applySeoValues(
    title: string,
    description: string,
    robots: string,
    ogTitle: string,
    ogDescription: string,
  ): void {
    this.titleService.setTitle(title);
    this.metaService.updateTag({ name: 'description', content: description });
    this.metaService.updateTag({ name: 'robots', content: robots });
    this.metaService.updateTag({ property: 'og:title', content: ogTitle });
    this.metaService.updateTag({
      property: 'og:description',
      content: ogDescription,
    });
    this.metaService.updateTag({ property: 'og:type', content: 'website' });
    this.metaService.updateTag({ name: 'twitter:card', content: 'summary' });

    const cleanPath = this.getCleanPath(this.router.url);
    const canonical = `${this.document.location.origin}${cleanPath}`;
    this.metaService.updateTag({ property: 'og:url', content: canonical });
    this.updateCanonicalTag(canonical);
    this.updateLangAndAlternates(cleanPath);
  }

  private getMergedRouteData(
    snapshot: ActivatedRouteSnapshot,
  ): Record<string, unknown> {
    const merged: Record<string, unknown> = {};
    let cursor: ActivatedRouteSnapshot | null = snapshot;
    while (cursor) {
      Object.assign(merged, cursor.data ?? {});
      cursor = cursor.firstChild;
    }
    return merged;
  }

  private asString(value: unknown): string | undefined {
    return typeof value === 'string' ? value : undefined;
  }

  private getCleanPath(url: string): string {
    const path = (url || '/').split('?')[0].split('#')[0];
    return path || '/';
  }

  private updateCanonicalTag(url: string): void {
    let link = this.document.head.querySelector(
      'link[rel="canonical"]',
    ) as HTMLLinkElement | null;
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', url);
  }

  private updateLangAndAlternates(path: string): void {
    const segments = path.split('/').filter(Boolean);
    const firstSegment = segments[0]?.toLowerCase();
    const hasLang = Boolean(
      firstSegment && this.supportedLangs.has(firstSegment),
    );
    const lang = hasLang ? firstSegment : 'it';
    const suffixSegments = hasLang ? segments.slice(1) : segments;
    const suffix =
      suffixSegments.length > 0 ? `/${suffixSegments.join('/')}` : '';

    this.document.documentElement.lang = lang;

    this.document.head
      .querySelectorAll('link[rel="alternate"][data-seo-managed="true"]')
      .forEach((node) => node.remove());

    for (const alt of ['it', 'en', 'de', 'fr']) {
      this.appendAlternateLink(
        alt,
        `${this.document.location.origin}/${alt}${suffix}`,
      );
    }
    this.appendAlternateLink(
      'x-default',
      `${this.document.location.origin}/it${suffix}`,
    );
  }

  private appendAlternateLink(hreflang: string, href: string): void {
    const link = this.document.createElement('link');
    link.setAttribute('rel', 'alternate');
    link.setAttribute('hreflang', hreflang);
    link.setAttribute('href', href);
    link.setAttribute('data-seo-managed', 'true');
    this.document.head.appendChild(link);
  }
}
