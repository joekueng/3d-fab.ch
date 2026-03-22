import { Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import {
  NavigationEnd,
  PRIMARY_OUTLET,
  Router,
  UrlTree,
} from '@angular/router';
import { resolveInitialLanguage } from '../i18n/language-resolution';

type SupportedLang = 'it' | 'en' | 'de' | 'fr';
type LocalizedRouteOverrides = Partial<Record<SupportedLang, string>>;

@Injectable({
  providedIn: 'root',
})
export class LanguageService {
  currentLang = signal<SupportedLang>('it');
  private readonly defaultLang: SupportedLang = 'it';
  private readonly supportedLangs: SupportedLang[] = ['it', 'en', 'de', 'fr'];
  private localizedRouteOverrides: LocalizedRouteOverrides | null = null;

  constructor(
    private translate: TranslateService,
    private router: Router,
  ) {
    this.translate.addLangs(this.supportedLangs);
    this.translate.setFallbackLang('it');
    this.translate.onLangChange.subscribe((event) => {
      const lang =
        typeof event.lang === 'string' ? event.lang.toLowerCase() : null;
      if (this.isSupportedLang(lang) && lang !== this.currentLang()) {
        this.currentLang.set(lang);
      }
    });

    const initialTree = this.router.parseUrl(this.router.url);
    const initialLang = resolveInitialLanguage({
      url: this.router.url,
    });
    this.applyLanguage(initialLang);
    this.ensureLanguageInPath(initialTree);

    this.router.events.subscribe((event) => {
      if (!(event instanceof NavigationEnd)) {
        return;
      }

      this.ensureLanguageInPath(this.router.parseUrl(this.router.url));
    });
  }

  switchLang(lang: SupportedLang) {
    if (!this.isSupportedLang(lang)) {
      return;
    }

    const currentTree = this.router.parseUrl(this.router.url);
    const localizedRoute = this.resolveLocalizedRouteOverride(
      currentTree,
      lang,
    );
    if (localizedRoute) {
      this.navigateToLocalizedRoute(currentTree, localizedRoute);
      return;
    }

    const segments = this.getPrimarySegments(currentTree);

    let targetSegments: string[];
    if (segments.length === 0) {
      targetSegments = [lang];
    } else if (
      this.isSupportedLang(segments[0]) ||
      this.looksLikeLangToken(segments[0])
    ) {
      targetSegments = [lang, ...segments.slice(1)];
    } else {
      targetSegments = [lang, ...segments];
    }

    this.navigateIfChanged(currentTree, targetSegments);
  }

  selectedLang(): SupportedLang {
    const activeLang =
      typeof this.translate.currentLang === 'string'
        ? this.translate.currentLang.toLowerCase()
        : null;
    return this.isSupportedLang(activeLang) ? activeLang : this.currentLang();
  }

  localizedPath(path: string): string {
    const lang = this.selectedLang();
    const rawValue = String(path ?? '').trim();
    const normalized = rawValue || '/';
    const match = normalized.match(/^([^?#]*)([?#].*)?$/);
    const rawPath = match?.[1] || '/';
    const suffix = match?.[2] || '';
    const segments = rawPath.split('/').filter(Boolean);

    if (segments.length === 0) {
      return `/${lang}${suffix}`;
    }

    if (this.isSupportedLang(segments[0])) {
      segments[0] = lang;
      return `/${segments.join('/')}${suffix}`;
    }

    if (this.looksLikeLangToken(segments[0])) {
      return `/${[lang, ...segments.slice(1)].join('/')}${suffix}`;
    }

    return `/${[lang, ...segments].join('/')}${suffix}`;
  }

  setLocalizedRouteOverrides(
    paths: LocalizedRouteOverrides | null | undefined,
  ): void {
    this.localizedRouteOverrides = this.normalizeLocalizedRouteOverrides(paths);
  }

  clearLocalizedRouteOverrides(): void {
    this.localizedRouteOverrides = null;
  }

  private ensureLanguageInPath(urlTree: UrlTree): void {
    const segments = this.getPrimarySegments(urlTree);

    if (segments.length > 0 && this.isSupportedLang(segments[0])) {
      this.applyLanguage(segments[0]);
      return;
    }

    if (segments.length === 0) {
      const queryLang = this.getQueryLang(urlTree);
      const rootLang = this.isSupportedLang(queryLang)
        ? queryLang
        : this.defaultLang;
      if (rootLang !== this.currentLang()) {
        this.applyLanguage(rootLang);
      }
      this.navigateIfChanged(urlTree, [rootLang]);
      return;
    }

    if (this.currentLang() !== this.defaultLang) {
      this.applyLanguage(this.defaultLang);
    }

    const targetSegments = this.looksLikeLangToken(segments[0])
      ? [this.defaultLang, ...segments.slice(1)]
      : [this.defaultLang, ...segments];

    this.navigateIfChanged(urlTree, targetSegments);
  }

  private getPrimarySegments(urlTree: UrlTree): string[] {
    const primaryGroup = urlTree.root.children[PRIMARY_OUTLET];
    if (!primaryGroup) {
      return [];
    }
    return primaryGroup.segments.map((segment) => segment.path.toLowerCase());
  }

  private getQueryLang(urlTree: UrlTree): string | null {
    const lang = urlTree.queryParams['lang'];
    return typeof lang === 'string' ? lang.toLowerCase() : null;
  }

  private isSupportedLang(
    lang: string | null | undefined,
  ): lang is SupportedLang {
    return (
      typeof lang === 'string' &&
      this.supportedLangs.includes(lang as SupportedLang)
    );
  }

  private looksLikeLangToken(segment: string | null | undefined): boolean {
    return (
      typeof segment === 'string' && /^[a-z]{2}(?:-[a-z]{2})?$/i.test(segment)
    );
  }

  private applyLanguage(lang: SupportedLang): void {
    if (this.currentLang() === lang && this.translate.currentLang === lang) {
      return;
    }
    this.translate.use(lang);
    this.currentLang.set(lang);
  }

  private resolveLocalizedRouteOverride(
    currentTree: UrlTree,
    lang: SupportedLang,
  ): string | null {
    const overrides = this.localizedRouteOverrides;
    if (!overrides) {
      return null;
    }

    const currentPath = this.getCleanPath(
      this.router.serializeUrl(currentTree),
    );
    const paths = Object.values(overrides)
      .map((path) => this.normalizeLocalizedRoutePath(path))
      .filter((path): path is string => !!path);
    if (!paths.includes(currentPath)) {
      return null;
    }

    return this.normalizeLocalizedRoutePath(overrides[lang]);
  }

  private normalizeLocalizedRouteOverrides(
    paths: LocalizedRouteOverrides | null | undefined,
  ): LocalizedRouteOverrides | null {
    if (!paths) {
      return null;
    }

    const normalized = this.supportedLangs.reduce<LocalizedRouteOverrides>(
      (accumulator, lang) => {
        const path = this.normalizeLocalizedRoutePath(paths[lang]);
        if (path) {
          accumulator[lang] = path;
        }
        return accumulator;
      },
      {},
    );

    return Object.keys(normalized).length > 0 ? normalized : null;
  }

  private normalizeLocalizedRoutePath(
    path: string | null | undefined,
  ): string | null {
    const rawPath = String(path ?? '').trim();
    if (!rawPath) {
      return null;
    }
    const cleanPath = this.getCleanPath(rawPath);
    return cleanPath.startsWith('/') ? cleanPath : null;
  }

  private navigateToLocalizedRoute(
    currentTree: UrlTree,
    localizedPath: string,
  ): void {
    const { lang: _unusedLang, ...queryParams } = currentTree.queryParams;
    const targetTree = this.router.createUrlTree(
      ['/', ...localizedPath.split('/').filter(Boolean)],
      {
        queryParams,
        fragment: currentTree.fragment ?? undefined,
      },
    );

    if (
      this.router.serializeUrl(targetTree) ===
      this.router.serializeUrl(currentTree)
    ) {
      return;
    }

    this.router.navigateByUrl(targetTree, { replaceUrl: true });
  }

  private getCleanPath(url: string): string {
    const path = (url || '/').split('?')[0].split('#')[0];
    return path || '/';
  }

  private navigateIfChanged(
    currentTree: UrlTree,
    targetSegments: string[],
  ): void {
    const { lang: _unusedLang, ...queryParams } = currentTree.queryParams;
    const targetTree = this.router.createUrlTree(['/', ...targetSegments], {
      queryParams,
      fragment: currentTree.fragment ?? undefined,
    });

    if (
      this.router.serializeUrl(targetTree) ===
      this.router.serializeUrl(currentTree)
    ) {
      return;
    }

    this.router.navigateByUrl(targetTree, { replaceUrl: true });
  }
}
