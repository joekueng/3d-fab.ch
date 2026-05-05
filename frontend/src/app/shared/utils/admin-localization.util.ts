export const ADMIN_LOCALIZED_LANGUAGES = ['it', 'en', 'de', 'fr'] as const;

export type AdminLocalizedLanguage = (typeof ADMIN_LOCALIZED_LANGUAGES)[number];

export type AdminLanguageStatus = 'complete' | 'incomplete' | 'empty';

export const ADMIN_LANGUAGE_LABELS: Readonly<
  Record<AdminLocalizedLanguage, string>
> = {
  it: 'IT',
  en: 'EN',
  de: 'DE',
  fr: 'FR',
};

export function resolveAdminLanguageStatus(
  isComplete: boolean,
  isStarted: boolean,
): AdminLanguageStatus {
  if (isComplete) {
    return 'complete';
  }
  return isStarted ? 'incomplete' : 'empty';
}

export function buildAdminLanguageStatusMap<TLanguage extends string>(
  languages: readonly TLanguage[],
  isComplete: (language: TLanguage) => boolean,
  isStarted: (language: TLanguage) => boolean,
): Record<TLanguage, AdminLanguageStatus> {
  return languages.reduce(
    (statuses, language) => ({
      ...statuses,
      [language]: resolveAdminLanguageStatus(
        isComplete(language),
        isStarted(language),
      ),
    }),
    {} as Record<TLanguage, AdminLanguageStatus>,
  );
}

export function mergeLocalizedTextMap<TLanguage extends string>(
  target: Record<TLanguage, string>,
  translated: Partial<Record<TLanguage, string>> | undefined,
  options: {
    overwriteExisting: boolean;
    targetLanguages?: readonly TLanguage[];
  },
): void {
  const languages =
    options.targetLanguages ?? (Object.keys(translated ?? {}) as TLanguage[]);
  for (const language of languages) {
    const incoming = translated?.[language];
    if (incoming === undefined) {
      continue;
    }
    if (target[language]?.trim() && !options.overwriteExisting) {
      continue;
    }
    target[language] = incoming.trim();
  }
}

export function createEmptyLocalizedTextMap<TLanguage extends string>(
  languages: readonly TLanguage[],
): Record<TLanguage, string> {
  return languages.reduce(
    (values, language) => ({
      ...values,
      [language]: '',
    }),
    {} as Record<TLanguage, string>,
  );
}
