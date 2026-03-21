import { isPlatformBrowser, isPlatformServer } from '@angular/common';
import {
  Injectable,
  PLATFORM_ID,
  TransferState,
  inject,
  makeStateKey,
} from '@angular/core';
import { TranslateLoader, TranslationObject } from '@ngx-translate/core';
import { from, Observable } from 'rxjs';

type SupportedLang = 'it' | 'en' | 'de' | 'fr';

const FALLBACK_LANG: SupportedLang = 'it';
const translationCache = new Map<SupportedLang, Promise<TranslationObject>>();

const translationLoaders: Record<
  SupportedLang,
  () => Promise<TranslationObject>
> = {
  it: () =>
    import('../../../assets/i18n/it.json').then(
      (module) => module.default as TranslationObject,
    ),
  en: () =>
    import('../../../assets/i18n/en.json').then(
      (module) => module.default as TranslationObject,
    ),
  de: () =>
    import('../../../assets/i18n/de.json').then(
      (module) => module.default as TranslationObject,
    ),
  fr: () =>
    import('../../../assets/i18n/fr.json').then(
      (module) => module.default as TranslationObject,
    ),
};

@Injectable()
export class StaticTranslateLoader implements TranslateLoader {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly transferState = inject(TransferState);

  getTranslation(lang: string): Observable<TranslationObject> {
    const normalized = this.normalizeLanguage(lang);
    return from(this.loadTranslation(normalized));
  }

  private normalizeLanguage(lang: string): SupportedLang {
    const normalized = String(lang || FALLBACK_LANG).toLowerCase();
    return normalized in translationLoaders
      ? (normalized as SupportedLang)
      : FALLBACK_LANG;
  }

  private loadTranslation(lang: SupportedLang): Promise<TranslationObject> {
    const transferStateKey = makeStateKey<TranslationObject>(
      `i18n:${lang.toLowerCase()}`,
    );
    if (
      isPlatformBrowser(this.platformId) &&
      this.transferState.hasKey(transferStateKey)
    ) {
      const transferred = this.transferState.get(transferStateKey, {});
      this.transferState.remove(transferStateKey);
      return Promise.resolve(transferred);
    }

    const cached = translationCache.get(lang);
    if (cached) {
      return cached;
    }

    const pending = translationLoaders[lang]()
      .then((translation) => {
        if (
          isPlatformServer(this.platformId) &&
          !this.transferState.hasKey(transferStateKey)
        ) {
          this.transferState.set(transferStateKey, translation);
        }
        return translation;
      })
      .catch(() =>
        lang === FALLBACK_LANG
          ? Promise.resolve({})
          : this.loadTranslation(FALLBACK_LANG),
      );

    translationCache.set(lang, pending);
    return pending;
  }
}
