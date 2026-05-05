export type SupportedLang = 'it' | 'en' | 'de' | 'fr';

export const SUPPORTED_LANGS: readonly SupportedLang[] = [
  'it',
  'en',
  'de',
  'fr',
];

export function isSupportedLangValue(
  lang: string | null | undefined,
): lang is SupportedLang {
  return (
    typeof lang === 'string' && SUPPORTED_LANGS.includes(lang as SupportedLang)
  );
}

type InitialLanguageOptions = {
  url?: string | null;
  preferredLanguages?: readonly string[] | null;
  fallbackLang?: SupportedLang;
};

type NavigatorLike = {
  language?: string;
  languages?: readonly string[];
};

export function resolveInitialLanguage({
  url,
  preferredLanguages,
  fallbackLang = 'it',
}: InitialLanguageOptions): SupportedLang {
  const explicitLang = resolveExplicitLanguageFromUrl(url);
  if (explicitLang) {
    return explicitLang;
  }

  for (const candidate of preferredLanguages ?? []) {
    const normalized = normalizeSupportedLanguage(candidate);
    if (normalized) {
      return normalized;
    }
  }

  return fallbackLang;
}

export function parseAcceptLanguage(
  header: string | null | undefined,
): string[] {
  if (!header) {
    return [];
  }

  return header
    .split(',')
    .map((entry, index) => {
      const [rawTag, ...params] = entry.split(';').map((part) => part.trim());
      if (!rawTag) {
        return null;
      }

      const qualityParam = params.find((param) => param.startsWith('q='));
      const quality = qualityParam
        ? Number.parseFloat(qualityParam.slice(2))
        : 1;
      return {
        tag: rawTag,
        quality: Number.isFinite(quality) ? quality : 0,
        index,
      };
    })
    .filter(
      (
        entry,
      ): entry is {
        tag: string;
        quality: number;
        index: number;
      } => entry !== null && entry.quality > 0,
    )
    .sort(
      (left, right) => right.quality - left.quality || left.index - right.index,
    )
    .map((entry) => entry.tag);
}

export function getNavigatorLanguagePreferences(
  navigatorLike: NavigatorLike | null | undefined,
): string[] {
  if (!navigatorLike) {
    return [];
  }

  const orderedLanguages = [
    ...(Array.isArray(navigatorLike.languages) ? navigatorLike.languages : []),
  ];

  if (
    typeof navigatorLike.language === 'string' &&
    navigatorLike.language &&
    !orderedLanguages.includes(navigatorLike.language)
  ) {
    orderedLanguages.push(navigatorLike.language);
  }

  return orderedLanguages;
}

function resolveExplicitLanguageFromUrl(
  url: string | null | undefined,
): SupportedLang | null {
  const normalizedUrl = String(url ?? '/');
  const [pathAndQuery] = normalizedUrl.split('#', 1);
  const [rawPath, rawQuery] = pathAndQuery.split('?', 2);
  const firstSegment = rawPath.split('/').filter(Boolean)[0];
  const pathLanguage = normalizeSupportedLanguage(firstSegment);
  if (pathLanguage) {
    return pathLanguage;
  }

  const queryLanguage = new URLSearchParams(rawQuery ?? '').get('lang');
  return normalizeSupportedLanguage(queryLanguage);
}

function normalizeSupportedLanguage(
  rawLanguage: string | null | undefined,
): SupportedLang | null {
  if (typeof rawLanguage !== 'string') {
    return null;
  }

  const normalized = rawLanguage.trim().toLowerCase();
  if (!normalized || normalized === '*') {
    return null;
  }

  const [baseLanguage] = normalized.split('-', 1);
  return isSupportedLangValue(baseLanguage)
    ? (baseLanguage as SupportedLang)
    : null;
}
