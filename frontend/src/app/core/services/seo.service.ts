import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { Title, Meta } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';

export interface PageSeoOverride {
  title?: string | null;
  titleKey?: string | null;
  description?: string | null;
  descriptionKey?: string | null;
  robots?: string | null;
  ogTitle?: string | null;
  ogTitleKey?: string | null;
  ogDescription?: string | null;
  ogDescriptionKey?: string | null;
}

type SupportedLang = 'it' | 'en' | 'de' | 'fr';
type SeoMap = Partial<Record<SupportedLang, string>>;
type SeoTextDataKey =
  | 'seoTitle'
  | 'seoDescription'
  | 'ogTitle'
  | 'ogDescription';

@Injectable({
  providedIn: 'root',
})
export class SeoService {
  private readonly defaultTitleByLang: Record<SupportedLang, string> = {
    it: '3D fab | Stampa 3D su misura',
    en: '3D fab | Custom 3D Printing',
    de: '3D fab | 3D-Druck nach Maß',
    fr: '3D fab | Impression 3D sur mesure',
  };
  private readonly defaultDescriptionByLang: Record<SupportedLang, string> = {
    it: 'Servizio di stampa 3D su misura, shop tecnico e supporto CAD per prototipi, ricambi e piccole serie.',
    en: 'Custom 3D printing service, technical shop and CAD support for prototypes, spare parts and short runs.',
    de: '3D-Druckservice nach Maß, technischer Shop und CAD-Support für Prototypen, Ersatzteile und Kleinserien.',
    fr: "Service d'impression 3D sur mesure, boutique technique et support CAD pour prototypes, pièces et petites séries.",
  };
  private readonly supportedLangs: readonly SupportedLang[] = [
    'it',
    'en',
    'de',
    'fr',
  ];
  private readonly supportedLangSet = new Set<SupportedLang>(
    this.supportedLangs,
  );
  private readonly ogLocaleByLang: Record<SupportedLang, string> = {
    it: 'it_IT',
    en: 'en_US',
    de: 'de_DE',
    fr: 'fr_FR',
  };

  constructor(
    private router: Router,
    private titleService: Title,
    private metaService: Meta,
    private translate: TranslateService,
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
    const cleanPath = this.getCleanPath(this.router.url);
    const lang = this.resolveLangFromPath(cleanPath);
    const title =
      this.resolveOverrideSeoText(override.title, override.titleKey) ??
      this.defaultTitle(lang);
    const description =
      this.resolveOverrideSeoText(
        override.description,
        override.descriptionKey,
      ) ?? this.defaultDescription(lang);
    const robots = this.asString(override.robots) ?? 'index, follow';
    const ogTitle =
      this.resolveOverrideSeoText(override.ogTitle, override.ogTitleKey) ??
      title;
    const ogDescription =
      this.resolveOverrideSeoText(
        override.ogDescription,
        override.ogDescriptionKey,
      ) ?? description;

    this.applySeoValues(
      title,
      description,
      robots,
      ogTitle,
      ogDescription,
      cleanPath,
      lang,
    );
  }

  private applyRouteSeo(rootSnapshot: ActivatedRouteSnapshot): void {
    const mergedData = this.getMergedRouteData(rootSnapshot);
    const cleanPath = this.getCleanPath(this.router.url);
    const lang = this.resolveLangFromPath(cleanPath);
    const title =
      this.resolveSeoText(mergedData, 'seoTitle', lang) ??
      this.defaultTitle(lang);
    const description =
      this.resolveSeoText(mergedData, 'seoDescription', lang) ??
      this.defaultDescription(lang);
    const robots = this.asString(mergedData['seoRobots']) ?? 'index, follow';
    const ogTitle = this.resolveSeoText(mergedData, 'ogTitle', lang) ?? title;
    const ogDescription =
      this.resolveSeoText(mergedData, 'ogDescription', lang) ?? description;

    this.applySeoValues(
      title,
      description,
      robots,
      ogTitle,
      ogDescription,
      cleanPath,
      lang,
    );
  }

  private applySeoValues(
    title: string,
    description: string,
    robots: string,
    ogTitle: string,
    ogDescription: string,
    cleanPath: string,
    lang: SupportedLang,
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
    this.metaService.updateTag({ property: 'og:site_name', content: '3D fab' });
    this.metaService.updateTag({ name: 'twitter:card', content: 'summary' });
    this.metaService.updateTag({ name: 'twitter:title', content: ogTitle });
    this.metaService.updateTag({
      name: 'twitter:description',
      content: ogDescription,
    });

    const canonicalPath = this.buildLocalizedPath(cleanPath, lang);
    const canonical = `${this.document.location.origin}${canonicalPath}`;
    this.metaService.updateTag({ property: 'og:url', content: canonical });
    this.updateCanonicalTag(canonical);
    this.updateOpenGraphLocales(lang);
    this.updateLangAndAlternates(canonicalPath, lang);
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

  private resolveOverrideSeoText(
    value: string | null | undefined,
    key: string | null | undefined,
  ): string | undefined {
    return this.asString(value) ?? this.resolveTranslation(key);
  }

  private resolveSeoText(
    routeData: Record<string, unknown>,
    key: SeoTextDataKey,
    lang: SupportedLang,
  ): string | undefined {
    const mapKey = `${key}ByLang`;
    const localized = routeData[mapKey];
    if (
      localized &&
      typeof localized === 'object' &&
      !Array.isArray(localized)
    ) {
      const mapped = localized as SeoMap;
      const byLang = this.asString(mapped[lang]);
      if (byLang) {
        return byLang;
      }
    }
    const translated = this.resolveTranslation(routeData[`${key}Key`]);
    if (translated) {
      return translated;
    }
    return this.asString(routeData[key]);
  }

  private resolveTranslation(value: unknown): string | undefined {
    const key = this.asString(value)?.trim();
    if (!key) {
      return undefined;
    }
    const translated = this.translate.instant(key);
    return typeof translated === 'string' && translated !== key
      ? translated
      : undefined;
  }

  private defaultTitle(lang: SupportedLang): string {
    return (
      this.resolveTranslation('SEO.DEFAULT.TITLE') ??
      this.defaultTitleByLang[lang]
    );
  }

  private defaultDescription(lang: SupportedLang): string {
    return (
      this.resolveTranslation('SEO.DEFAULT.DESCRIPTION') ??
      this.defaultDescriptionByLang[lang]
    );
  }

  private getCleanPath(url: string): string {
    const path = (url || '/').split('?')[0].split('#')[0];
    return path || '/';
  }

  private resolveLangFromPath(path: string): SupportedLang {
    const firstSegment = path.split('/').filter(Boolean)[0]?.toLowerCase();
    if (
      firstSegment &&
      this.supportedLangSet.has(firstSegment as SupportedLang)
    ) {
      return firstSegment as SupportedLang;
    }
    return 'it';
  }

  private buildLocalizedPath(path: string, lang: SupportedLang): string {
    const segments = path.split('/').filter(Boolean);
    if (segments.length === 0) {
      return `/${lang}`;
    }

    const firstSegment = segments[0]?.toLowerCase();
    if (
      firstSegment &&
      this.supportedLangSet.has(firstSegment as SupportedLang)
    ) {
      segments[0] = lang;
      return `/${segments.join('/')}`;
    }

    return `/${[lang, ...segments].join('/')}`;
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

  private updateOpenGraphLocales(lang: SupportedLang): void {
    this.metaService.updateTag({
      property: 'og:locale',
      content: this.ogLocaleByLang[lang],
    });

    this.document.head
      .querySelectorAll(
        'meta[property="og:locale:alternate"][data-seo-managed="true"]',
      )
      .forEach((node) => node.remove());

    for (const alternateLang of this.supportedLangs) {
      if (alternateLang === lang) {
        continue;
      }
      this.appendOgLocaleAlternate(this.ogLocaleByLang[alternateLang]);
    }
  }

  private updateLangAndAlternates(
    localizedPath: string,
    lang: SupportedLang,
  ): void {
    const suffixSegments = localizedPath.split('/').filter(Boolean).slice(1);
    const suffix =
      suffixSegments.length > 0 ? `/${suffixSegments.join('/')}` : '';

    this.document.documentElement.lang = lang;

    this.document.head
      .querySelectorAll('link[rel="alternate"][data-seo-managed="true"]')
      .forEach((node) => node.remove());

    for (const alt of this.supportedLangs) {
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

  private appendOgLocaleAlternate(locale: string): void {
    const meta = this.document.createElement('meta');
    meta.setAttribute('property', 'og:locale:alternate');
    meta.setAttribute('content', locale);
    meta.setAttribute('data-seo-managed', 'true');
    this.document.head.appendChild(meta);
  }
}
